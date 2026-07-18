package io.frctl.app.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.frctl.app.data.LocalModelEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class ChatMessage(val fromUser: Boolean, val text: String)

data class LocalAiState(
    val messages: List<ChatMessage> = emptyList(),
    val thinking: Boolean = false,
    val error: String? = null,
    val summary: String = "",
    val summarizing: Boolean = false,
)

internal fun boundedReadme(value: String): String = value.take(6_000)

class LocalAiViewModel(app: Application) : AndroidViewModel(app) {
    private val engine: LocalLlmEngine = MediaPipeLocalLlmEngine(app)
    private val mutable = MutableStateFlow(LocalAiState())
    val state: StateFlow<LocalAiState> = mutable
    private var activeModelPath: String? = null
    private var generation: Job? = null

    fun startChat(model: LocalModelEntity) {
        if (mutable.value.summarizing || (activeModelPath != null && activeModelPath != model.path)) {
            generation?.cancel()
            generation = null
            mutable.update { it.copy(summarizing = false, summary = "", error = null) }
            viewModelScope.launch { runCatching { engine.unload() }; activeModelPath = null }
        }
    }

    fun send(model: LocalModelEntity, prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || mutable.value.thinking) return
        generation = viewModelScope.launch {
            mutable.update { it.copy(messages = it.messages + ChatMessage(true, clean) + ChatMessage(false, ""), thinking = true, error = null) }
            runCatching {
                ensureLoaded(model)
                engine.generate(clean).collect { token ->
                    mutable.update { state -> state.copy(messages = state.messages.dropLast(1) + state.messages.last().copy(text = state.messages.last().text + token)) }
                }
            }.onFailure { error -> mutable.update { it.copy(error = error.message ?: "Local generation failed") } }
            mutable.update { it.copy(thinking = false) }
        }
    }

    fun stop() {
        val running = generation
        generation = null
        running?.cancel()
        mutable.update { it.copy(thinking = false) }
        viewModelScope.launch {
            runCatching { running?.join(); engine.unload() }
            activeModelPath = null
        }
    }

    fun leaveChat() {
        stop()
        mutable.update { it.copy(messages = emptyList(), error = null) }
    }

    fun summarize(model: LocalModelEntity, prompt: String) {
        generation?.cancel()
        generation = viewModelScope.launch {
            mutable.update { it.copy(summary = "", summarizing = true, error = null) }
            runCatching {
                ensureLoaded(model)
                engine.generate(prompt).collect { token -> mutable.update { it.copy(summary = it.summary + token) } }
            }.onFailure { error -> mutable.update { it.copy(error = error.message ?: "Local summary failed") } }
            runCatching { engine.unload() }
            activeModelPath = null
            mutable.update { it.copy(summarizing = false) }
        }
    }

    fun clearSummary() = mutable.update { it.copy(summary = "", error = null) }

    fun stopSummary() {
        generation?.cancel()
        generation = null
        mutable.update { it.copy(summarizing = false) }
        viewModelScope.launch { runCatching { engine.unload() }; activeModelPath = null }
    }

    private suspend fun ensureLoaded(model: LocalModelEntity) {
        if (activeModelPath == model.path) return
        engine.unload()
        activeModelPath = null
        engine.load(model.path)
        activeModelPath = model.path
    }

    override fun onCleared() {
        generation?.cancel()
        runBlocking { runCatching { engine.unload() } }
        super.onCleared()
    }
}
