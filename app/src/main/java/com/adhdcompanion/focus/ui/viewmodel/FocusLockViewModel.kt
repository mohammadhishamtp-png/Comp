package com.adhdcompanion.focus.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.FocusLockConfig
import com.adhdcompanion.focus.model.FocusLockWindow
import com.adhdcompanion.focus.model.InstalledAppInfo
import com.adhdcompanion.focus.model.LockEvaluationState
import com.adhdcompanion.focus.model.LockMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class FocusLockUiState(
    val config: FocusLockConfig = FocusLockConfig(),
    val evaluation: LockEvaluationState = LockEvaluationState(),
    val isAccessibilityEnabled: Boolean = false,
    val isAccessibilitySettingOn: Boolean = false,
    val isBatteryUnrestricted: Boolean = true,
    val hasOverlayPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val eventsReceivedCount: Long = 0L,
    val lastForegroundPackage: String? = null,
    val serviceError: String? = null,
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = true,
    val appSearchQuery: String = "",
    val nextScheduledWindowLabel: String? = null,
    val feedbackMessage: String? = null
)

class FocusLockViewModel(
    private val appContext: Application,
    private val container: AppContainer
) : ViewModel() {

    private val lockManager = container.focusLockManager

    private val _uiState = MutableStateFlow(FocusLockUiState())
    val uiState: StateFlow<FocusLockUiState> = _uiState.asStateFlow()

    private val secondTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    init {
        refreshAccessibilityStatus()
        loadInstalledApps()

        viewModelScope.launch {
            combine(
                lockManager.config,
                secondTicker
            ) { config, now ->
                config to lockManager.evaluateNow(now)
            }.collect { (config, eval) ->
                val nextLabel = if (!eval.isInScheduledWindow) {
                    lockManager.describeNextWindowStart()
                } else null
                _uiState.value = _uiState.value.copy(
                    config = config,
                    evaluation = eval,
                    isAccessibilityEnabled = FocusBlockerAccessibilityService.isServiceEnabled(appContext),
                    isAccessibilitySettingOn = FocusBlockerAccessibilityService.isAccessibilitySettingOn(appContext),
                    hasOverlayPermission = FocusBlockerAccessibilityService.hasOverlayPermission(appContext),
                    hasUsageStatsPermission = FocusBlockerAccessibilityService.hasUsageStatsPermission(appContext),
                    eventsReceivedCount = FocusBlockerAccessibilityService.eventsReceivedCount,
                    lastForegroundPackage = FocusBlockerAccessibilityService.lastForegroundPackage,
                    serviceError = FocusBlockerAccessibilityService.lastServiceError,
                    nextScheduledWindowLabel = nextLabel
                )
            }
        }
    }

    fun refreshAccessibilityStatus() {
        val liveEnabled = FocusBlockerAccessibilityService.isServiceEnabled(appContext)
        val settingOn = FocusBlockerAccessibilityService.isAccessibilitySettingOn(appContext)
        val batteryUnrestricted = FocusBlockerAccessibilityService.isIgnoringBatteryOptimizations(appContext)
        val overlayGranted = FocusBlockerAccessibilityService.hasOverlayPermission(appContext)
        val usageStatsGranted = FocusBlockerAccessibilityService.hasUsageStatsPermission(appContext)
        _uiState.value = _uiState.value.copy(
            isAccessibilityEnabled = liveEnabled,
            isAccessibilitySettingOn = settingOn,
            isBatteryUnrestricted = batteryUnrestricted,
            hasOverlayPermission = overlayGranted,
            hasUsageStatsPermission = usageStatsGranted,
            eventsReceivedCount = FocusBlockerAccessibilityService.eventsReceivedCount,
            lastForegroundPackage = FocusBlockerAccessibilityService.lastForegroundPackage,
            serviceError = FocusBlockerAccessibilityService.lastServiceError
        )
        lockManager.evaluateAndSyncState(appContext)
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingApps = true)
            val apps = withContext(Dispatchers.IO) {
                lockManager.getInstalledLaunchableApps()
            }
            _uiState.value = _uiState.value.copy(
                installedApps = apps,
                isLoadingApps = false
            )
        }
    }

    fun updateAppSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(appSearchQuery = query)
    }

    fun setMasterEnabled(enabled: Boolean) {
        val updated = lockManager.updateConfigIfAllowed { it.copy(isMasterEnabled = enabled) }
        if (!updated) notifyLocked()
    }

    fun setLockMode(mode: LockMode) {
        val updated = lockManager.updateConfigIfAllowed { it.copy(mode = mode) }
        if (!updated) notifyLocked()
    }

    fun setCooldownMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(
            FocusLockManager.MIN_WAIT_MINUTES,
            FocusLockManager.MAX_WAIT_MINUTES
        )
        val updated = lockManager.updateConfigIfAllowed {
            it.copy(cooldownMinutes = safeMinutes)
        }
        if (!updated) notifyLocked()
    }

    fun togglePackageSelection(packageName: String) {
        val updated = lockManager.updateConfigIfAllowed { cfg ->
            val next = cfg.selectedPackages.toMutableSet()
            if (packageName in next) next.remove(packageName) else next.add(packageName)
            cfg.copy(selectedPackages = next)
        }
        if (!updated) notifyLocked()
    }

    fun saveWindow(
        existingId: String?,
        name: String,
        startMinuteOfDay: Int,
        endMinuteOfDay: Int,
        activeDays: Set<Int>
    ) {
        if (activeDays.isEmpty()) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Pick at least one active day.")
            return
        }
        val updated = lockManager.updateConfigIfAllowed { cfg ->
            val window = FocusLockWindow(
                id = existingId ?: UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Focus Session" },
                startMinuteOfDay = startMinuteOfDay.coerceIn(0, 23 * 60 + 59),
                endMinuteOfDay = endMinuteOfDay.coerceIn(0, 23 * 60 + 59),
                activeDays = activeDays,
                isEnabled = true
            )
            val nextWindows = if (existingId == null) {
                cfg.windows + window
            } else {
                cfg.windows.map { if (it.id == existingId) window else it }
            }
            cfg.copy(windows = nextWindows, isMasterEnabled = true)
        }
        if (!updated) notifyLocked()
    }

    fun toggleWindowEnabled(windowId: String, enabled: Boolean) {
        val updated = lockManager.updateConfigIfAllowed { cfg ->
            cfg.copy(windows = cfg.windows.map { if (it.id == windowId) it.copy(isEnabled = enabled) else it })
        }
        if (!updated) notifyLocked()
    }

    fun deleteWindow(windowId: String) {
        val updated = lockManager.updateConfigIfAllowed { cfg ->
            val remaining = cfg.windows.filterNot { it.id == windowId }
            cfg.copy(windows = remaining)
        }
        if (!updated) notifyLocked()
    }

    fun startUnlockCooldown() {
        lockManager.startUnlockCooldown()
        _uiState.value = _uiState.value.copy(
            feedbackMessage = "Cooldown started. Blocking remains active until the countdown finishes."
        )
    }

    fun cancelUnlockCooldown() {
        lockManager.cancelUnlockCooldown()
        _uiState.value = _uiState.value.copy(feedbackMessage = "Unlock request canceled. Stay steady.")
    }

    fun relockNow() {
        lockManager.relockImmediately()
        _uiState.value = _uiState.value.copy(feedbackMessage = "Focus lock re-armed.")
    }

    fun removeLockNow() {
        val removed = lockManager.removeLockWhenUnlocked()
        if (removed) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Focus lock removed.")
        } else {
            notifyLocked()
        }
    }

    fun setExitPassword(password: String, confirmPassword: String): Boolean {
        if (password.length < FocusLockManager.MIN_PASSWORD_LENGTH) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Password must be at least ${FocusLockManager.MIN_PASSWORD_LENGTH} characters."
            )
            return false
        }
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Passwords do not match.")
            return false
        }
        val saved = lockManager.setExitPassword(password)
        if (saved) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Exit password saved. You can now exit any active lock with this password."
            )
        } else {
            notifyLocked()
        }
        return saved
    }

    fun removeExitPassword() {
        val cleared = lockManager.clearExitPassword()
        if (cleared) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Exit password removed.")
        } else {
            notifyLocked()
        }
    }

    fun unlockWithPassword(password: String, exitCompletely: Boolean): Boolean {
        val ok = lockManager.unlockWithPassword(password, exitCompletely)
        if (ok) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = if (exitCompletely) {
                    "Lock removed using your exit password."
                } else {
                    "Unlocked for 5 minutes using your exit password."
                }
            )
        }
        return ok
    }

    fun startQuickLock(durationMinutes: Int) {
        val cfg = _uiState.value.config
        if (cfg.mode == LockMode.BLOCK_LIST && cfg.selectedPackages.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Step 1: Scroll down and check at least 1 distracting app first, then tap Quick Lock."
            )
            return
        }
        lockManager.startQuickLockWindow(durationMinutes)
        val label = if (durationMinutes >= 60) "${durationMinutes / 60}h" else "${durationMinutes}m"
        _uiState.value = _uiState.value.copy(
            feedbackMessage = "Quick Focus Lock is LIVE for $label. Selected apps are blocked now."
        )
    }

    fun testShieldOverlay() {
        val shown = FocusBlockerAccessibilityService.triggerTestShield()
        _uiState.value = _uiState.value.copy(
            feedbackMessage = if (shown) {
                "Test shield overlay sent to Accessibility Service."
            } else {
                "Accessibility Service is not actively bound yet. Turn it OFF -> ON in Accessibility Settings."
            }
        )
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }

    private fun notifyLocked() {
        val hasPwd = _uiState.value.config.hasExitPassword
        _uiState.value = _uiState.value.copy(
            feedbackMessage = if (hasPwd) {
                "Lock window active. Wait the countdown or use your Exit Password to change rules."
            } else {
                "Lock window active. Start the waiting timer to edit or remove the lock."
            }
        )
    }
}
