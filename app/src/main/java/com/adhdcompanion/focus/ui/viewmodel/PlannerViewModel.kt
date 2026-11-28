package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.data.repository.StudyRepository
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockCategory
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.EnergyLevel
import com.adhdcompanion.focus.model.ExamGoal
import com.adhdcompanion.focus.model.PlannerInput
import com.adhdcompanion.focus.model.StudySession
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.model.StudyTodoItem
import com.adhdcompanion.focus.model.TimelineTenMinCell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlannerUiState(
    val availableHours: String = "",
    val energyLevel: EnergyLevel = EnergyLevel.MEDIUM,
    val goalsAndBlocks: String = "",
    val isGenerating: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class PlannerViewModel(private val container: AppContainer) : ViewModel() {

    private val studyRepo = container.studyRepository

    private val _uiState = MutableStateFlow(PlannerUiState())
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    val todaysBlocks: StateFlow<List<DayBlock>> = container.blockRepository
        .observeBlocksForDay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subjects: StateFlow<List<StudySubject>> = studyRepo
        .observeActiveSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaysSessions: StateFlow<List<StudySession>> = studyRepo
        .observeSessionsForDay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timelineCells: StateFlow<List<TimelineTenMinCell>> = todaysSessions
        .map { sessions -> StudyRepository.buildTenMinuteTimeline(sessions, studyRepo.todayEpoch()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            StudyRepository.buildTenMinuteTimeline(emptyList(), studyRepo.todayEpoch())
        )

    val todaysTodos: StateFlow<List<StudyTodoItem>> = studyRepo
        .observeTodosForDay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val examGoals: StateFlow<List<ExamGoal>> = studyRepo
        .observeExamGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            studyRepo.ensureDefaultSubjects()
        }
    }

    fun updateHours(value: String) {
        _uiState.value = _uiState.value.copy(availableHours = value, errorMessage = null)
    }

    fun updateEnergy(value: EnergyLevel) {
        _uiState.value = _uiState.value.copy(energyLevel = value)
    }

    fun updateGoals(value: String) {
        _uiState.value = _uiState.value.copy(goalsAndBlocks = value, errorMessage = null)
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
    }

    fun addTodo(subject: StudySubject?, title: String, estimatedMinutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            studyRepo.addTodo(subject, title, estimatedMinutes)
        }
    }

    fun toggleTodo(item: StudyTodoItem) {
        viewModelScope.launch {
            studyRepo.toggleTodo(item)
        }
    }

    fun deleteTodo(item: StudyTodoItem) {
        viewModelScope.launch {
            studyRepo.deleteTodo(item)
        }
    }

    fun saveExamGoal(title: String, targetDateEpoch: Long, targetDailyMinutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            studyRepo.upsertExamGoal(
                ExamGoal(
                    title = title.trim(),
                    targetDateEpoch = targetDateEpoch,
                    targetDailyMinutes = targetDailyMinutes.coerceIn(30, 720)
                )
            )
        }
    }

    fun deleteExamGoal(goal: ExamGoal) {
        viewModelScope.launch {
            studyRepo.deleteExamGoal(goal)
        }
    }

    fun generatePlan() {
        val state = _uiState.value
        if (state.availableHours.isBlank() || state.goalsAndBlocks.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Fill in your available hours and goals first.")
            return
        }
        if (!container.prefs.hasApiKey()) {
            _uiState.value = state.copy(errorMessage = "Add your Gemini API key in Settings first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isGenerating = true, errorMessage = null, statusMessage = null)
            val input = PlannerInput(
                availableHours = state.availableHours,
                energyLevel = state.energyLevel,
                goalsAndBlocks = state.goalsAndBlocks
            )
            container.geminiRepository.generateDayPlan(input)
                .onSuccess { blocks ->
                    container.blockRepository.replaceDayPlan(blocks)
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = "Saved ${blocks.size} blocks to today's schedule."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        errorMessage = error.message ?: "Couldn't generate a plan. Check your connection and API key."
                    )
                }
        }
    }

    fun saveBlock(
        existing: DayBlock?,
        title: String,
        why: String,
        microStep: String,
        startMinuteOfDay: Int,
        durationMinutes: Int,
        category: BlockCategory,
        isDecompressionBuffer: Boolean
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val block = DayBlock(
                id = existing?.id ?: 0L,
                title = title.trim(),
                why = why.trim(),
                microStep = microStep.trim(),
                startMinuteOfDay = startMinuteOfDay.coerceIn(0, 23 * 60 + 59),
                durationMinutes = durationMinutes.coerceIn(5, 360),
                category = category,
                isDecompressionBuffer = isDecompressionBuffer,
                isCompleted = existing?.isCompleted ?: false
            )
            container.blockRepository.upsertBlock(block)
            _uiState.value = _uiState.value.copy(
                statusMessage = if (existing == null) "Block added." else "Block updated.",
                errorMessage = null
            )
        }
    }

    fun deleteBlock(block: DayBlock) {
        viewModelScope.launch { container.blockRepository.deleteBlock(block) }
    }

    fun toggleCompleted(block: DayBlock) {
        viewModelScope.launch {
            if (!block.isCompleted) {
                container.blockRepository.markCompleted(block)
            } else {
                container.blockRepository.upsertBlock(block.copy(isCompleted = false))
            }
        }
    }
}
