package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockCategory
import com.adhdcompanion.focus.model.DoubtSolution
import com.adhdcompanion.focus.model.EnergyLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoubtSolverUiState(
    val doubtText: String = "",
    val subjectText: String = "",
    val energyLevel: EnergyLevel? = EnergyLevel.MEDIUM,
    val isListening: Boolean = false,
    val isLoading: Boolean = false,
    val isSpeaking: Boolean = false,
    val solution: DoubtSolution? = null,
    val actionFeedback: String? = null,
    val errorMessage: String? = null
)

class DoubtSolverViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(DoubtSolverUiState())
    val uiState: StateFlow<DoubtSolverUiState> = _uiState.asStateFlow()

    fun updateDoubtText(text: String) {
        _uiState.value = _uiState.value.copy(doubtText = text, errorMessage = null)
    }

    fun updateSubjectText(text: String) {
        _uiState.value = _uiState.value.copy(subjectText = text)
    }

    fun updateEnergyLevel(level: EnergyLevel?) {
        _uiState.value = _uiState.value.copy(energyLevel = level)
    }

    fun startVoiceInput() {
        if (!container.speechRecognitionManager.isAvailable()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Voice input isn't available on this device.")
            return
        }
        container.speechRecognitionManager.startListening(
            onResult = { text ->
                _uiState.value = _uiState.value.copy(doubtText = text, isListening = false)
            },
            onError = { message ->
                _uiState.value = _uiState.value.copy(errorMessage = message, isListening = false)
            },
            onListeningStateChanged = { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        )
    }

    fun stopVoiceInput() {
        container.speechRecognitionManager.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    fun solveDoubt() {
        val state = _uiState.value
        if (state.doubtText.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter or speak your study doubt first.")
            return
        }
        if (!container.prefs.hasApiKey()) {
            _uiState.value = state.copy(errorMessage = "Add your Gemini API key in Settings to solve doubts.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoading = true,
                errorMessage = null,
                actionFeedback = null
            )
            container.geminiRepository.solveDoubt(
                doubt = state.doubtText.trim(),
                subject = state.subjectText.trim().ifBlank { null },
                energyLevel = state.energyLevel
            ).onSuccess { solution ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    solution = solution
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = err.message ?: "Couldn't break down that doubt right now."
                )
            }
        }
    }

    fun speakSolution() {
        val sol = _uiState.value.solution ?: return
        val spokenText = buildString {
            append("Core intuition: ${sol.coreIntuition}. ")
            if (sol.stepByStep.isNotEmpty()) {
                append("Step by step: ")
                sol.stepByStep.forEachIndexed { index, step ->
                    append("Step ${index + 1}: $step. ")
                }
            }
            append("Your two-minute practice micro-step: ${sol.twoMinuteMicroStep}.")
        }
        _uiState.value = _uiState.value.copy(isSpeaking = true)
        container.speechManager.speak(spokenText) {
            _uiState.value = _uiState.value.copy(isSpeaking = false)
        }
    }

    fun stopSpeaking() {
        container.speechManager.stop()
        _uiState.value = _uiState.value.copy(isSpeaking = false)
    }

    fun sendMicroStepToDump() {
        val sol = _uiState.value.solution ?: return
        val prefix = _uiState.value.subjectText.trim().takeIf { it.isNotBlank() }?.let { "[$it] " } ?: ""
        viewModelScope.launch {
            container.brainDumpRepository.quickCapture("${prefix}Practice: ${sol.twoMinuteMicroStep}")
            _uiState.value = _uiState.value.copy(actionFeedback = "Micro-step saved to Brain Dump.")
        }
    }

    fun sendMicroStepToSchedule() {
        val sol = _uiState.value.solution ?: return
        val subject = _uiState.value.subjectText.trim().ifBlank { "Doubt practice" }
        viewModelScope.launch {
            container.blockRepository.addQuickMicroStepBlock(
                title = "Practice: $subject",
                why = "Lock in the concept while it's fresh.",
                microStep = sol.twoMinuteMicroStep,
                durationMinutes = 10,
                category = BlockCategory.STUDY
            )
            _uiState.value = _uiState.value.copy(actionFeedback = "Micro-step added to today's schedule.")
        }
    }

    fun reset() {
        container.speechManager.stop()
        _uiState.value = DoubtSolverUiState()
    }
}
