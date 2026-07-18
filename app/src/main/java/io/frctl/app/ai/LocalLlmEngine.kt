package io.frctl.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

interface LocalLlmEngine {
    suspend fun load(modelPath: String)
    fun generate(prompt: String): Flow<String>
    suspend fun unload()
}

class LocalLlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MediaPipeLocalLlmEngine(context: Context) : LocalLlmEngine {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var inference: LlmInference? = null
    private val activeSink = AtomicReference<SendChannel<String>?>(null)

    override suspend fun load(modelPath: String) = mutex.withLock {
        inference?.close()
        inference = try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(2048)
                .build()
            LlmInference.createFromOptions(appContext, options)
        } catch (error: OutOfMemoryError) {
            throw LocalLlmException("Not enough memory to load this model", error)
        } catch (error: Exception) {
            throw LocalLlmException("The local model could not be loaded", error)
        }
    }

    override fun generate(prompt: String): Flow<String> = callbackFlow {
        val sink: SendChannel<String> = channel
        val engine = mutex.withLock {
            val loaded = inference ?: throw LocalLlmException("Load a local model first")
            if (!activeSink.compareAndSet(null, sink)) throw LocalLlmException("Another local generation is already running")
            loaded
        }
        try {
            val future = engine.generateResponseAsync(prompt) { partial: String, done: Boolean ->
                if (partial.isNotEmpty()) sink.trySend(partial)
                if (done && activeSink.compareAndSet(sink, null)) sink.close()
            }
            future.addListener({
                runCatching { future.get() }.exceptionOrNull()?.let { error ->
                    if (activeSink.compareAndSet(sink, null)) {
                        sink.close(LocalLlmException("Local generation failed", error))
                    }
                }
            }, { command -> command.run() })
        } catch (error: OutOfMemoryError) {
            activeSink.compareAndSet(sink, null)
            close(LocalLlmException("Not enough memory to generate a response", error))
        } catch (error: Exception) {
            activeSink.compareAndSet(sink, null)
            close(LocalLlmException("Local generation failed", error))
        }
        awaitClose { activeSink.compareAndSet(sink, null) }
    }

    override suspend fun unload() = mutex.withLock {
        activeSink.getAndSet(null)?.close(CancellationException("Local model unloaded"))
        inference?.close()
        inference = null
    }
}
