package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.LockEvaluationState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.Calendar

data class NowUiState(
    val isLoading: Boolean = true,
    val currentBlock: DayBlock? = null,
    val nextBlock: DayBlock? = null,
    val upcomingBlocks: List<DayBlock> = emptyList(),
    val todaysBlocks: List<DayBlock> = emptyList(),
    val nowMinuteOfDay: Int = 0,
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val lockEvaluation: LockEvaluationState = LockEvaluationState(),
    val briefingScript: String? = null,
    val isSpeaking: Boolean = false,
    /** Seconds left on the 5-minute micro-step timer; null when no timer is running. */
    val microStepSecondsRemaining: Int? = null,
    val errorMessage: String? = null
)

class NowViewModel(private val container: AppContainer) : ViewModel() {

    companion object {
        const val MICRO_STEP_SECONDS = 300
    }

    private var microStepTimerJob: Job? = null

    private val _uiState = MutableStateFlow(NowUiState(nowMinuteOfDay = nowMinuteOfDay()))
    val uiState: StateFlow<NowUiState> = _uiState.asStateFlow()

    private val minuteTicker = flow {
        while (true) {
            emit(nowMinuteOfDay())
            delay(15_000L)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                container.blockRepository.observeBlocksForDay(),
                minuteTicker,
                container.focusLockManager.evaluation
            ) { blocks, minute, lockEval ->
                Triple(blocks.sortedBy { it.startMinuteOfDay }, minute, lockEval)
            }.collect { (sortedBlocks, minute, lockEval) ->
                val current = sortedBlocks.firstOrNull {
                    !it.isCompleted && minute in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes)
                } ?: sortedBlocks.firstOrNull {
                    minute in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes)
                }

                val upcoming = sortedBlocks.filter {
                    it.id != current?.id &&
                        !it.isCompleted &&
                        (it.startMinuteOfDay + it.durationMinutes) > minute
                }
                val next = upcoming.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentBlock = current,
                    nextBlock = next,
                    upcomingBlocks = upcoming,
                    todaysBlocks = sortedBlocks,
                    nowMinuteOfDay = minute,
                    completedCount = sortedBlocks.count { it.isCompleted },
                    totalCount = sortedBlocks.size,
                    lockEvaluation = lockEval
                )
            }
        }
    }

    private fun nowMinuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun refresh() {
        container.focusLockManager.evaluateNow()
        viewModelScope.launch {
            val minute = nowMinuteOfDay()
            val sortedBlocks = container.blockRepository.getBlocksForDay().sortedBy { it.startMinuteOfDay }
            val current = sortedBlocks.firstOrNull {
                !it.isCompleted && minute in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes)
            } ?: sortedBlocks.firstOrNull {
                minute in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes)
            }
            val upcoming = sortedBlocks.filter {
                it.id != current?.id &&
                    !it.isCompleted &&
                    (it.startMinuteOfDay + it.durationMinutes) > minute
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                currentBlock = current,
                nextBlock = upcoming.firstOrNull(),
                upcomingBlocks = upcoming,
                todaysBlocks = sortedBlocks,
                nowMinuteOfDay = minute,
                completedCount = sortedBlocks.count { it.isCompleted },
                totalCount = sortedBlocks.size,
                errorMessage = null
            )
        }
    }

    fun speakBriefing() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentForBriefing = state.currentBlock ?: state.nextBlock
            val nextForBriefing = if (state.currentBlock != null) {
                state.nextBlock
            } else {
                state.upcomingBlocks.drop(1).firstOrNull()
            }

            _uiState.value = state.copy(isSpeaking = true, errorMessage = null)

            val script = if (!container.prefs.hasApiKey()) {
                fallbackScript(state.currentBlock, state.nextBlock)
            } else {
                container.geminiRepository
                    .generateBriefingScript(currentForBriefing, nextForBriefing)
                    .getOrElse { fallbackScript(state.currentBlock, state.nextBlock) }
            }

            _uiState.value = _uiState.value.copy(briefingScript = script)
            container.speechManager.speak(script) {
                _uiState.value = _uiState.value.copy(isSpeaking = false)
            }
        }
    }

    fun stopSpeaking() {
        container.speechManager.stop()
        _uiState.value = _uiState.value.copy(isSpeaking = false)
    }

    fun speakMicroStepOnly() {
        val target = _uiState.value.currentBlock ?: _uiState.value.nextBlock ?: return
        val step = target.microStep.ifBlank { "Open what you need and look at it for ten seconds." }
        _uiState.value = _uiState.value.copy(isSpeaking = true)
        container.speechManager.speak("Your tiny first step for ${target.title}: $step") {
            _uiState.value = _uiState.value.copy(isSpeaking = false)
        }
    }

    /**
     * Speaks the first micro-step, then runs a live 300-second countdown.
     * Speaks a short completion cue when it reaches zero.
     */
    fun startFiveMinuteMicroStep() {
        val block = _uiState.value.currentBlock ?: _uiState.value.nextBlock ?: return
        microStepTimerJob?.cancel()

        val step = block.microStep.ifBlank { "Just open what you need and look at it. That's it." }
        _uiState.value = _uiState.value.copy(
            isSpeaking = true,
            microStepSecondsRemaining = MICRO_STEP_SECONDS
        )
        container.speechManager.speak("Your tiny first step: $step. I'll time five minutes. Just start.") {
            _uiState.value = _uiState.value.copy(isSpeaking = false)
        }

        microStepTimerJob = viewModelScope.launch {
            var remaining = MICRO_STEP_SECONDS
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _uiState.value = _uiState.value.copy(microStepSecondsRemaining = remaining)
            }
            _uiState.value = _uiState.value.copy(microStepSecondsRemaining = null, isSpeaking = true)
            container.speechManager.speak(
                "Five minutes are up. Nice work. You can keep going, or take a breath."
            ) {
                _uiState.value = _uiState.value.copy(isSpeaking = false)
            }
        }
    }

    fun cancelMicroStepTimer() {
        microStepTimerJob?.cancel()
        microStepTimerJob = null
        container.speechManager.stop()
        _uiState.value = _uiState.value.copy(microStepSecondsRemaining = null, isSpeaking = false)
    }

    fun markCurrentBlockCompleted() {
        viewModelScope.launch {
            val block = _uiState.value.currentBlock ?: return@launch
            microStepTimerJob?.cancel()
            microStepTimerJob = null
            _uiState.value = _uiState.value.copy(microStepSecondsRemaining = null)
            container.blockRepository.markCompleted(block)
        }
    }

    fun markBlockCompleted(block: DayBlock) {
        viewModelScope.launch {
            container.blockRepository.markCompleted(block)
        }
    }

    override fun onCleared() {
        microStepTimerJob?.cancel()
        super.onCleared()
    }

    private fun fallbackScript(current: DayBlock?, next: DayBlock?): String {
        return when {
            current != null -> buildString {
                append("Right now: ${current.title}. ")
                if (current.why.isNotBlank()) append("${current.why} ")
                if (current.microStep.isNotBlank()) append("First tiny step: ${current.microStep}. ")
                if (next != null) append("Up next: ${next.title}.")
            }
            next != null -> buildString {
                append("Nothing is active this exact minute, so take a breath. ")
                append("Coming up next: ${next.title}. ")
                if (next.microStep.isNotBlank()) append("When you're ready, your first tiny step is: ${next.microStep}.")
            }
            else -> "Nothing is scheduled yet today. Open the Planner tab whenever you're ready to set up your day."
        }
    }
}
