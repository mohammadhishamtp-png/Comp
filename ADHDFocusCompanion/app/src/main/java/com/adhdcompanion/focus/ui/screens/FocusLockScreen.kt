package com.adhdcompanion.focus.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.FocusLockWindow
import com.adhdcompanion.focus.model.LockMode
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.viewmodel.FocusLockViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusLockScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel = viewModel<FocusLockViewModel>(factory = ViewModelFactories.focusLock(app, container))
    val state by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val eval = state.evaluation
    val noBlockAppsYet = state.config.mode == LockMode.BLOCK_LIST && state.config.selectedPackages.isEmpty()
    val isRuleEditingAllowed = !state.config.isMasterEnabled || noBlockAppsYet || !eval.isInScheduledWindow || eval.isTemporaryUnlocked

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var appPickerOffsetY by remember { mutableIntStateOf(0) }

    var editingWindow by remember { mutableStateOf<FocusLockWindow?>(null) }
    var showWindowDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("App Blocker & Lock") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            state.feedbackMessage?.let { msg ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearFeedback) { Text("OK") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            val isMalfunctioning = state.isAccessibilitySettingOn && !state.isAccessibilityEnabled

            // 1. Accessibility Service status & setup / recovery guide
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isAccessibilityEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.isAccessibilityEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (state.isAccessibilityEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = when {
                                state.isAccessibilityEnabled -> "Accessibility Blocker Active"
                                isMalfunctioning -> "Accessibility Service Disconnected"
                                else -> "Accessibility Permission Needed"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isAccessibilityEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when {
                            state.isAccessibilityEnabled -> {
                                val batteryNote = if (!state.isBatteryUnrestricted) {
                                    "\n\nTip: Battery optimization is still restricting this app. Open App Info -> Battery -> No restrictions."
                                } else ""
                                val telemetry = buildString {
                                    append("Connected & monitoring foreground app switches.")
                                    append(" (Events seen: ${state.eventsReceivedCount}")
                                    state.lastForegroundPackage?.let { append(" • Last: $it") }
                                    append(")")
                                }
                                "$telemetry$batteryNote\n\nOn Xiaomi/HyperOS: in App Info -> Other permissions, enable 'Display pop-up windows while running in the background' for reliable full-screen redirects."
                            }
                            isMalfunctioning -> {
                                "Android shows 'This service is malfunctioning' or stopped binding the blocker after an app install or background kill.\n\nFix steps:\n1. Tap 'Accessibility' -> turn Focus Companion App Blocker OFF, wait 2s, and turn it back ON.\n2. Tap 'App Info' -> Battery -> 'No restrictions', turn ON Autostart, and under Other permissions enable 'Display pop-up windows while running in the background'."
                            }
                            else -> {
                                "Setup steps:\n1. If sideloaded on Android 13+: tap 'App Info' -> ⋮ menu -> Allow restricted settings.\n2. In 'App Info' -> set Battery to 'No restrictions' & enable 'Display pop-up windows while running in the background' (HyperOS/MIUI).\n3. Tap 'Accessibility' -> Downloaded apps -> turn ON Focus Companion App Blocker."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isAccessibilityEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    state.serviceError?.let { err ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Last service note: $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { FocusBlockerAccessibilityService.openAccessibilitySettings(context) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_accessibility_settings_button")
                        ) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Accessibility")
                        }
                        OutlinedButton(
                            onClick = { FocusBlockerAccessibilityService.openAppInfoSettings(context) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (state.isAccessibilityEnabled)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_app_info_settings_button")
                        ) {
                            Text("App Info")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { FocusBlockerAccessibilityService.openOverlayPermissionSettings(context) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (state.isAccessibilityEnabled)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_overlay_permission_button")
                        ) {
                            Text(if (state.hasOverlayPermission) "Overlay: On" else "Allow Pop-up / Overlay")
                        }
                        OutlinedButton(
                            onClick = { FocusBlockerAccessibilityService.openUsageStatsSettings(context) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (state.isAccessibilityEnabled)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_usage_access_button")
                        ) {
                            Text(if (state.hasUsageStatsPermission) "Usage Access: On" else "Usage Fallback")
                        }
                    }
                    if (state.isAccessibilityEnabled) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.testShieldOverlay() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_shield_overlay_button")
                        ) {
                            Text("Test Blocker Overlay on This Screen")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 1b. Live Blocker Diagnostics & 1-Tap Quick Lock
            val appCount = state.config.selectedPackages.size
            val noAppsSelected = state.config.mode == LockMode.BLOCK_LIST && appCount == 0
            val statusHeadline = when {
                isMalfunctioning -> "Status: Restart Accessibility Toggle"
                !state.isAccessibilityEnabled -> "Status: Needs Accessibility Permission"
                noAppsSelected -> "Status: Select Apps Below to Block"
                eval.isBlockingActive -> "Status: BLOCKING LIVE NOW"
                !state.config.isMasterEnabled -> "Status: Paused (Master Switch Off)"
                else -> "Status: Armed & Waiting"
            }
            val statusDetail = when {
                isMalfunctioning ->
                    "The system toggle is ON, but Android stopped binding the service. Toggle it OFF -> ON in Accessibility Settings above."
                !state.isAccessibilityEnabled ->
                    "Turn ON Focus Companion App Blocker above so Android notifies us when a distracting app opens."
                noAppsSelected ->
                    "0 distracting apps are checked in your Block List below. Check at least 1 app, then tap Quick Lock or enable a schedule covering right now."
                eval.isBlockingActive -> {
                    val scopeText = if (state.config.mode == LockMode.BLOCK_LIST) {
                        "$appCount distracting app${if (appCount == 1) "" else "s"} guarded"
                    } else {
                        "Only $appCount allowed app${if (appCount == 1) "" else "s"} permitted"
                    }
                    val endText = eval.activeWindow?.let {
                        FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay)
                    } ?: "scheduled end"
                    "$scopeText until $endText."
                }
                !state.config.isMasterEnabled ->
                    "Tap a Quick Lock duration below to block selected apps immediately, or turn ON the scheduled blocker switch."
                else ->
                    "Current time is outside scheduled focus hours (not blocking right now). " +
                        (state.nextScheduledWindowLabel?.let { "Next window: $it. " } ?: "") +
                        "Tap a Quick Lock button below to start blocking RIGHT NOW."
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (eval.isBlockingActive && state.isAccessibilityEnabled && !noAppsSelected)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("blocker_live_status_card")
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = statusHeadline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Checklist: Accessibility ${if (state.isAccessibilityEnabled) "OK" else "Missing"} • Apps ${if (noAppsSelected) "0 (Select below)" else "$appCount chosen"} • Lock ${if (eval.isBlockingActive) "ACTIVE NOW" else "Inactive right now"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (noAppsSelected) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    scrollState.animateScrollTo(appPickerOffsetY)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Jump to App List to Pick Apps")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Quick Lock Right Now",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.startQuickLock(30) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_lock_30m_button")
                        ) {
                            Text("30 min")
                        }
                        OutlinedButton(
                            onClick = { viewModel.startQuickLock(60) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_lock_1h_button")
                        ) {
                            Text("1 hour")
                        }
                        OutlinedButton(
                            onClick = { viewModel.startQuickLock(120) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_lock_2h_button")
                        ) {
                            Text("2 hours")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Anti-Impulse Lock Guard / Cooldown Banner when in an active window
            if (state.config.isMasterEnabled && eval.isInScheduledWindow && !noAppsSelected) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (eval.isTemporaryUnlocked)
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (eval.isTemporaryUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (eval.isTemporaryUnlocked) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    eval.isTemporaryUnlocked -> "Temporary Edit Window Open"
                                    eval.isCooldownRunning -> "Anti-Impulse Cooldown Running"
                                    else -> "Lock Window Active: ${eval.activeWindow?.name ?: "Focus"}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (eval.isTemporaryUnlocked) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        when {
                            eval.isTemporaryUnlocked -> {
                                val mins = eval.tempUnlockRemainingSeconds / 60
                                val secs = eval.tempUnlockRemainingSeconds % 60
                                Text(
                                    "You can adjust or disable lock rules for %d:%02d before blocking re-arms automatically."
                                        .format(Locale.getDefault(), mins, secs),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = viewModel::relockNow,
                                    modifier = Modifier.testTag("relock_now_button")
                                ) {
                                    Text("Re-lock immediately")
                                }
                            }

                            eval.isCooldownRunning -> {
                                val mins = eval.cooldownRemainingSeconds / 60
                                val secs = eval.cooldownRemainingSeconds % 60
                                Text(
                                    "Blocking stays fully active during this countdown. Rules unlock for editing in:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "%02d:%02d".format(Locale.getDefault(), mins, secs),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = viewModel::cancelUnlockCooldown,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("cancel_cooldown_button")
                                ) {
                                    Text("Cancel unlock request")
                                }
                            }

                            else -> {
                                val endText = eval.activeWindow?.let {
                                    FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay)
                                } ?: ""
                                Text(
                                    "Active until $endText. Editing or disabling lock rules mid-session requires a strict ${state.config.cooldownMinutes}-minute cooldown (blocking stays active during the countdown).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = viewModel::startUnlockCooldown,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimary,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("start_cooldown_button")
                                ) {
                                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Start ${state.config.cooldownMinutes}-min unlock cooldown")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 3. Master Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable scheduled app blocker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Automatically locks selected apps during your focus windows and re-evaluates after reboot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.config.isMasterEnabled,
                    onCheckedChange = viewModel::setMasterEnabled,
                    enabled = isRuleEditingAllowed,
                    modifier = Modifier.testTag("master_lock_switch")
                )
            }

            Spacer(Modifier.height(20.dp))

            // 4. Scheduled Focus Windows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Scheduled lock windows")
                OutlinedButton(
                    onClick = {
                        editingWindow = null
                        showWindowDialog = true
                    },
                    enabled = isRuleEditingAllowed,
                    modifier = Modifier.testTag("add_lock_window_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add window")
                }
            }
            Spacer(Modifier.height(8.dp))

            state.config.windows.forEach { window ->
                FocusWindowCard(
                    window = window,
                    editingAllowed = isRuleEditingAllowed,
                    onToggle = { enabled -> viewModel.toggleWindowEnabled(window.id, enabled) },
                    onEdit = {
                        editingWindow = window
                        showWindowDialog = true
                    },
                    onDelete = { viewModel.deleteWindow(window.id) }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(16.dp))

            // 5. Cooldown duration selector (10–15 min)
            SectionLabel("Anti-impulse edit cooldown")
            Text(
                "Cooldown required before lock rules can be edited or disabled mid-session: ${state.config.cooldownMinutes} minutes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = state.config.cooldownMinutes.toFloat(),
                onValueChange = { viewModel.setCooldownMinutes(it.toInt()) },
                valueRange = 10f..15f,
                steps = 4,
                enabled = isRuleEditingAllowed,
                modifier = Modifier.testTag("cooldown_slider")
            )

            Spacer(Modifier.height(16.dp))

            // 6. Mode: Block chosen apps vs Allow only chosen apps
            SectionLabel("Blocking mode")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { if (isRuleEditingAllowed) viewModel.setLockMode(LockMode.BLOCK_LIST) },
                    enabled = isRuleEditingAllowed,
                    label = { Text("Block chosen apps") },
                    colors = if (state.config.mode == LockMode.BLOCK_LIST) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else AssistChipDefaults.assistChipColors()
                )
                AssistChip(
                    onClick = { if (isRuleEditingAllowed) viewModel.setLockMode(LockMode.ALLOW_LIST) },
                    enabled = isRuleEditingAllowed,
                    label = { Text("Allow only chosen apps") },
                    colors = if (state.config.mode == LockMode.ALLOW_LIST) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else AssistChipDefaults.assistChipColors()
                )
            }

            Spacer(Modifier.height(18.dp))

            // 7. Installed apps picker
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        appPickerOffsetY = coords.positionInParent().y.toInt()
                    }
            ) {
                SectionLabel(
                    if (state.config.mode == LockMode.BLOCK_LIST)
                        "Distracting apps to block (${state.config.selectedPackages.size} selected)"
                    else
                        "Allowed apps to permit (${state.config.selectedPackages.size} selected)"
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.appSearchQuery,
                    onValueChange = viewModel::updateAppSearchQuery,
                    label = { Text("Search installed apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                if (state.isLoadingApps) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading installed apps…", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val query = state.appSearchQuery.trim().lowercase(Locale.getDefault())
                    val filteredApps = state.installedApps.filter {
                        query.isBlank() ||
                            it.label.lowercase(Locale.getDefault()).contains(query) ||
                            it.packageName.lowercase(Locale.getDefault()).contains(query)
                    }.sortedByDescending { it.packageName in state.config.selectedPackages }

                    if (filteredApps.isEmpty()) {
                        Text(
                            "No matching launchable apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filteredApps.forEach { appInfo ->
                            val checked = appInfo.packageName in state.config.selectedPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isRuleEditingAllowed) {
                                        viewModel.togglePackageSelection(appInfo.packageName)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.togglePackageSelection(appInfo.packageName) },
                                    enabled = isRuleEditingAllowed
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(appInfo.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        appInfo.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Android security note: Standard Android cannot permanently block Safe Mode, ADB, or factory resets without enterprise Device Owner controls. Within normal use, active lock windows guard app switches, App Info, uninstall, and lock settings with a mandatory cooldown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showWindowDialog) {
        FocusWindowEditorDialog(
            initial = editingWindow,
            onDismiss = { showWindowDialog = false },
            onSave = { name, startMin, endMin, days ->
                viewModel.saveWindow(
                    existingId = editingWindow?.id,
                    name = name,
                    startMinuteOfDay = startMin,
                    endMinuteOfDay = endMin,
                    activeDays = days
                )
                showWindowDialog = false
            }
        )
    }
}

@Composable
private fun FocusWindowCard(
    window: FocusLockWindow,
    editingAllowed: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dayNames = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    val activeDayText = dayNames.filter { it.first in window.activeDays }.joinToString(", ") { it.second }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(window.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${FocusLockManager.formatMinuteOfDay(window.startMinuteOfDay)} – ${FocusLockManager.formatMinuteOfDay(window.endMinuteOfDay)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    activeDayText.ifBlank { "No days selected" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit, enabled = editingAllowed) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit window")
            }
            IconButton(onClick = onDelete, enabled = editingAllowed) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete window")
            }
            Switch(
                checked = window.isEnabled,
                onCheckedChange = onToggle,
                enabled = editingAllowed
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FocusWindowEditorDialog(
    initial: FocusLockWindow?,
    onDismiss: () -> Unit,
    onSave: (name: String, startMinute: Int, endMinute: Int, days: Set<Int>) -> Unit
) {
    val nowLdt = remember { LocalDateTime.now() }
    val defaultStartMin = nowLdt.hour * 60 + nowLdt.minute
    val defaultEndMin = (defaultStartMin + 120) % (24 * 60)

    var name by remember { mutableStateOf(initial?.name ?: "Study Focus Window") }
    val startHour = (initial?.startMinuteOfDay ?: defaultStartMin) / 60
    val startMin = (initial?.startMinuteOfDay ?: defaultStartMin) % 60
    val endHour = (initial?.endMinuteOfDay ?: defaultEndMin) / 60
    val endMin = (initial?.endMinuteOfDay ?: defaultEndMin) % 60
    var selectedDays by remember {
        mutableStateOf(initial?.activeDays ?: (setOf(1, 2, 3, 4, 5) + nowLdt.dayOfWeek.value))
    }

    val startPicker = rememberTimePickerState(initialHour = startHour, initialMinute = startMin, is24Hour = false)
    val endPicker = rememberTimePickerState(initialHour = endHour, initialMinute = endMin, is24Hour = false)

    val dayChips = listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add focus window" else "Edit focus window") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Window label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Start time", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                TimeInput(state = startPicker)

                Spacer(Modifier.height(8.dp))
                Text("End time", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                TimeInput(state = endPicker)

                Spacer(Modifier.height(10.dp))
                Text("Active days", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayChips.forEach { (dow, label) ->
                        val active = dow in selectedDays
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedDays = if (active) selectedDays - dow else selectedDays + dow
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sMin = startPicker.hour * 60 + startPicker.minute
                    val eMin = endPicker.hour * 60 + endPicker.minute
                    onSave(name, sMin, eMin, selectedDays)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
