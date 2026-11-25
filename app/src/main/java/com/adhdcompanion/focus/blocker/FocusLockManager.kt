package com.adhdcompanion.focus.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.adhdcompanion.focus.alarm.AlarmScheduler
import com.adhdcompanion.focus.model.FocusLockConfig
import com.adhdcompanion.focus.model.FocusLockWindow
import com.adhdcompanion.focus.model.InstalledAppInfo
import com.adhdcompanion.focus.model.LockEvaluationState
import com.adhdcompanion.focus.model.LockMode
import com.adhdcompanion.focus.notification.NotificationHelper
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Central state & evaluation engine for Scheduled App Blocker & Anti-Impulse Lock.
 * Persists immediately to dedicated SharedPreferences so AccessibilityService,
 * BootCompletedReceiver, AlarmReceiver, and UI share consistent real-time rules.
 */
class FocusLockManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<FocusLockConfig> = _config.asStateFlow()

    private val _evaluation = MutableStateFlow(computeEvaluation(_config.value, System.currentTimeMillis()))
    val evaluation: StateFlow<LockEvaluationState> = _evaluation.asStateFlow()

    @Synchronized
    private fun loadConfig(): FocusLockConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return FocusLockConfig()
        return runCatching { gson.fromJson(raw, FocusLockConfig::class.java) }
            .getOrNull() ?: FocusLockConfig()
    }

    @Synchronized
    private fun saveConfig(newConfig: FocusLockConfig) {
        prefs.edit().putString(KEY_CONFIG_JSON, gson.toJson(newConfig)).apply()
        _config.value = newConfig
        _evaluation.value = computeEvaluation(newConfig, System.currentTimeMillis())
    }

    /**
     * Re-evaluates the active lock state right now, persists automatic cooldown -> temp unlock
     * transitions or expired temp unlocks, updates the persistent notification, and schedules
     * the next start/end boundary alarm.
     */
    @Synchronized
    fun evaluateAndSyncState(context: Context = appContext): LockEvaluationState {
        val now = System.currentTimeMillis()
        var current = _config.value
        var mutated = false

        // Transition completed cooldown -> 5-minute temporary edit/unlock window
        if (current.unlockCooldownEndMillis > 0L && now >= current.unlockCooldownEndMillis) {
            current = current.copy(
                unlockCooldownEndMillis = 0L,
                temporaryUnlockUntilMillis = now + TEMP_UNLOCK_DURATION_MS
            )
            mutated = true
        }

        // Clear expired temporary unlock so lock re-arms automatically
        if (current.temporaryUnlockUntilMillis > 0L && now >= current.temporaryUnlockUntilMillis) {
            current = current.copy(temporaryUnlockUntilMillis = 0L)
            mutated = true
        }

        if (mutated) {
            saveConfig(current)
        } else {
            _evaluation.value = computeEvaluation(current, now)
        }

        val state = _evaluation.value
        if (state.isBlockingActive && state.activeWindow != null) {
            NotificationHelper.updateFocusLockNotification(
                context = context,
                windowName = state.activeWindow.name.ifBlank { "Focus Window" },
                untilText = formatMinuteOfDay(state.activeWindow.endMinuteOfDay)
            )
        } else {
            NotificationHelper.cancelFocusLockNotification(context)
        }

        val nextBoundary = computeNextBoundaryMillis(current, now)
        val scheduler = AlarmScheduler(context)
        if (nextBoundary != null) {
            scheduler.scheduleLockBoundaryEvaluation(nextBoundary)
        } else {
            scheduler.cancelLockBoundaryEvaluation()
        }

        return state
    }

    fun evaluateNow(nowMillis: Long = System.currentTimeMillis()): LockEvaluationState {
        val current = _config.value
        if ((current.unlockCooldownEndMillis > 0L && nowMillis >= current.unlockCooldownEndMillis) ||
            (current.temporaryUnlockUntilMillis > 0L && nowMillis >= current.temporaryUnlockUntilMillis)
        ) {
            return evaluateAndSyncState(appContext)
        }
        val computed = computeEvaluation(current, nowMillis)
        _evaluation.value = computed
        return computed
    }

    fun canEditRules(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val current = _config.value
        if (!current.isMasterEnabled) return true
        val eval = evaluateNow(nowMillis)
        return !eval.isInScheduledWindow || eval.isTemporaryUnlocked
    }

    @Synchronized
    fun updateConfigIfAllowed(transform: (FocusLockConfig) -> FocusLockConfig): Boolean {
        if (!canEditRules()) return false
        val updated = transform(_config.value)
        saveConfig(updated)
        evaluateAndSyncState(appContext)
        return true
    }

    @Synchronized
    fun startUnlockCooldown(): LockEvaluationState {
        val current = _config.value
        val now = System.currentTimeMillis()
        if (current.unlockCooldownEndMillis > now) {
            return evaluateNow(now)
        }
        val durationMs = current.cooldownMinutes.coerceIn(1, 60) * 60_000L
        saveConfig(
            current.copy(
                unlockCooldownEndMillis = now + durationMs,
                temporaryUnlockUntilMillis = 0L
            )
        )
        return evaluateAndSyncState(appContext)
    }

    @Synchronized
    fun cancelUnlockCooldown(): LockEvaluationState {
        saveConfig(_config.value.copy(unlockCooldownEndMillis = 0L))
        return evaluateAndSyncState(appContext)
    }

    @Synchronized
    fun relockImmediately(): LockEvaluationState {
        saveConfig(
            _config.value.copy(
                unlockCooldownEndMillis = 0L,
                temporaryUnlockUntilMillis = 0L
            )
        )
        return evaluateAndSyncState(appContext)
    }

    fun isPackageBlockedNow(packageName: String, essentialPackages: Set<String>): Boolean {
        if (packageName.isBlank() || packageName == appContext.packageName) return false
        if (packageName in essentialPackages) return false

        val eval = evaluateNow()
        if (!eval.isBlockingActive) return false

        val cfg = _config.value
        return when (cfg.mode) {
            LockMode.BLOCK_LIST -> packageName in cfg.selectedPackages
            LockMode.ALLOW_LIST -> packageName !in cfg.selectedPackages
        }
    }

    fun isTamperAttempt(
        packageName: String,
        className: String?,
        screenTokens: List<String>
    ): Boolean {
        val eval = evaluateNow()
        if (!eval.isBlockingActive) return false

        val isSystemSettingsOrInstaller =
            packageName == "com.android.settings" ||
                packageName == "com.android.packageinstaller" ||
                packageName == "com.google.android.packageinstaller" ||
                packageName == "com.samsung.android.packageinstaller"

        if (!isSystemSettingsOrInstaller) return false

        val combined = buildString {
            className?.let { append(it).append(' ') }
            screenTokens.forEach { append(it).append(' ') }
        }.lowercase(Locale.US)

        val targetsSelf =
            combined.contains("focus companion") ||
                combined.contains(appContext.packageName.lowercase(Locale.US)) ||
                combined.contains("adhdcompanion")

        val isSensitiveScreen =
            combined.contains("app info") ||
                combined.contains("installedappdetails") ||
                combined.contains("uninstall") ||
                combined.contains("force stop") ||
                combined.contains("clear data") ||
                combined.contains("clear storage") ||
                combined.contains("accessibility")

        return targetsSelf && isSensitiveScreen
    }

    private fun computeEvaluation(cfg: FocusLockConfig, nowMillis: Long): LockEvaluationState {
        if (!cfg.isMasterEnabled) {
            return LockEvaluationState(isInScheduledWindow = false, isBlockingActive = false)
        }
        val activeWindow = findActiveWindow(cfg.windows, nowMillis)
        val inWindow = activeWindow != null
        if (!inWindow) {
            return LockEvaluationState(isInScheduledWindow = false, isBlockingActive = false)
        }

        val isTempUnlocked = cfg.temporaryUnlockUntilMillis > nowMillis
        val tempRemainingSec = if (isTempUnlocked) {
            ((cfg.temporaryUnlockUntilMillis - nowMillis + 999L) / 1000L).toInt()
        } else 0

        val isCooldownRunning = !isTempUnlocked && cfg.unlockCooldownEndMillis > nowMillis
        val cooldownRemainingSec = if (isCooldownRunning) {
            ((cfg.unlockCooldownEndMillis - nowMillis + 999L) / 1000L).toInt()
        } else 0

        return LockEvaluationState(
            isInScheduledWindow = true,
            isBlockingActive = !isTempUnlocked,
            activeWindow = activeWindow,
            isCooldownRunning = isCooldownRunning,
            cooldownRemainingSeconds = cooldownRemainingSec,
            isTemporaryUnlocked = isTempUnlocked,
            tempUnlockRemainingSeconds = tempRemainingSec
        )
    }

    private fun findActiveWindow(windows: List<FocusLockWindow>, nowMillis: Long): FocusLockWindow? {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val todayDow = dateTime.dayOfWeek.value // 1=Mon..7=Sun
        val yesterdayDow = dateTime.minusDays(1).dayOfWeek.value
        val minuteOfDay = dateTime.hour * 60 + dateTime.minute

        return windows.firstOrNull { w ->
            if (!w.isEnabled || w.activeDays.isEmpty()) return@firstOrNull false
            when {
                w.startMinuteOfDay < w.endMinuteOfDay -> {
                    todayDow in w.activeDays &&
                        minuteOfDay in w.startMinuteOfDay until w.endMinuteOfDay
                }
                w.startMinuteOfDay > w.endMinuteOfDay -> {
                    (todayDow in w.activeDays && minuteOfDay >= w.startMinuteOfDay) ||
                        (yesterdayDow in w.activeDays && minuteOfDay < w.endMinuteOfDay)
                }
                else -> {
                    todayDow in w.activeDays
                }
            }
        }
    }

    private fun computeNextBoundaryMillis(cfg: FocusLockConfig, nowMillis: Long): Long? {
        if (!cfg.isMasterEnabled) return null
        val enabledWindows = cfg.windows.filter { it.isEnabled && it.activeDays.isNotEmpty() }
        if (enabledWindows.isEmpty()) return null

        val zone = ZoneId.systemDefault()
        val nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var earliestMillis: Long? = null

        for (dayOffset in 0L..7L) {
            val date = nowLdt.toLocalDate().plusDays(dayOffset)
            val dow = date.dayOfWeek.value
            for (window in enabledWindows) {
                if (dow in window.activeDays) {
                    val startLdt = date.atTime(window.startMinuteOfDay / 60, window.startMinuteOfDay % 60)
                    val startMs = startLdt.atZone(zone).toInstant().toEpochMilli()
                    if (startMs > nowMillis && (earliestMillis == null || startMs < earliestMillis)) {
                        earliestMillis = startMs
                    }

                    val endDate = if (window.endMinuteOfDay <= window.startMinuteOfDay) date.plusDays(1) else date
                    val endLdt = endDate.atTime(window.endMinuteOfDay / 60, window.endMinuteOfDay % 60)
                    val endMs = endLdt.atZone(zone).toInstant().toEpochMilli()
                    if (endMs > nowMillis && (earliestMillis == null || endMs < earliestMillis)) {
                        earliestMillis = endMs
                    }
                }
            }
        }

        if (cfg.temporaryUnlockUntilMillis > nowMillis) {
            val tempEnd = cfg.temporaryUnlockUntilMillis
            if (earliestMillis == null || tempEnd < earliestMillis) {
                earliestMillis = tempEnd
            }
        }
        return earliestMillis
    }

    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = appContext.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        val seen = HashSet<String>()
        val results = mutableListOf<InstalledAppInfo>()
        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == appContext.packageName || !seen.add(pkg)) continue
            val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            results.add(InstalledAppInfo(packageName = pkg, label = label))
        }
        return results.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun getAppLabel(packageName: String): String {
        return runCatching {
            val info = appContext.packageManager.getApplicationInfo(packageName, 0)
            appContext.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    companion object {
        private const val PREFS_NAME = "focus_lock_prefs"
        private const val KEY_CONFIG_JSON = "focus_lock_config_json"
        private const val TEMP_UNLOCK_DURATION_MS = 5 * 60_000L

        @Volatile
        private var INSTANCE: FocusLockManager? = null

        fun getInstance(context: Context): FocusLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FocusLockManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatMinuteOfDay(minuteOfDay: Int): String {
            val m = minuteOfDay.coerceIn(0, 23 * 60 + 59)
            val h24 = m / 60
            val min = m % 60
            val amPm = if (h24 < 12) "AM" else "PM"
            val h12 = when {
                h24 == 0 -> 12
                h24 > 12 -> h24 - 12
                else -> h24
            }
            return "%d:%02d %s".format(Locale.getDefault(), h12, min, amPm)
        }
    }
}
