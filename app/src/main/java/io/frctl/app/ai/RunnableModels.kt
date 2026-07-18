package io.frctl.app.ai

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RunnableModel(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val minRamBytes: Long,
    val license: String,
) {
    val key: String get() = "$id::$fileName"
    val downloadUrl: String get() = "https://huggingface.co/$id/resolve/main/$fileName"
}

internal fun parseRunnableModels(raw: String): List<RunnableModel> =
    Json.parseToJsonElement(raw).jsonObject["models"]?.jsonArray.orEmpty().mapNotNull { element ->
        val item = element.jsonObject
        runCatching {
            RunnableModel(
                id = item.getValue("id").jsonPrimitive.content,
                displayName = item.getValue("displayName").jsonPrimitive.content,
                fileName = item.getValue("fileName").jsonPrimitive.content,
                sizeBytes = item.getValue("sizeBytes").jsonPrimitive.content.toLong(),
                sha256 = item.getValue("sha256").jsonPrimitive.content.lowercase(),
                minRamBytes = item.getValue("minRamBytes").jsonPrimitive.content.toLong(),
                license = item["license"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrNull()
    }.filter {
        it.id.count { char -> char == '/' } == 1 &&
            !it.fileName.contains('/') &&
            it.sha256.matches(Regex("[a-f0-9]{64}")) &&
            it.sizeBytes > 0 && it.minRamBytes > 0
    }

class RunnableModelCatalog(context: Context) {
    val models: List<RunnableModel> by lazy {
        val raw = context.assets.open("runnable_models.json").bufferedReader().use { it.readText() }
        parseRunnableModels(raw)
    }

    fun find(repoId: String): RunnableModel? = models.firstOrNull { it.id.equals(repoId, ignoreCase = true) }
}
