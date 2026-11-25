package com.adhdcompanion.focus.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockedAppEvent
import com.adhdcompanion.focus.model.BlockedAppSummary
import com.adhdcompanion.focus.model.DailyFocusStat
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.WeeklyDashboardStats
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

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val _stats = MutableStateFlow(WeeklyDashboardStats())
    val stats: StateFlow<WeeklyDashboardStats> = _stats.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                container.blockRepository.observeBlocksInLastWeek(),
                container.blockRepository.observeAllCompletedBlockEpochs(),
                container.focusLockManager.blockedEvents
            ) { weekBlocks, completedByEpoch, blockedEvents ->
                buildStats(weekBlocks, completedByEpoch, blockedEvents)
            }.collect { computed ->
                _stats.value = computed
            }
        }
    }

    private fun buildStats(
        weekBlocks: List<Pair<Long, DayBlock>>,
        completedMinutesByEpoch: Map<Long, Int>,
        blockedEvents: List<BlockedAppEvent>
    ): WeeklyDashboardStats {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayEpoch = today.toEpochDay()
        val startEpoch = todayEpoch - 6L
        val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

        val blocksByDay = weekBlocks.groupBy({ it.first }, { it.second })
        val eventsInWeek = blockedEvents.filter { it.dayEpoch in startEpoch..todayEpoch }
        val eventsByDay = eventsInWeek.groupBy { it.dayEpoch }

        val dailyStats = (0L..6L).map { offset ->
            val epoch = startEpoch + offset
            val date = LocalDate.ofEpochDay(epoch)
            val dayBlocks = blocksByDay[epoch].orEmpty()
            val completedFocusBlocks = dayBlocks.filter { it.isCompleted && !it.isDecompressionBuffer }
            val focusedMinutes = completedFocusBlocks.sumOf { it.durationMinutes }
            val blockedCount = eventsByDay[epoch]?.size ?: 0

            DailyFocusStat(
                dayEpoch = epoch,
                dayLabel = if (epoch == todayEpoch) "Today"
                else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                dateLabel = date.format(dateFormatter),
                focusedMinutes = focusedMinutes,
                completedBlocks = completedFocusBlocks.size,
                blockedAttempts = blockedCount,
                isToday = epoch == todayEpoch
            )
        }

        val activeEpochs = completedMinutesByEpoch
            .filterValues { it > 0 }
            .keys
            .sorted()

        val currentStreak = computeCurrentStreak(activeEpochs.toSet(), todayEpoch)
        val bestStreak = computeBestStreak(activeEpochs)

        val topApps = eventsInWeek
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                BlockedAppSummary(
                    packageName = pkg,
                    appLabel = list.last().appLabel,
                    attemptCount = list.size
                )
            }
            .sortedByDescending { it.attemptCount }

        return WeeklyDashboardStats(
            dailyStats = dailyStats,
            topBlockedApps = topApps,
            currentStreakDays = currentStreak,
            bestStreakDays = bestStreak,
            totalWeeklyFocusMinutes = dailyStats.sumOf { it.focusedMinutes },
            totalWeeklyCompletedBlocks = dailyStats.sumOf { it.completedBlocks },
            totalWeeklyBlockedAttempts = dailyStats.sumOf { it.blockedAttempts }
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

    private fun computeBestStreak(sortedActiveEpochs: List<Long>): Int {
        if (sortedActiveEpochs.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sortedActiveEpochs.size) {
            if (sortedActiveEpochs[i] == sortedActiveEpochs[i - 1] + 1L) {
                run++
                if (run > best) best = run
            } else {
                run = 1
            }
        }
        return best
    }
}
