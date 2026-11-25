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

data class FocusLockConfig(
    val isMasterEnabled: Boolean = false,
    val mode: LockMode = LockMode.BLOCK_LIST,
    val selectedPackages: Set<String> = emptySet(),
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
    val temporaryUnlockUntilMillis: Long = 0L
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
