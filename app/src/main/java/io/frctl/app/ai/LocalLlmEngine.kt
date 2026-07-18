package io.frctl.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    override fun generate(prompt: String): Flow<String> = flow {
        val engine = mutex.withLock { inference } ?: throw LocalLlmException("Load a local model first")
        val result = try {
            engine.generateResponse(prompt)
        } catch (error: OutOfMemoryError) {
            throw LocalLlmException("Not enough memory to generate a response", error)
        } catch (error: Exception) {
            throw LocalLlmException("Local generation failed", error)
        }
        emit(result)
    }.flowOn(Dispatchers.Default)

    override suspend fun unload() = mutex.withLock {
        inference?.close()
        inference = null
    }
}
