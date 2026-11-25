package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.DayBlock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class NowUiState(
    val isLoading: Boolean = true,
    val currentBlock: DayBlock? = null,
    val nextBlock: DayBlock? = null,
    val briefingScript: String? = null,
    val isSpeaking: Boolean = false,
    val errorMessage: String? = null
)

class NowViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(NowUiState())
    val uiState: StateFlow<NowUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    private fun nowMinuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val minute = nowMinuteOfDay()
            val current = container.blockRepository.getCurrentBlock(minute)
            val next = container.blockRepository.getNextBlock(minute)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentBlock = current,
                nextBlock = next
            )
        }
    }

    fun speakBriefing() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(isSpeaking = true, errorMessage = null)

            val script = if (!container.prefs.hasApiKey()) {
                fallbackScript(state.currentBlock, state.nextBlock)
            } else {
                container.geminiRepository
                    .generateBriefingScript(state.currentBlock, state.nextBlock)
                    .getOrElse { fallbackScript(state.currentBlock, state.nextBlock) }
            }

            _uiState.value = _uiState.value.copy(briefingScript = script)
            container.speechManager.speak(script) {
                _uiState.value = _uiState.value.copy(isSpeaking = false)
            }
        }
    }

    fun speakMicroStepOnly() {
        val step = _uiState.value.currentBlock?.microStep ?: return
        _uiState.value = _uiState.value.copy(isSpeaking = true)
        container.speechManager.speak("Your tiny first step: $step") {
            _uiState.value = _uiState.value.copy(isSpeaking = false)
        }
    }

    fun markCurrentBlockStarted() {
        // Reserved for future "start micro-step timer" behavior; refresh for now.
        refresh()
    }

    fun markCurrentBlockCompleted() {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            container.blockRepository.markCompleted(block)
            refresh()
        }
    }

    private fun fallbackScript(current: DayBlock?, next: DayBlock?): String {
        return if (current == null) {
            "Nothing is scheduled right now. Open the Planner tab whenever you're ready to set up your day."
        } else {
            buildString {
                append("Right now: ${current.title}. ")
                if (current.why.isNotBlank()) append("${current.why} ")
                if (current.microStep.isNotBlank()) append("First tiny step: ${current.microStep}. ")
                if (next != null) append("Up next: ${next.title}.")
            }
        }
    }
}
