package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.data.repository.StudyRepository
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockedAppEvent
import com.adhdcompanion.focus.model.BlockedAppSummary
import com.adhdcompanion.focus.model.DailyFocusStat
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.StudySession
import com.adhdcompanion.focus.model.StudyStatsOverview
import com.adhdcompanion.focus.model.StudySubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val _overview = MutableStateFlow(StudyStatsOverview())
    val overview: StateFlow<StudyStatsOverview> = _overview.asStateFlow()

    init {
        viewModelScope.launch {
            container.studyRepository.ensureDefaultSubjects()
        }

        viewModelScope.launch {
            combine(
                container.studyRepository.observeAllSessions(),
                container.studyRepository.observeActiveSubjects(),
                container.blockRepository.observeBlocksInLastWeek(),
                container.blockRepository.observeAllCompletedBlockEpochs(),
                container.focusLockManager.blockedEvents
            ) { allSessions, subjects, weekBlocks, completedBlocksByEpoch, blockedEvents ->
                computeOverview(
                    allSessions = allSessions,
                    subjects = subjects,
                    weekBlocks = weekBlocks,
                    completedBlocksByEpoch = completedBlocksByEpoch,
                    blockedEvents = blockedEvents
                )
            }.collect { computed ->
                _overview.value = computed
            }
        }
    }

    private fun computeOverview(
        allSessions: List<StudySession>,
        subjects: List<StudySubject>,
        weekBlocks: List<Pair<Long, DayBlock>>,
        completedBlocksByEpoch: Map<Long, Int>,
        blockedEvents: List<BlockedAppEvent>
    ): StudyStatsOverview {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayEpoch = today.toEpochDay()
        val weekStartEpoch = todayEpoch - 6L
        val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

        val sessionSecondsByDay = allSessions.groupBy { it.dayEpoch }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds } }

        val mergedMinutesByEpoch = mutableMapOf<Long, Int>()
        completedBlocksByEpoch.forEach { (epoch, mins) ->
            if (mins > 0) mergedMinutesByEpoch[epoch] = (mergedMinutesByEpoch[epoch] ?: 0) + mins
        }
        sessionSecondsByDay.forEach { (epoch, secs) ->
            val mins = secs / 60
            if (mins > 0 || secs > 0) {
                mergedMinutesByEpoch[epoch] = (mergedMinutesByEpoch[epoch] ?: 0) + mins.coerceAtLeast(1)
            }
        }

        val blocksByDay = weekBlocks.groupBy({ it.first }, { it.second })
        val eventsInWeek = blockedEvents.filter { it.dayEpoch in weekStartEpoch..todayEpoch }
        val eventsByDay = eventsInWeek.groupBy { it.dayEpoch }

        val weeklyDailyStats = (0L..6L).map { offset ->
            val epoch = weekStartEpoch + offset
            val date = LocalDate.ofEpochDay(epoch)
            val completedDayBlocks = blocksByDay[epoch].orEmpty()
                .filter { it.isCompleted && !it.isDecompressionBuffer }
            val sessionMins = (sessionSecondsByDay[epoch] ?: 0) / 60
            val blockMins = completedDayBlocks.sumOf { it.durationMinutes }

            DailyFocusStat(
                dayEpoch = epoch,
                dayLabel = if (epoch == todayEpoch) "Today"
                else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dateLabel = date.format(formatter),
                focusedMinutes = sessionMins + blockMins,
                completedBlocks = completedDayBlocks.size + allSessions.count { it.dayEpoch == epoch },
                blockedAttempts = eventsByDay[epoch]?.size ?: 0,
                isToday = epoch == todayEpoch
            )
        }

        val todayMinutes = mergedMinutesByEpoch[todayEpoch] ?: 0
        val weeklyMinutes = weeklyDailyStats.sumOf { it.focusedMinutes }
        val dailyAvg = weeklyMinutes / 7

        val activeEpochs = mergedMinutesByEpoch
            .filterValues { it > 0 }
            .keys
            .sorted()

        val currentStreak = computeCurrentStreak(activeEpochs.toSet(), todayEpoch)
        val bestStreak = computeBestStreak(activeEpochs)

        val heatmap = StudyRepository.buildHeatmapDays(mergedMinutesByEpoch, daysCount = 91)
        val weekSessions = allSessions.filter { it.dayEpoch in weekStartEpoch..todayEpoch }
        val breakdown = StudyRepository.buildSubjectBreakdown(weekSessions, subjects)

        val topBlockedApps = eventsInWeek
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                BlockedAppSummary(
                    packageName = pkg,
                    appLabel = list.last().appLabel,
                    attemptCount = list.size
                )
            }
            .sortedByDescending { it.attemptCount }

        return StudyStatsOverview(
            todayStudyMinutes = todayMinutes,
            weeklyStudyMinutes = weeklyMinutes,
            dailyAverageMinutes = dailyAvg,
            currentStreakDays = currentStreak,
            bestStreakDays = bestStreak,
            heatmapDays = heatmap,
            subjectBreakdown = breakdown,
            weeklyDailyStats = weeklyDailyStats,
            topBlockedApps = topBlockedApps,
            totalWeeklyBlockedAttempts = eventsInWeek.size
        )
    }

    private fun computeCurrentStreak(activeEpochs: Set<Long>, todayEpoch: Long): Int {
        if (activeEpochs.isEmpty()) return 0
        var cursor = when {
            todayEpoch in activeEpochs -> todayEpoch
            (todayEpoch - 1L) in activeEpochs -> todayEpoch - 1L
            else -> return 0
        }
        var streak = 0
        while (cursor in activeEpochs) {
            streak++
            cursor--
        }
        return streak
    }

    private fun computeBestStreak(sortedEpochs: List<Long>): Int {
        if (sortedEpochs.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sortedEpochs.size) {
            if (sortedEpochs[i] == sortedEpochs[i - 1] + 1L) {
                run++
                if (run > best) best = run
            } else {
                run = 1
            }
        }
        return best
    }
}
