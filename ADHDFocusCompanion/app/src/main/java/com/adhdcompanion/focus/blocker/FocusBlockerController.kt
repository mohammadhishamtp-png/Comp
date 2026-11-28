package com.adhdcompanion.focus.blocker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.adhdcompanion.focus.model.BlockedAppEvent
import com.adhdcompanion.focus.model.FocusInterruptionEvent
import com.adhdcompanion.focus.model.FocusLockConfig
import com.adhdcompanion.focus.model.LockEvaluationState
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean abstraction over foreground app inspection, strict study session locking,
 * allowed study-tools whitelisting, and optional Accessibility-based Shorts/Reels blocking.
 */
interface FocusBlockerController {
    val config: StateFlow<FocusLockConfig>
    val evaluation: StateFlow<LockEvaluationState>
    val blockedEvents: StateFlow<List<BlockedAppEvent>>

    fun hasUsageStatsAccess(): Boolean
    fun isAccessibilityBlockerRunning(): Boolean
    fun queryCurrentForegroundPackage(): String?
    fun startSessionStrictLock(subjectName: String, durationMinutes: Int = 180)
    fun stopSessionStrictLock()
    fun recordInterruption(subjectName: String, reason: String)
    fun toggleAllowedStudyTool(packageName: String): Boolean
    fun setBlockShortsAndReels(enabled: Boolean): Boolean
    fun recentInterruptions(): List<FocusInterruptionEvent>
}

class AndroidFocusBlockerController(
    private val context: Context,
    private val lockManager: FocusLockManager
) : FocusBlockerController {

    private val appContext = context.applicationContext

    override val config: StateFlow<FocusLockConfig> = lockManager.config
    override val evaluation: StateFlow<LockEvaluationState> = lockManager.evaluation
    override val blockedEvents: StateFlow<List<BlockedAppEvent>> = lockManager.blockedEvents

    override fun hasUsageStatsAccess(): Boolean =
        FocusBlockerAccessibilityService.hasUsageStatsPermission(appContext)

    override fun isAccessibilityBlockerRunning(): Boolean =
        FocusBlockerAccessibilityService.isServiceEnabled(appContext)

    override fun queryCurrentForegroundPackage(): String? {
        if (!hasUsageStatsAccess()) return FocusBlockerAccessibilityService.lastForegroundPackage
        return runCatching {
            val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return FocusBlockerAccessibilityService.lastForegroundPackage
            val end = System.currentTimeMillis()
            val begin = end - 10_000L
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.timeStamp >= latestTs &&
                    !event.packageName.isNullOrBlank()
                ) {
                    latestTs = event.timeStamp
                    latestPkg = event.packageName
                }
            }
            latestPkg ?: FocusBlockerAccessibilityService.lastForegroundPackage
        }.getOrDefault(FocusBlockerAccessibilityService.lastForegroundPackage)
    }

    override fun startSessionStrictLock(subjectName: String, durationMinutes: Int) {
        lockManager.startSessionStrictLock(subjectName, durationMinutes)
    }

    override fun stopSessionStrictLock() {
        lockManager.stopSessionStrictLock()
    }

    override fun recordInterruption(subjectName: String, reason: String) {
        lockManager.recordInterruption(subjectName, reason)
    }

    override fun toggleAllowedStudyTool(packageName: String): Boolean =
        lockManager.toggleAllowedStudyTool(packageName)

    override fun setBlockShortsAndReels(enabled: Boolean): Boolean =
        lockManager.setBlockShortsAndReels(enabled)

    override fun recentInterruptions(): List<FocusInterruptionEvent> =
        lockManager.config.value.interruptionEvents
}
