package io.frctl.app.ai

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import io.frctl.app.data.FrctlDatabase
import io.frctl.app.data.LocalModelEntity
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

enum class LocalModelStatus { DOWNLOADED, PARTIAL, FAILED }

enum class ModelBlockReason { LOW_RAM, LOW_STORAGE, METERED_NETWORK }

data class ModelEligibility(val allowed: Boolean, val reason: ModelBlockReason? = null)

sealed interface ModelDownloadState {
    data object Checking : ModelDownloadState
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data class Complete(val model: LocalModelEntity) : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}

internal fun fileSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

class LocalModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val dao = FrctlDatabase.get(appContext).dao()
    private val preferences = ModelPreferences(appContext)
    private val modelsDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30 * 60 * 1000L
            connectTimeoutMillis = 20_000L
            socketTimeoutMillis = 60_000L
        }
    }

    fun localModels(): Flow<List<LocalModelEntity>> = dao.observeLocalModels()

    suspend fun eligibility(model: RunnableModel): ModelEligibility {
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        if (memory.totalMem < model.minRamBytes) return ModelEligibility(false, ModelBlockReason.LOW_RAM)
        val existing = modelFile(model).takeIf(File::isFile)?.length() ?: 0L
        val required = (model.sizeBytes - existing).coerceAtLeast(0L) + 256L * 1024L * 1024L
        if (StatFs(modelsDir.absolutePath).availableBytes < required) return ModelEligibility(false, ModelBlockReason.LOW_STORAGE)
        if (preferences.unmeteredOnly.first() && !isUnmetered()) return ModelEligibility(false, ModelBlockReason.METERED_NETWORK)
        return ModelEligibility(true)
    }

    fun download(model: RunnableModel): Flow<ModelDownloadState> = flow {
        emit(ModelDownloadState.Checking)
        val eligibility = eligibility(model)
        if (!eligibility.allowed) {
            emit(ModelDownloadState.Error(eligibility.reason?.name ?: "BLOCKED"))
            return@flow
        }
        val file = modelFile(model)
        var downloaded = file.takeIf(File::isFile)?.length()?.coerceAtMost(model.sizeBytes) ?: 0L
        dao.upsertLocalModel(entity(model, file, downloaded, LocalModelStatus.PARTIAL))
        try {
            val response = client.get(model.downloadUrl) {
                if (downloaded > 0) header(HttpHeaders.Range, "bytes=$downloaded-")
            }
            if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.PartialContent) {
                error("Hugging Face returned HTTP ${response.status.value}")
            }
            val append = downloaded > 0 && response.status == HttpStatusCode.PartialContent
            if (!append) {
                downloaded = 0L
                if (file.exists()) file.delete()
            }
            val channel = response.bodyAsChannel()
            FileOutputStream(file, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    downloaded += read
                    emit(ModelDownloadState.Progress(downloaded, model.sizeBytes))
                }
                output.fd.sync()
            }
            if (file.length() != model.sizeBytes || fileSha256(file) != model.sha256) {
                file.delete()
                dao.upsertLocalModel(entity(model, file, 0L, LocalModelStatus.FAILED))
                emit(ModelDownloadState.Error("SHA256_MISMATCH"))
                return@flow
            }
            val complete = entity(model, file, file.length(), LocalModelStatus.DOWNLOADED, System.currentTimeMillis())
            dao.upsertLocalModel(complete)
            emit(ModelDownloadState.Complete(complete))
        } catch (cancelled: CancellationException) {
            dao.upsertLocalModel(entity(model, file, file.length(), LocalModelStatus.PARTIAL))
            throw cancelled
        } catch (error: Throwable) {
            if (error is OutOfMemoryError) {
                dao.upsertLocalModel(entity(model, file, file.length(), LocalModelStatus.FAILED))
                emit(ModelDownloadState.Error("OUT_OF_MEMORY"))
            } else {
                dao.upsertLocalModel(entity(model, file, file.length(), if (file.length() > 0) LocalModelStatus.PARTIAL else LocalModelStatus.FAILED))
                emit(ModelDownloadState.Error(error.message ?: "DOWNLOAD_FAILED"))
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun delete(model: LocalModelEntity) {
        val file = File(model.path).canonicalFile
        if (file.parentFile == modelsDir.canonicalFile) file.delete()
        dao.deleteLocalModel(model.id)
    }

    private fun modelFile(model: RunnableModel) = File(modelsDir, "${model.sha256.take(16)}-${model.fileName}")

    private fun entity(model: RunnableModel, file: File, size: Long, status: LocalModelStatus, downloadedAt: Long = 0L) =
        LocalModelEntity(model.key, file.absolutePath, size, model.sha256, status.name, downloadedAt)

    private fun isUnmetered(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
