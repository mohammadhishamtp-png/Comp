package com.adhdcompanion.focus.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.data.ai.DoubtBackend
import com.adhdcompanion.focus.data.ai.HybridDoubtEvent
import com.adhdcompanion.focus.data.ai.LocalModelInfo
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockCategory
import com.adhdcompanion.focus.model.DoubtSolution
import com.adhdcompanion.focus.model.EnergyLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DoubtSolverUiState(
    val doubtText: String = "",
    val subjectText: String = "",
    val energyLevel: EnergyLevel? = EnergyLevel.MEDIUM,
    val attachedImageUri: Uri? = null,
    val localModelInfo: LocalModelInfo = LocalModelInfo(),
    val isImportingModel: Boolean = false,
    val isListening: Boolean = false,
    val isLoading: Boolean = false,
    val isSpeaking: Boolean = false,
    val activeBackend: DoubtBackend? = null,
    val statusMessage: String? = null,
    val streamingText: String = "",
    val solution: DoubtSolution? = null,
    val statusNote: String? = null,
    val needsOfflineModel: Boolean = false,
    val actionFeedback: String? = null,
    val errorMessage: String? = null
)

class DoubtSolverViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DoubtSolverUiState(localModelInfo = container.localLiteRtEngine.getModelInfo())
    )
    val uiState: StateFlow<DoubtSolverUiState> = _uiState.asStateFlow()

    private var activeGenerationJob: Job? = null

    fun refreshLocalModelInfo() {
        _uiState.value = _uiState.value.copy(
            localModelInfo = container.localLiteRtEngine.getModelInfo()
        )
    }

    fun updateDoubtText(text: String) {
        _uiState.value = _uiState.value.copy(doubtText = text, errorMessage = null)
    }

    fun updateSubjectText(text: String) {
        _uiState.value = _uiState.value.copy(subjectText = text)
    }

    fun updateEnergyLevel(level: EnergyLevel?) {
        _uiState.value = _uiState.value.copy(energyLevel = level)
    }

    fun attachImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(attachedImageUri = uri, errorMessage = null)
    }

    fun importOfflineModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImportingModel = true,
                errorMessage = null,
                actionFeedback = null
            )
            container.localLiteRtEngine.importModelFromUri(uri)
                .onSuccess { info ->
                    _uiState.value = _uiState.value.copy(
                        isImportingModel = false,
                        localModelInfo = info,
                        needsOfflineModel = false,
                        actionFeedback = "Offline model ready: ${info.fileName} • ${info.formattedSize}"
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isImportingModel = false,
                        errorMessage = err.message ?: "Failed to import .litertlm model file."
                    )
                }
        }
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
        val promptText = state.doubtText.trim().ifBlank {
            if (state.attachedImageUri != null) "Explain what is shown in this study image step by step." else ""
        }
        if (promptText.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter a doubt or attach a study photo first.")
            return
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                actionFeedback = null,
                statusNote = null,
                needsOfflineModel = false,
                streamingText = "",
                solution = null,
                activeBackend = null,
                statusMessage = "Routing request..."
            )

            container.hybridDoubtEngine.solveDoubtStream(
                doubt = promptText,
                subject = state.subjectText.trim().ifBlank { null },
                energyLevel = state.energyLevel,
                imageUri = state.attachedImageUri
            ).collect { event ->
                when (event) {
                    is HybridDoubtEvent.Status -> {
                        _uiState.value = _uiState.value.copy(
                            activeBackend = event.backend,
                            statusMessage = event.message
                        )
                    }

                    is HybridDoubtEvent.Delta -> {
                        _uiState.value = _uiState.value.copy(
                            activeBackend = event.backend,
                            streamingText = event.fullText,
                            statusMessage = null
                        )
                    }

                    is HybridDoubtEvent.Completed -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            activeBackend = event.backend,
                            streamingText = event.fullText,
                            solution = event.solution,
                            statusNote = event.note,
                            statusMessage = null,
                            needsOfflineModel = false
                        )
                    }

                    is HybridDoubtEvent.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = event.message,
                            needsOfflineModel = event.needsOfflineModel
                        )
                    }
                }
            }
        }
    }

    fun cancelGeneration() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        viewModelScope.launch {
            container.localLiteRtEngine.cancelGeneration()
        }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            statusMessage = null
        )
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
        cancelGeneration()
        container.speechManager.stop()
        _uiState.value = DoubtSolverUiState(
            localModelInfo = container.localLiteRtEngine.getModelInfo()
        )
    }

    override fun onCleared() {
        activeGenerationJob?.cancel()
        super.onCleared()
    }
}
