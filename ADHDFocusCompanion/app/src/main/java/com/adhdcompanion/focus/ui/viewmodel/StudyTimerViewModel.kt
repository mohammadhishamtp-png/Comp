package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.ExamGoal
import com.adhdcompanion.focus.model.SoundscapeType
import com.adhdcompanion.focus.model.StudyMemberStatus
import com.adhdcompanion.focus.model.StudySession
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.model.StudyTimerMode
import com.adhdcompanion.focus.model.TimelineTenMinCell
import com.adhdcompanion.focus.data.repository.StudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StudyTimerUiState(
    val subjects: List<StudySubject> = emptyList(),
    val selectedSubjectId: Long? = null,
    val mode: StudyTimerMode = StudyTimerMode.STOPWATCH,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFullScreenFocus: Boolean = false,
    val isStrictLockEnabled: Boolean = true,
    val isPomodoroBreak: Boolean = false,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroCyclesCompleted: Int = 0,
    val countdownTargetMinutes: Int = 45,
    val activeSegmentElapsedSeconds: Int = 0,
    val displaySeconds: Int = 0,
    val currentSessionInterruptions: Int = 0,
    val todaysSessions: List<StudySession> = emptyList(),
    val todaysTotalSeconds: Int = 0,
    val subjectMinutesToday: Map<Long, Int> = emptyMap(),
    val timelineCells: List<TimelineTenMinCell> = emptyList(),
    val examGoals: List<ExamGoal> = emptyList(),
    val soundscapeType: SoundscapeType = SoundscapeType.NONE,
    val isSoundscapePlaying: Boolean = false,
    val soundscapeVolume: Float = 0.55f,
    val blockedDuringSessionCount: Int = 0,
    val feedbackMessage: String? = null
) {
    val selectedSubject: StudySubject?
        get() = subjects.firstOrNull { it.id == selectedSubjectId } ?: subjects.firstOrNull()
}

class StudyTimerViewModel(private val container: AppContainer) : ViewModel() {

    private val studyRepo = container.studyRepository
    private val groupRepo = container.studyGroupRepository
    private val soundscape = container.soundscapeEngine
    private val blocker = container.focusBlockerController

    private val _uiState = MutableStateFlow(StudyTimerUiState())
    val uiState: StateFlow<StudyTimerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null
    private var activeSegmentStartMillis: Long = 0L
    private var sessionStartBlockedCount: Int = 0

    init {
        viewModelScope.launch {
            studyRepo.ensureDefaultSubjects()
        }

        viewModelScope.launch {
            combine(
                studyRepo.observeActiveSubjects(),
                studyRepo.observeSessionsForDay(),
                studyRepo.observeExamGoals()
            ) { subjects, sessions, goals ->
                Triple(subjects, sessions, goals)
            }.collect { (subjects, sessions, goals) ->
                val currentSel = _uiState.value.selectedSubjectId
                val resolvedSel = if (subjects.any { it.id == currentSel }) currentSel else subjects.firstOrNull()?.id

                val totalSec = sessions.sumOf { it.durationSeconds }
                val bySubjectMin = sessions.groupBy { it.subjectId }
                    .mapValues { (_, list) -> list.sumOf { it.durationSeconds } / 60 }

                _uiState.value = _uiState.value.copy(
                    subjects = subjects,
                    selectedSubjectId = resolvedSel,
                    todaysSessions = sessions,
                    todaysTotalSeconds = totalSec,
                    subjectMinutesToday = bySubjectMin,
                    timelineCells = StudyRepository.buildTenMinuteTimeline(sessions, studyRepo.todayEpoch()),
                    examGoals = goals
                )
                syncGroupPresence()
            }
        }

        viewModelScope.launch {
            combine(
                soundscape.selectedType,
                soundscape.isPlaying,
                soundscape.volume
            ) { type, playing, vol ->
                Triple(type, playing, vol)
            }.collect { (type, playing, vol) ->
                _uiState.value = _uiState.value.copy(
                    soundscapeType = type,
                    isSoundscapePlaying = playing,
                    soundscapeVolume = vol
                )
            }
        }

        viewModelScope.launch {
            blocker.blockedEvents.collect { events ->
                if (_uiState.value.isRunning) {
                    val delta = (events.size - sessionStartBlockedCount).coerceAtLeast(0)
                    _uiState.value = _uiState.value.copy(blockedDuringSessionCount = delta)
                }
            }
        }
    }

    fun selectSubject(subjectId: Long) {
        if (_uiState.value.isRunning) return
        _uiState.value = _uiState.value.copy(selectedSubjectId = subjectId)
        syncGroupPresence()
    }

    fun focusSubjectIfIdle(subjectId: Long?, subjectName: String?) {
        if (_uiState.value.isRunning) return
        val subjects = _uiState.value.subjects
        val matched = subjects.firstOrNull { it.id == subjectId }
            ?: subjects.firstOrNull { it.name.equals(subjectName, ignoreCase = true) }
        if (matched != null) {
            _uiState.value = _uiState.value.copy(selectedSubjectId = matched.id)
        }
    }

    fun startOrSwitchSubject(subjectId: Long) {
        val current = _uiState.value
        if (current.isRunning && current.selectedSubjectId == subjectId) {
            pauseSession("Paused from subject row")
            return
        }
        if (current.isRunning || current.isPaused) {
            finishAndSaveSession()
        }
        _uiState.value = _uiState.value.copy(selectedSubjectId = subjectId)
        startOrResume()
    }

    fun setMode(mode: StudyTimerMode) {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        val initialDisplay = when (mode) {
            StudyTimerMode.STOPWATCH -> 0
            StudyTimerMode.POMODORO -> _uiState.value.pomodoroFocusMinutes * 60
            StudyTimerMode.COUNTDOWN -> _uiState.value.countdownTargetMinutes * 60
        }
        _uiState.value = _uiState.value.copy(
            mode = mode,
            isPomodoroBreak = false,
            displaySeconds = initialDisplay,
            activeSegmentElapsedSeconds = 0
        )
    }

    fun setPomodoroConfig(focusMinutes: Int, breakMinutes: Int) {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        val safeFocus = focusMinutes.coerceIn(10, 90)
        val safeBreak = breakMinutes.coerceIn(3, 30)
        val display = if (_uiState.value.mode == StudyTimerMode.POMODORO) safeFocus * 60 else _uiState.value.displaySeconds
        _uiState.value = _uiState.value.copy(
            pomodoroFocusMinutes = safeFocus,
            pomodoroBreakMinutes = safeBreak,
            displaySeconds = display
        )
    }

    fun setCountdownMinutes(minutes: Int) {
        if (_uiState.value.isRunning || _uiState.value.isPaused) return
        val safe = minutes.coerceIn(5, 240)
        val display = if (_uiState.value.mode == StudyTimerMode.COUNTDOWN) safe * 60 else _uiState.value.displaySeconds
        _uiState.value = _uiState.value.copy(
            countdownTargetMinutes = safe,
            displaySeconds = display
        )
    }

    fun toggleStrictLock(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isStrictLockEnabled = enabled)
        if (_uiState.value.isRunning && !_uiState.value.isPomodoroBreak) {
            val subjectName = _uiState.value.selectedSubject?.name ?: "Study"
            if (enabled) {
                blocker.startSessionStrictLock(subjectName)
            } else {
                blocker.stopSessionStrictLock()
            }
        }
    }

    fun setFullScreenFocus(fullScreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullScreenFocus = fullScreen)
    }

    fun startOrResume() {
        val current = _uiState.value
        val subject = current.selectedSubject ?: return
        if (current.isRunning) return

        val isFreshStart = !current.isPaused
        val initialDisplay = if (isFreshStart) {
            when (current.mode) {
                StudyTimerMode.STOPWATCH -> 0
                StudyTimerMode.POMODORO -> {
                    if (current.isPomodoroBreak) current.pomodoroBreakMinutes * 60
                    else current.pomodoroFocusMinutes * 60
                }
                StudyTimerMode.COUNTDOWN -> current.countdownTargetMinutes * 60
            }
        } else {
            current.displaySeconds
        }

        if (isFreshStart) {
            sessionStartBlockedCount = blocker.blockedEvents.value.size
        }

        activeSegmentStartMillis = System.currentTimeMillis()
        _uiState.value = current.copy(
            isRunning = true,
            isPaused = false,
            displaySeconds = initialDisplay,
            activeSegmentElapsedSeconds = if (isFreshStart) 0 else current.activeSegmentElapsedSeconds,
            blockedDuringSessionCount = if (isFreshStart) 0 else current.blockedDuringSessionCount
        )

        if (current.isStrictLockEnabled && !current.isPomodoroBreak) {
            blocker.startSessionStrictLock(subject.name)
        }
        syncGroupPresence()
        startTicker()
    }

    fun pauseSession(reason: String = "Break") {
        val current = _uiState.value
        if (!current.isRunning) return
        tickerJob?.cancel()
        tickerJob = null

        val now = System.currentTimeMillis()
        val subject = current.selectedSubject

        // Save completed focus sub-segment immediately so progress is never lost
        if (subject != null && !current.isPomodoroBreak && activeSegmentStartMillis > 0L) {
            val segmentStart = activeSegmentStartMillis
            viewModelScope.launch {
                studyRepo.recordStudySession(
                    subject = subject,
                    mode = current.mode,
                    startMillis = segmentStart,
                    endMillis = now,
                    interruptionsCount = 1,
                    notes = reason
                )
            }
        }

        activeSegmentStartMillis = 0L
        blocker.stopSessionStrictLock()
        if (!current.isPomodoroBreak && subject != null) {
            blocker.recordInterruption(subject.name, reason)
        }

        _uiState.value = current.copy(
            isRunning = false,
            isPaused = true,
            currentSessionInterruptions = current.currentSessionInterruptions + 1
        )
        syncGroupPresence()
    }

    fun finishAndSaveSession() {
        val current = _uiState.value
        tickerJob?.cancel()
        tickerJob = null
        blocker.stopSessionStrictLock()

        val now = System.currentTimeMillis()
        val subject = current.selectedSubject

        if (current.isRunning && !current.isPomodoroBreak && subject != null && activeSegmentStartMillis > 0L) {
            val segStart = activeSegmentStartMillis
            val segSec = ((now - segStart) / 1000L).toInt()
            if (segSec >= 5) {
                viewModelScope.launch {
                    studyRepo.recordStudySession(
                        subject = subject,
                        mode = current.mode,
                        startMillis = segStart,
                        endMillis = now,
                        interruptionsCount = 0
                    )
                }
            }
        }

        activeSegmentStartMillis = 0L
        val resetDisplay = when (current.mode) {
            StudyTimerMode.STOPWATCH -> 0
            StudyTimerMode.POMODORO -> current.pomodoroFocusMinutes * 60
            StudyTimerMode.COUNTDOWN -> current.countdownTargetMinutes * 60
        }

        _uiState.value = current.copy(
            isRunning = false,
            isPaused = false,
            isFullScreenFocus = false,
            isPomodoroBreak = false,
            activeSegmentElapsedSeconds = 0,
            displaySeconds = resetDisplay,
            currentSessionInterruptions = 0,
            feedbackMessage = "Session saved to your timeline & stats."
        )
        syncGroupPresence()
    }

    fun skipPomodoroBreak() {
        val current = _uiState.value
        if (current.mode != StudyTimerMode.POMODORO || !current.isPomodoroBreak) return
        tickerJob?.cancel()
        tickerJob = null
        activeSegmentStartMillis = 0L
        _uiState.value = current.copy(
            isRunning = false,
            isPaused = false,
            isPomodoroBreak = false,
            displaySeconds = current.pomodoroFocusMinutes * 60,
            activeSegmentElapsedSeconds = 0
        )
        syncGroupPresence()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRunning) {
                delay(1000L)
                val current = _uiState.value
                if (!current.isRunning) break

                when (current.mode) {
                    StudyTimerMode.STOPWATCH -> {
                        _uiState.value = current.copy(
                            displaySeconds = current.displaySeconds + 1,
                            activeSegmentElapsedSeconds = current.activeSegmentElapsedSeconds + 1
                        )
                    }
                    StudyTimerMode.POMODORO -> {
                        val nextRemaining = (current.displaySeconds - 1).coerceAtLeast(0)
                        if (nextRemaining == 0) {
                            handlePomodoroPhaseComplete(current)
                        } else {
                            _uiState.value = current.copy(
                                displaySeconds = nextRemaining,
                                activeSegmentElapsedSeconds = current.activeSegmentElapsedSeconds + 1
                            )
                        }
                    }
                    StudyTimerMode.COUNTDOWN -> {
                        val nextRemaining = (current.displaySeconds - 1).coerceAtLeast(0)
                        if (nextRemaining == 0) {
                            handleCountdownGoalComplete(current)
                        } else {
                            _uiState.value = current.copy(
                                displaySeconds = nextRemaining,
                                activeSegmentElapsedSeconds = current.activeSegmentElapsedSeconds + 1
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handlePomodoroPhaseComplete(current: StudyTimerUiState) {
        val now = System.currentTimeMillis()
        val subject = current.selectedSubject

        if (!current.isPomodoroBreak) {
            // Focus phase completed -> record session, unlock, transition to break
            if (subject != null && activeSegmentStartMillis > 0L) {
                val segStart = activeSegmentStartMillis
                viewModelScope.launch {
                    studyRepo.recordStudySession(
                        subject = subject,
                        mode = StudyTimerMode.POMODORO,
                        startMillis = segStart,
                        endMillis = now,
                        interruptionsCount = 0
                    )
                }
            }
            blocker.stopSessionStrictLock()
            activeSegmentStartMillis = now
            _uiState.value = current.copy(
                isPomodoroBreak = true,
                pomodoroCyclesCompleted = current.pomodoroCyclesCompleted + 1,
                displaySeconds = current.pomodoroBreakMinutes * 60,
                activeSegmentElapsedSeconds = 0,
                feedbackMessage = "Focus sprint done! ${current.pomodoroBreakMinutes}m break started."
            )
            syncGroupPresence()
        } else {
            // Break phase completed -> return to ready Focus state
            tickerJob?.cancel()
            tickerJob = null
            activeSegmentStartMillis = 0L
            _uiState.value = current.copy(
                isRunning = false,
                isPaused = false,
                isPomodoroBreak = false,
                displaySeconds = current.pomodoroFocusMinutes * 60,
                activeSegmentElapsedSeconds = 0,
                feedbackMessage = "Break complete! Ready for your next Pomodoro."
            )
            syncGroupPresence()
        }
    }

    private fun handleCountdownGoalComplete(current: StudyTimerUiState) {
        tickerJob?.cancel()
        tickerJob = null
        blocker.stopSessionStrictLock()
        val now = System.currentTimeMillis()
        val subject = current.selectedSubject

        if (subject != null && activeSegmentStartMillis > 0L) {
            val segStart = activeSegmentStartMillis
            viewModelScope.launch {
                studyRepo.recordStudySession(
                    subject = subject,
                    mode = StudyTimerMode.COUNTDOWN,
                    startMillis = segStart,
                    endMillis = now,
                    interruptionsCount = 0
                )
            }
        }
        activeSegmentStartMillis = 0L
        _uiState.value = current.copy(
            isRunning = false,
            isPaused = false,
            isFullScreenFocus = false,
            displaySeconds = current.countdownTargetMinutes * 60,
            activeSegmentElapsedSeconds = 0,
            feedbackMessage = "Goal timer completed (${current.countdownTargetMinutes}m)!"
        )
        syncGroupPresence()
    }

    // Subject CRUD
    fun addSubject(name: String, colorHex: Long, dailyTargetMinutes: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = studyRepo.addSubject(name, colorHex, dailyTargetMinutes)
            if (!_uiState.value.isRunning) {
                _uiState.value = _uiState.value.copy(selectedSubjectId = newId)
            }
        }
    }

    fun updateSubject(subject: StudySubject) {
        viewModelScope.launch {
            studyRepo.updateSubject(subject)
        }
    }

    fun archiveSubject(subjectId: Long) {
        if (_uiState.value.subjects.size <= 1) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Keep at least one active subject.")
            return
        }
        viewModelScope.launch {
            studyRepo.archiveSubject(subjectId)
        }
    }

    // Soundscape controls
    fun toggleSoundscape(type: SoundscapeType) {
        soundscape.selectAndToggle(type)
    }

    fun setSoundscapeVolume(volume: Float) {
        soundscape.setVolume(volume)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }

    private fun syncGroupPresence() {
        val current = _uiState.value
        val subject = current.selectedSubject
        val status = when {
            current.isRunning && !current.isPomodoroBreak -> StudyMemberStatus.STUDYING
            current.isPaused || current.isPomodoroBreak -> StudyMemberStatus.BREAK
            else -> StudyMemberStatus.OFFLINE
        }
        val todayMinutes = current.todaysTotalSeconds / 60
        groupRepo.updateMyLivePresence(
            status = status,
            activeSubject = subject?.name ?: "Study",
            subjectColorHex = subject?.colorHex ?: 0xFF2EE6A6L,
            todayMinutes = todayMinutes
        )
    }

    override fun onCleared() {
        tickerJob?.cancel()
        blocker.stopSessionStrictLock()
        soundscape.stop()
        super.onCleared()
    }
}
