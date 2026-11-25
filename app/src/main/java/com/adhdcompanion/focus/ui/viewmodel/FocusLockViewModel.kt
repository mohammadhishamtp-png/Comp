package com.adhdcompanion.focus.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
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
    val installedApps: List<InstalledAppInfo> = emptyList(),
    val isLoadingApps: Boolean = true,
    val appSearchQuery: String = "",
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
                _uiState.value = _uiState.value.copy(
                    config = config,
                    evaluation = eval
                )
            }
        }
    }

    fun refreshAccessibilityStatus() {
        val enabled = FocusBlockerAccessibilityService.isServiceEnabled(appContext)
        _uiState.value = _uiState.value.copy(isAccessibilityEnabled = enabled)
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
        val updated = lockManager.updateConfigIfAllowed {
            it.copy(cooldownMinutes = minutes.coerceIn(10, 15))
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
            cfg.copy(windows = nextWindows)
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

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }

    private fun notifyLocked() {
        _uiState.value = _uiState.value.copy(
            feedbackMessage = "Lock window active. Start the cooldown timer to edit rules."
        )
    }
}
