package com.adhdcompanion.focus.model

/**
 * A single block of the day: a study session, a meds reminder, a meditation
 * pause, a meal, a decompression buffer, etc. This is the core unit the
 * whole app revolves around.
 */
data class DayBlock(
    val id: Long = 0,
    val title: String,
    val why: String,
    val microStep: String,
    val startMinuteOfDay: Int,   // minutes since midnight, e.g. 9:30am = 570
    val durationMinutes: Int,
    val category: BlockCategory = BlockCategory.STUDY,
    val isDecompressionBuffer: Boolean = false,
    val isCompleted: Boolean = false
)

enum class BlockCategory {
    STUDY, CODING, MEDS, MEDITATION, MEAL, MOVEMENT, BUFFER, OTHER
}

data class BrainDumpEntry(
    val id: Long = 0,
    val text: String,
    val createdAtMillis: Long,
    val sentToPlanner: Boolean = false
)

/**
 * Input the user gives the AI Planner to generate a day.
 */
data class PlannerInput(
    val availableHours: String,
    val energyLevel: EnergyLevel,
    val goalsAndBlocks: String
)

enum class EnergyLevel { LOW, MEDIUM, HIGH }

/**
 * A single unblocker micro-step the AI supporter returns when the user is stuck.
 */
data class MicroStep(
    val id: Int,
    val text: String,
    val isChecked: Boolean = false
)

/**
 * The full response from the "I'm stuck" AI supporter flow.
 */
data class UnblockerResponse(
    val calmMessage: String,
    val whyReminder: String,
    val microSteps: List<MicroStep>
)

/**
 * Structured low-friction explanation returned by the Doubt Solver.
 */
data class DoubtSolution(
    val coreIntuition: String,
    val stepByStep: List<String>,
    val twoMinuteMicroStep: String
)

/**
 * Whether the blocker blocks chosen distracting apps or permits only chosen allowed apps.
 */
enum class LockMode {
    BLOCK_LIST,
    ALLOW_LIST
}

/**
 * A scheduled focus lock window.
 * activeDays uses ISO DayOfWeek values: 1 (Monday) .. 7 (Sunday).
 */
data class FocusLockWindow(
    val id: String,
    val name: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val isEnabled: Boolean = true
)

data class FocusInterruptionEvent(
    val timestampMillis: Long,
    val subjectName: String,
    val reason: String
)

data class FocusLockConfig(
    val isMasterEnabled: Boolean = false,
    val mode: LockMode = LockMode.BLOCK_LIST,
    val selectedPackages: Set<String> = emptySet(),
    val allowedStudyTools: Set<String> = emptySet(),
    val blockShortsAndReels: Boolean = false,
    val windows: List<FocusLockWindow> = listOf(
        FocusLockWindow(
            id = "default_study_window",
            name = "Deep Study Focus",
            startMinuteOfDay = 9 * 60,
            endMinuteOfDay = 12 * 60,
            activeDays = setOf(1, 2, 3, 4, 5),
            isEnabled = true
        )
    ),
    val cooldownMinutes: Int = 10,
    val unlockCooldownEndMillis: Long = 0L,
    val temporaryUnlockUntilMillis: Long = 0L,
    val quickLockUntilMillis: Long = 0L,
    val hasExitPassword: Boolean = false,
    val interruptionEvents: List<FocusInterruptionEvent> = emptyList()
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

data class LockEvaluationState(
    val isInScheduledWindow: Boolean = false,
    val isBlockingActive: Boolean = false,
    val activeWindow: FocusLockWindow? = null,
    val isCooldownRunning: Boolean = false,
    val cooldownRemainingSeconds: Int = 0,
    val isTemporaryUnlocked: Boolean = false,
    val tempUnlockRemainingSeconds: Int = 0
)

data class BlockedAppEvent(
    val dayEpoch: Long,
    val packageName: String,
    val appLabel: String,
    val timestampMillis: Long
)

data class DailyFocusStat(
    val dayEpoch: Long,
    val dayLabel: String,
    val dateLabel: String,
    val focusedMinutes: Int,
    val completedBlocks: Int,
    val blockedAttempts: Int,
    val isToday: Boolean
)

data class BlockedAppSummary(
    val packageName: String,
    val appLabel: String,
    val attemptCount: Int
)

data class WeeklyDashboardStats(
    val dailyStats: List<DailyFocusStat> = emptyList(),
    val topBlockedApps: List<BlockedAppSummary> = emptyList(),
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val totalWeeklyFocusMinutes: Int = 0,
    val totalWeeklyCompletedBlocks: Int = 0,
    val totalWeeklyBlockedAttempts: Int = 0
)

enum class StudyTimerMode {
    STOPWATCH,
    POMODORO,
    COUNTDOWN
}

enum class SoundscapeType(val label: String, val subtitle: String) {
    NONE("Off", "Silent"),
    BROWN_NOISE("Brown Noise", "Deep warm focus mask"),
    SOFT_RAIN("Soft Rain", "Gentle ambient rain"),
    TONE_40HZ("40Hz Focus", "Subtle gamma pulse")
}

data class StudySubject(
    val id: Long = 0,
    val name: String,
    val colorHex: Long,
    val dailyTargetMinutes: Int = 90,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false
)

data class StudySession(
    val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val subjectColorHex: Long,
    val mode: StudyTimerMode = StudyTimerMode.STOPWATCH,
    val startMillis: Long,
    val endMillis: Long,
    val durationSeconds: Int,
    val dayEpoch: Long,
    val interruptionsCount: Int = 0,
    val notes: String = ""
)

data class StudyTodoItem(
    val id: Long = 0,
    val subjectId: Long,
    val subjectName: String = "",
    val subjectColorHex: Long = 0xFF2EE6A6L,
    val title: String,
    val dayEpoch: Long,
    val isCompleted: Boolean = false,
    val estimatedMinutes: Int = 30,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class ExamGoal(
    val id: Long = 0,
    val title: String,
    val targetDateEpoch: Long,
    val targetDailyMinutes: Int = 240,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class TimelineTenMinCell(
    val hourOfDay: Int,
    val slotIndex: Int, // 0..5 (00, 10, 20, 30, 40, 50)
    val subjectName: String? = null,
    val colorHex: Long? = null,
    val minutesCovered: Int = 0,
    val sessionId: Long? = null
)

data class HeatmapDayCell(
    val dayEpoch: Long,
    val dateLabel: String,
    val focusedMinutes: Int,
    val intensityLevel: Int // 0..4
)

data class SubjectBreakdownItem(
    val subjectId: Long,
    val subjectName: String,
    val colorHex: Long,
    val totalMinutes: Int,
    val dailyTargetMinutes: Int,
    val shareFraction: Float
)

data class StudyStatsOverview(
    val todayStudyMinutes: Int = 0,
    val weeklyStudyMinutes: Int = 0,
    val dailyAverageMinutes: Int = 0,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val heatmapDays: List<HeatmapDayCell> = emptyList(),
    val subjectBreakdown: List<SubjectBreakdownItem> = emptyList(),
    val weeklyDailyStats: List<DailyFocusStat> = emptyList(),
    val topBlockedApps: List<BlockedAppSummary> = emptyList(),
    val totalWeeklyBlockedAttempts: Int = 0
)

enum class StudyMemberStatus {
    STUDYING,
    BREAK,
    OFFLINE
}

data class StudyGroupMember(
    val id: String,
    val name: String,
    val status: StudyMemberStatus,
    val activeSubject: String,
    val subjectColorHex: Long,
    val todayMinutes: Int,
    val isMe: Boolean = false,
    val lastNudgeFromMeMillis: Long = 0L
)

data class StudyNudgeEvent(
    val id: String,
    val fromName: String,
    val toName: String,
    val message: String,
    val timestampMillis: Long
)

data class StudyRoom(
    val id: String,
    val name: String,
    val code: String,
    val description: String,
    val dailyGoalMinutes: Int,
    val members: List<StudyGroupMember>,
    val recentNudges: List<StudyNudgeEvent> = emptyList()
)
