package com.adhdcompanion.focus.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Base64
import com.adhdcompanion.focus.alarm.AlarmScheduler
import com.adhdcompanion.focus.model.BlockedAppEvent
import com.adhdcompanion.focus.model.FocusLockConfig
import com.adhdcompanion.focus.model.FocusLockWindow
import com.adhdcompanion.focus.model.InstalledAppInfo
import com.adhdcompanion.focus.model.LockEvaluationState
import com.adhdcompanion.focus.model.LockMode
import com.adhdcompanion.focus.notification.NotificationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

    private val _blockedEvents = MutableStateFlow(loadBlockedEvents())
    val blockedEvents: StateFlow<List<BlockedAppEvent>> = _blockedEvents.asStateFlow()

    @Suppress("USELESS_ELVIS")
    @Synchronized
    private fun loadConfig(): FocusLockConfig {
        val hasPwd = hasExitPassword()
        val raw = prefs.getString(KEY_CONFIG_JSON, null)
            ?: return FocusLockConfig(hasExitPassword = hasPwd)
        val parsed = runCatching { gson.fromJson(raw, FocusLockConfig::class.java) }
            .getOrNull() ?: return FocusLockConfig(hasExitPassword = hasPwd)
        val normalizedWait = if (parsed.cooldownMinutes <= 0) {
            10
        } else {
            parsed.cooldownMinutes.coerceIn(MIN_WAIT_MINUTES, MAX_WAIT_MINUTES)
        }
        return parsed.copy(
            mode = parsed.mode ?: LockMode.BLOCK_LIST,
            selectedPackages = parsed.selectedPackages ?: emptySet(),
            windows = parsed.windows ?: FocusLockConfig().windows,
            cooldownMinutes = normalizedWait,
            hasExitPassword = hasPwd
        )
    }

    @Synchronized
    private fun loadBlockedEvents(): List<BlockedAppEvent> {
        val raw = prefs.getString(KEY_BLOCKED_EVENTS_JSON, null) ?: return emptyList()
        val type = object : TypeToken<List<BlockedAppEvent>>() {}.type
        return runCatching { gson.fromJson<List<BlockedAppEvent>>(raw, type) }
            .getOrNull() ?: emptyList()
    }

    @Synchronized
    fun recordBlockedAttempt(packageName: String, appLabel: String) {
        val now = System.currentTimeMillis()
        val todayEpoch = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val cutoffEpoch = todayEpoch - 30L
        val updated = (_blockedEvents.value + BlockedAppEvent(
            dayEpoch = todayEpoch,
            packageName = packageName,
            appLabel = appLabel,
            timestampMillis = now
        )).filter { it.dayEpoch >= cutoffEpoch }
        prefs.edit().putString(KEY_BLOCKED_EVENTS_JSON, gson.toJson(updated)).apply()
        _blockedEvents.value = updated
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

        // Clear expired 1-tap quick lock
        if (current.quickLockUntilMillis > 0L && now >= current.quickLockUntilMillis) {
            current = current.copy(quickLockUntilMillis = 0L)
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

        FocusBlockerAccessibilityService.notifyRulesChanged()

        return state
    }

    fun evaluateNow(nowMillis: Long = System.currentTimeMillis()): LockEvaluationState {
        val current = _config.value
        if ((current.unlockCooldownEndMillis > 0L && nowMillis >= current.unlockCooldownEndMillis) ||
            (current.temporaryUnlockUntilMillis > 0L && nowMillis >= current.temporaryUnlockUntilMillis) ||
            (current.quickLockUntilMillis > 0L && nowMillis >= current.quickLockUntilMillis)
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
        // Never lock the user out of picking apps when the block list has 0 apps selected.
        if (current.mode == LockMode.BLOCK_LIST && current.selectedPackages.isEmpty()) return true
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
        val durationMs = current.cooldownMinutes.coerceIn(MIN_WAIT_MINUTES, MAX_WAIT_MINUTES) * 60_000L
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

    @Synchronized
    fun removeLockWhenUnlocked(): Boolean {
        if (!canEditRules()) return false
        saveConfig(
            _config.value.copy(
                isMasterEnabled = false,
                unlockCooldownEndMillis = 0L,
                temporaryUnlockUntilMillis = 0L,
                quickLockUntilMillis = 0L
            )
        )
        evaluateAndSyncState(appContext)
        return true
    }

    fun hasExitPassword(): Boolean {
        val hash = prefs.getString(KEY_EXIT_PASSWORD_HASH, null)
        val salt = prefs.getString(KEY_EXIT_PASSWORD_SALT, null)
        return !hash.isNullOrBlank() && !salt.isNullOrBlank()
    }

    @Synchronized
    fun setExitPassword(password: String): Boolean {
        if (!canEditRules()) return false
        if (password.length < MIN_PASSWORD_LENGTH) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hashBytes = derivePasswordHash(password, salt)
        prefs.edit()
            .putString(KEY_EXIT_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_EXIT_PASSWORD_HASH, Base64.encodeToString(hashBytes, Base64.NO_WRAP))
            .apply()
        saveConfig(_config.value.copy(hasExitPassword = true))
        return true
    }

    @Synchronized
    fun clearExitPassword(): Boolean {
        if (!canEditRules()) return false
        prefs.edit()
            .remove(KEY_EXIT_PASSWORD_SALT)
            .remove(KEY_EXIT_PASSWORD_HASH)
            .apply()
        saveConfig(_config.value.copy(hasExitPassword = false))
        return true
    }

    fun verifyExitPassword(password: String): Boolean {
        val saltB64 = prefs.getString(KEY_EXIT_PASSWORD_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_EXIT_PASSWORD_HASH, null) ?: return false
        val salt = runCatching { Base64.decode(saltB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val expectedHash = runCatching { Base64.decode(hashB64, Base64.NO_WRAP) }.getOrNull() ?: return false
        val candidateHash = derivePasswordHash(password, salt)
        return MessageDigest.isEqual(expectedHash, candidateHash)
    }

    @Synchronized
    fun unlockWithPassword(password: String, exitCompletely: Boolean): Boolean {
        if (!verifyExitPassword(password)) return false
        val now = System.currentTimeMillis()
        val current = _config.value
        val updated = if (exitCompletely) {
            current.copy(
                isMasterEnabled = false,
                unlockCooldownEndMillis = 0L,
                temporaryUnlockUntilMillis = 0L,
                quickLockUntilMillis = 0L
            )
        } else {
            current.copy(
                unlockCooldownEndMillis = 0L,
                temporaryUnlockUntilMillis = now + TEMP_UNLOCK_DURATION_MS
            )
        }
        saveConfig(updated)
        evaluateAndSyncState(appContext)
        return true
    }

    private fun derivePasswordHash(password: String, salt: ByteArray): ByteArray {
        return runCatching {
            val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }.getOrElse {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt)
            md.digest(password.toByteArray(Charsets.UTF_8))
        }
    }

    @Synchronized
    fun startQuickLockWindow(durationMinutes: Int): LockEvaluationState {
        val now = System.currentTimeMillis()
        val safeMinutes = durationMinutes.coerceIn(5, 8 * 60)
        val endMillis = now + safeMinutes * 60_000L
        val current = _config.value
        saveConfig(
            current.copy(
                isMasterEnabled = true,
                unlockCooldownEndMillis = 0L,
                temporaryUnlockUntilMillis = 0L,
                quickLockUntilMillis = maxOf(current.quickLockUntilMillis, endMillis)
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
                packageName == "com.miui.securitycenter" ||
                packageName.contains("packageinstaller")

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
        val scheduledWindow = findActiveWindow(cfg.windows, nowMillis)
        val hasQuickLock = cfg.quickLockUntilMillis > nowMillis
        val activeWindow = when {
            hasQuickLock && scheduledWindow == null -> buildQuickLockWindow(cfg.quickLockUntilMillis, nowMillis)
            hasQuickLock && scheduledWindow != null -> {
                val scheduledEndMs = computeWindowEndMillis(scheduledWindow, nowMillis)
                if (cfg.quickLockUntilMillis > scheduledEndMs) {
                    buildQuickLockWindow(cfg.quickLockUntilMillis, nowMillis)
                } else {
                    scheduledWindow
                }
            }
            else -> scheduledWindow
        }
        if (activeWindow == null) {
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

    private fun buildQuickLockWindow(endMillis: Long, nowMillis: Long): FocusLockWindow {
        val zone = ZoneId.systemDefault()
        val nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val endLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone)
        return FocusLockWindow(
            id = QUICK_LOCK_WINDOW_ID,
            name = "Quick Focus Lock",
            startMinuteOfDay = nowLdt.hour * 60 + nowLdt.minute,
            endMinuteOfDay = endLdt.hour * 60 + endLdt.minute,
            activeDays = setOf(nowLdt.dayOfWeek.value),
            isEnabled = true
        )
    }

    private fun computeWindowEndMillis(window: FocusLockWindow, nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val todayDate = nowLdt.toLocalDate()
        val endDate = if (window.endMinuteOfDay <= window.startMinuteOfDay &&
            (nowLdt.hour * 60 + nowLdt.minute) >= window.startMinuteOfDay
        ) {
            todayDate.plusDays(1)
        } else {
            todayDate
        }
        return endDate.atTime(window.endMinuteOfDay / 60, window.endMinuteOfDay % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
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
        if (cfg.quickLockUntilMillis > nowMillis) {
            val quickEnd = cfg.quickLockUntilMillis
            if (earliestMillis == null || quickEnd < earliestMillis) {
                earliestMillis = quickEnd
            }
        }
        return earliestMillis
    }

    fun describeNextWindowStart(nowMillis: Long = System.currentTimeMillis()): String? {
        val enabledWindows = _config.value.windows.filter { it.isEnabled && it.activeDays.isNotEmpty() }
        if (enabledWindows.isEmpty()) return null
        val zone = ZoneId.systemDefault()
        val nowLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var best: Pair<Long, FocusLockWindow>? = null
        for (dayOffset in 0L..7L) {
            val date = nowLdt.toLocalDate().plusDays(dayOffset)
            val dow = date.dayOfWeek.value
            for (window in enabledWindows) {
                if (dow in window.activeDays) {
                    val startLdt = date.atTime(window.startMinuteOfDay / 60, window.startMinuteOfDay % 60)
                    val startMs = startLdt.atZone(zone).toInstant().toEpochMilli()
                    if (startMs > nowMillis && (best == null || startMs < best.first)) {
                        best = startMs to window
                    }
                }
            }
        }
        val (bestMs, bestWindow) = best ?: return null
        val targetLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(bestMs), zone)
        val dayText = when (targetLdt.toLocalDate()) {
            nowLdt.toLocalDate() -> "Today"
            nowLdt.toLocalDate().plusDays(1) -> "Tomorrow"
            else -> targetLdt.dayOfWeek.name.lowercase(Locale.getDefault())
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
                .take(3)
        }
        val timeText = formatMinuteOfDay(bestWindow.startMinuteOfDay)
        return "${bestWindow.name}: $dayText at $timeText"
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
            val pkg = info.activityInfo?.applicationInfo?.packageName
                ?: info.activityInfo?.packageName
                ?: continue
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
        private const val KEY_BLOCKED_EVENTS_JSON = "blocked_app_events_json"
        private const val KEY_EXIT_PASSWORD_SALT = "exit_password_salt"
        private const val KEY_EXIT_PASSWORD_HASH = "exit_password_hash"
        private const val QUICK_LOCK_WINDOW_ID = "quick_lock_now"
        private const val TEMP_UNLOCK_DURATION_MS = 5 * 60_000L
        private const val PBKDF2_ITERATIONS = 10_000
        const val MIN_WAIT_MINUTES = 1
        const val MAX_WAIT_MINUTES = 240
        const val MIN_PASSWORD_LENGTH = 4

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

        fun formatDurationMinutes(minutes: Int): String {
            val safe = minutes.coerceAtLeast(1)
            val hrs = safe / 60
            val mins = safe % 60
            return when {
                hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
                hrs > 0 -> "${hrs}h"
                else -> "$safe min"
            }
        }

        fun formatCountdownSeconds(totalSeconds: Int): String {
            val safe = totalSeconds.coerceAtLeast(0)
            val hrs = safe / 3600
            val mins = (safe % 3600) / 60
            val secs = safe % 60
            return if (hrs > 0) {
                "%d:%02d:%02d".format(Locale.getDefault(), hrs, mins, secs)
            } else {
                "%02d:%02d".format(Locale.getDefault(), mins, secs)
            }
        }
    }
}
