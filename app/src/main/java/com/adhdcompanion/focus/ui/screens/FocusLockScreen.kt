package com.adhdcompanion.focus.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayDisabled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.FocusLockWindow
import com.adhdcompanion.focus.model.LockMode
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.components.SegmentedModeBar
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusAmber
import com.adhdcompanion.focus.ui.theme.FocusCoral
import com.adhdcompanion.focus.ui.theme.FocusCoralContainer
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.FocusEmeraldDim
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import com.adhdcompanion.focus.ui.viewmodel.FocusLockViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusLockScreen(
    container: AppContainer,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        BackHandler { onBack() }
    }

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

    var activeSectionTab by remember { mutableIntStateOf(0) } // 0 = Apps, 1 = Schedule, 2 = Guardrails
    var permissionsExpanded by remember(state.isAccessibilityEnabled) {
        mutableStateOf(!state.isAccessibilityEnabled)
    }
    var editingWindow by remember { mutableStateOf<FocusLockWindow?>(null) }
    var showWindowDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showPasswordExitDialog by remember { mutableStateOf(false) }
    var customWaitInput by remember(state.config.cooldownMinutes) {
        mutableStateOf(state.config.cooldownMinutes.toString())
    }
    var customWaitError by remember { mutableStateOf<String?>(null) }
    var isEditingStudyToolsPicker by remember { mutableStateOf(false) }

    val isMalfunctioning = state.isAccessibilitySettingOn && !state.isAccessibilityEnabled
    val appCount = state.config.selectedPackages.size
    val shieldColor = when {
        !state.isAccessibilityEnabled || isMalfunctioning -> FocusCoral
        eval.isBlockingActive && !noBlockAppsYet -> FocusEmerald
        else -> FocusAmber
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnAmoled)
                    }
                }
                Column {
                    Text(
                        text = "Focus Shield",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnAmoled
                    )
                    Text(
                        text = "App blocker · Shorts & Reels guard · Cooldown lock",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoledMuted
                    )
                }
            }
        }

        state.feedbackMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            AmoledCard(containerColor = AmoledSurfaceHigh, borderColor = FocusEmerald.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = OnAmoled, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearFeedback) { Text("OK", color = FocusEmerald) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 1. Sleek Regain Shield Hero Card
        AmoledCard(
            containerColor = AmoledSurface,
            borderColor = shieldColor.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("blocker_live_status_card")
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(shieldColor.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (state.isAccessibilityEnabled) Icons.Filled.Shield else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = shieldColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = when {
                                    isMalfunctioning -> "Restart Accessibility Service"
                                    !state.isAccessibilityEnabled -> "Setup Accessibility Shield"
                                    noBlockAppsYet -> "Pick Apps to Guard"
                                    eval.isBlockingActive -> "Shield Active Now"
                                    !state.config.isMasterEnabled -> "Shield Paused"
                                    else -> "Shield Armed"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnAmoled
                            )
                            Text(
                                text = when {
                                    isMalfunctioning -> "Toggle Focus Companion OFF -> ON in Accessibility"
                                    !state.isAccessibilityEnabled -> "Required to intercept distracting apps"
                                    noBlockAppsYet -> "Select 1+ distracting apps in the Apps tab"
                                    eval.isBlockingActive -> {
                                        val endText = eval.activeWindow?.let {
                                            FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay)
                                        } ?: "session end"
                                        "$appCount apps guarded until $endText"
                                    }
                                    else -> state.nextScheduledWindowLabel?.let { "Next window: $it" }
                                        ?: "$appCount apps ready · Tap Quick Lock below"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoledMuted
                            )
                        }
                    }
                }

                if (noBlockAppsYet) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            activeSectionTab = 0
                            coroutineScope.launch { scrollState.animateScrollTo(appPickerOffsetY) }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pick Apps to Block", color = FocusEmerald)
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionLabel("Quick Lock Right Now")
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        30 to ("30m" to "quick_lock_30m_button"),
                        60 to ("1h" to "quick_lock_1h_button"),
                        120 to ("2h" to "quick_lock_2h_button")
                    ).forEach { (mins, meta) ->
                        val (label, tag) = meta
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AmoledSurfaceHigh,
                            border = BorderStroke(0.75.dp, AmoledOutline),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { viewModel.startQuickLock(mins) }
                                .testTag(tag)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = TabularStatStyle.copy(fontSize = 13.sp),
                                    color = OnAmoled
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
                Spacer(Modifier.height(8.dp))

                // Compact expandable System Permissions & Diagnostics drawer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { permissionsExpanded = !permissionsExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isAccessibilityEnabled) {
                            "System Health · Events: ${state.eventsReceivedCount} · Overlay ${if (state.hasOverlayPermission) "On" else "Off"}"
                        } else {
                            "Required Permissions & HyperOS Setup"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.isAccessibilityEnabled) FocusEmerald else FocusCoral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (permissionsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Toggle permissions",
                        tint = OnAmoledMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = permissionsExpanded) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = when {
                                state.isAccessibilityEnabled ->
                                    "Connected & watching foreground switches.${state.lastForegroundPackage?.let { " Last: $it" } ?: ""}" +
                                        if (!state.isBatteryUnrestricted) " Battery optimization may sleep the service — set App Info -> Battery -> No restrictions." else ""
                                isMalfunctioning ->
                                    "Accessibility toggle is ON, but Android unbound the service. Turn it OFF, wait 2s, then turn ON, and set Battery -> No restrictions."
                                else ->
                                    "1. Sideloaded on Android 13+: App Info -> ⋮ -> Allow restricted settings.\n2. Battery -> No restrictions & HyperOS background pop-up.\n3. Accessibility -> Downloaded apps -> Focus Companion ON."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAmoledMuted
                        )
                        state.serviceError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(err, style = MaterialTheme.typography.labelSmall, color = FocusCoral)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { FocusBlockerAccessibilityService.openAccessibilitySettings(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald, contentColor = AmoledBlack),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_accessibility_settings_button")
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Accessibility", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { FocusBlockerAccessibilityService.openAppInfoSettings(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_app_info_settings_button")
                            ) {
                                Text("App Info", color = OnAmoled)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { FocusBlockerAccessibilityService.openOverlayPermissionSettings(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_overlay_permission_button")
                            ) {
                                Text(if (state.hasOverlayPermission) "Overlay: On" else "Allow Pop-up", color = OnAmoledMuted)
                            }
                            OutlinedButton(
                                onClick = { FocusBlockerAccessibilityService.openUsageStatsSettings(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_usage_access_button")
                            ) {
                                Text(if (state.hasUsageStatsPermission) "Usage: On" else "Usage Access", color = OnAmoledMuted)
                            }
                        }
                        if (state.isAccessibilityEnabled) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.testShieldOverlay() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("test_shield_overlay_button")
                            ) {
                                Text("Test Blocker Redirect Now", color = FocusEmerald)
                            }
                        }
                    }
                }
            }
        }

        // 2. Active Cooldown / Temporary Unlock / Password Exit Banner
        if (state.config.isMasterEnabled && eval.isInScheduledWindow && !noBlockAppsYet) {
            Spacer(Modifier.height(14.dp))
            AmoledCard(
                containerColor = if (eval.isTemporaryUnlocked) AmoledSurfaceElevated else FocusEmeraldDim,
                borderColor = FocusEmerald.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (eval.isTemporaryUnlocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                            contentDescription = null,
                            tint = FocusEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when {
                                eval.isTemporaryUnlocked -> "Temporary Edit Window Open"
                                eval.isCooldownRunning -> "Anti-Impulse Cooldown Running"
                                else -> "Lock Active: ${eval.activeWindow?.name ?: "Focus"}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = OnAmoled
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        eval.isTemporaryUnlocked -> {
                            val mins = eval.tempUnlockRemainingSeconds / 60
                            val secs = eval.tempUnlockRemainingSeconds % 60
                            Text(
                                "Rules unlocked for %d:%02d before blocking re-arms.".format(Locale.getDefault(), mins, secs),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoledMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = viewModel::removeLockNow,
                                    colors = ButtonDefaults.buttonColors(containerColor = FocusCoral, contentColor = AmoledBlack),
                                    modifier = Modifier.weight(1f).testTag("remove_lock_now_button")
                                ) { Text("Remove Lock", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = viewModel::relockNow,
                                    modifier = Modifier.weight(1f).testTag("relock_now_button")
                                ) { Text("Re-lock", color = OnAmoled) }
                            }
                        }
                        eval.isCooldownRunning -> {
                            Text(
                                "Blocking stays active. Rules unlock in:",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoledMuted
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                FocusLockManager.formatCountdownSeconds(eval.cooldownRemainingSeconds),
                                style = TabularStatStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                                color = FocusEmerald
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = viewModel::cancelUnlockCooldown,
                                modifier = Modifier.fillMaxWidth().testTag("cancel_cooldown_button")
                            ) { Text("Cancel Unlock Request", color = OnAmoled) }
                            if (state.config.hasExitPassword) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { showPasswordExitDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald, contentColor = AmoledBlack),
                                    modifier = Modifier.fillMaxWidth().testTag("password_exit_running_button")
                                ) {
                                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Exit Lock with Password", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {
                            val waitLabel = FocusLockManager.formatDurationMinutes(state.config.cooldownMinutes)
                            Text(
                                "Editing or removing rules mid-session requires waiting $waitLabel" +
                                    if (state.config.hasExitPassword) " or entering your Exit Password." else ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoledMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = viewModel::startUnlockCooldown,
                                colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald, contentColor = AmoledBlack),
                                modifier = Modifier.fillMaxWidth().testTag("start_cooldown_button")
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Wait $waitLabel to Unlock", fontWeight = FontWeight.Bold)
                            }
                            if (state.config.hasExitPassword) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showPasswordExitDialog = true },
                                    modifier = Modifier.fillMaxWidth().testTag("password_exit_button")
                                ) {
                                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnAmoled)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Exit Lock with Password", color = OnAmoled)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3. Clean 3-Tab Switcher (Apps | Schedule | Guardrails)
        SegmentedModeBar(
            options = listOf("Apps ($appCount)", "Schedule", "Guardrails"),
            selectedIndex = activeSectionTab,
            onSelect = { activeSectionTab = it },
            testTagPrefix = "shield_tab"
        )

        Spacer(Modifier.height(14.dp))

        when (activeSectionTab) {
            // TAB 0: APPS & SHORTS/REELS
            0 -> {
                // Shorts & Reels Guard
                AmoledCard(
                    containerColor = AmoledSurface,
                    borderColor = if (state.config.blockShortsAndReels) FocusEmerald.copy(alpha = 0.45f) else AmoledOutlineSubtle,
                    modifier = Modifier.fillMaxWidth().testTag("shorts_reels_hook_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Filled.PlayDisabled,
                                contentDescription = null,
                                tint = if (state.config.blockShortsAndReels) FocusEmerald else OnAmoledMuted
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Block Shorts & Reels",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnAmoled
                                )
                                Text(
                                    "Allows long study lectures while backing out of short-form feeds",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnAmoledMuted
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.config.blockShortsAndReels,
                            onCheckedChange = viewModel::setBlockShortsAndReels,
                            enabled = isRuleEditingAllowed,
                            modifier = Modifier.testTag("shorts_reels_switch")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Mode Switch
                SegmentedModeBar(
                    options = listOf("Block Chosen Apps", "Allow Only Chosen"),
                    selectedIndex = if (state.config.mode == LockMode.BLOCK_LIST) 0 else 1,
                    onSelect = { idx ->
                        if (isRuleEditingAllowed) {
                            viewModel.setLockMode(if (idx == 0) LockMode.BLOCK_LIST else LockMode.ALLOW_LIST)
                        }
                    },
                    enabled = isRuleEditingAllowed,
                    testTagPrefix = "lock_mode_seg"
                )

                Spacer(Modifier.height(12.dp))

                // Study Tools Whitelist chips if any
                if (state.config.allowedStudyTools.isNotEmpty()) {
                    AmoledCard(
                        containerColor = AmoledSurface,
                        borderColor = AmoledOutlineSubtle,
                        modifier = Modifier.fillMaxWidth().testTag("allowed_study_tools_card")
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = FocusEmerald, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                SectionLabel("Exempt Study Tools (${state.config.allowedStudyTools.size})")
                            }
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.config.allowedStudyTools.forEach { pkg ->
                                    val label = state.installedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                                    FilterChip(
                                        selected = true,
                                        onClick = { if (isRuleEditingAllowed) viewModel.toggleAllowedStudyTool(pkg) },
                                        enabled = isRuleEditingAllowed,
                                        label = { Text(label) },
                                        trailingIcon = {
                                            Icon(Icons.Filled.Close, contentDescription = "Remove $label", modifier = Modifier.size(14.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Installed Apps Picker
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords -> appPickerOffsetY = coords.positionInParent().y.toInt() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isEditingStudyToolsPicker,
                            onClick = { isEditingStudyToolsPicker = false },
                            label = {
                                Text(
                                    if (state.config.mode == LockMode.BLOCK_LIST)
                                        "Block List (${state.config.selectedPackages.size})"
                                    else
                                        "Allow List (${state.config.selectedPackages.size})"
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FocusEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = FocusEmerald
                            ),
                            modifier = Modifier.weight(1f).testTag("picker_tab_blocked_apps")
                        )
                        FilterChip(
                            selected = isEditingStudyToolsPicker,
                            onClick = { isEditingStudyToolsPicker = true },
                            label = { Text("Study Tools (${state.config.allowedStudyTools.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FocusEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = FocusEmerald
                            ),
                            modifier = Modifier.weight(1f).testTag("picker_tab_study_tools")
                        )
                    }

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
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = FocusEmerald)
                            Spacer(Modifier.width(8.dp))
                            Text("Loading installed apps…", style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                        }
                    } else {
                        val query = state.appSearchQuery.trim().lowercase(Locale.getDefault())
                        val activeSet = if (isEditingStudyToolsPicker) state.config.allowedStudyTools else state.config.selectedPackages
                        val filteredApps = state.installedApps.filter {
                            query.isBlank() ||
                                it.label.lowercase(Locale.getDefault()).contains(query) ||
                                it.packageName.lowercase(Locale.getDefault()).contains(query)
                        }.sortedByDescending { it.packageName in activeSet }

                        if (filteredApps.isEmpty()) {
                            Text("No matching launchable apps found.", style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                        } else {
                            AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                                filteredApps.forEachIndexed { i, appInfo ->
                                    val checked = appInfo.packageName in activeSet
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = isRuleEditingAllowed) {
                                                if (isEditingStudyToolsPicker) viewModel.toggleAllowedStudyTool(appInfo.packageName)
                                                else viewModel.togglePackageSelection(appInfo.packageName)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                if (isEditingStudyToolsPicker) viewModel.toggleAllowedStudyTool(appInfo.packageName)
                                                else viewModel.togglePackageSelection(appInfo.packageName)
                                            },
                                            enabled = isRuleEditingAllowed
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(appInfo.label, style = MaterialTheme.typography.titleSmall, color = OnAmoled, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(appInfo.packageName, style = MaterialTheme.typography.labelSmall, color = OnAmoledSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    if (i < filteredApps.lastIndex) {
                                        HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TAB 1: SCHEDULE & INTERRUPTIONS
            1 -> {
                AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Scheduled App Blocker", style = MaterialTheme.typography.titleSmall, color = OnAmoled)
                            Text(
                                "Auto-locks during windows & persists after reboot",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoledMuted
                            )
                        }
                        Switch(
                            checked = state.config.isMasterEnabled,
                            onCheckedChange = viewModel::setMasterEnabled,
                            enabled = isRuleEditingAllowed,
                            modifier = Modifier.testTag("master_lock_switch")
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Focus Windows")
                    TextButton(
                        onClick = {
                            editingWindow = null
                            showWindowDialog = true
                        },
                        enabled = isRuleEditingAllowed,
                        modifier = Modifier.testTag("add_lock_window_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = FocusEmerald)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Window", color = FocusEmerald)
                    }
                }

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
                    Spacer(Modifier.height(8.dp))
                }

                if (state.config.interruptionEvents.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel("Recent Session Interruptions")
                    Spacer(Modifier.height(6.dp))
                    AmoledCard(
                        containerColor = AmoledSurface,
                        borderColor = AmoledOutlineSubtle,
                        modifier = Modifier.fillMaxWidth().testTag("session_interruptions_card")
                    ) {
                        val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        state.config.interruptionEvents.take(6).forEachIndexed { idx, evt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${evt.subjectName} · ${evt.reason}", style = MaterialTheme.typography.bodySmall, color = OnAmoled, modifier = Modifier.weight(1f))
                                Text(timeFmt.format(Date(evt.timestampMillis)), style = TabularStatStyle.copy(fontSize = 11.5.sp), color = OnAmoledMuted)
                            }
                            if (idx < minOf(5, state.config.interruptionEvents.lastIndex)) {
                                HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
                            }
                        }
                    }
                }
            }

            // TAB 2: GUARDRAILS (CUSTOM WAIT + EXIT PASSWORD)
            else -> {
                AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                    Column(Modifier.padding(16.dp)) {
                        SectionLabel("Anti-Impulse Cooldown")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Wait time before editing or removing an active lock: ${FocusLockManager.formatDurationMinutes(state.config.cooldownMinutes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAmoledMuted
                        )
                        Spacer(Modifier.height(10.dp))
                        val waitPresets = listOf(1, 5, 10, 15, 30, 60, 120)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            waitPresets.forEach { presetMin ->
                                val label = if (presetMin >= 60) "${presetMin / 60}h" else "${presetMin}m"
                                FilterChip(
                                    selected = state.config.cooldownMinutes == presetMin,
                                    onClick = {
                                        customWaitError = null
                                        viewModel.setCooldownMinutes(presetMin)
                                    },
                                    enabled = isRuleEditingAllowed,
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("wait_preset_${presetMin}m_chip")
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = state.config.cooldownMinutes.coerceAtMost(120).toFloat(),
                            onValueChange = {
                                customWaitError = null
                                viewModel.setCooldownMinutes(it.toInt())
                            },
                            valueRange = FocusLockManager.MIN_WAIT_MINUTES.toFloat()..120f,
                            enabled = isRuleEditingAllowed,
                            modifier = Modifier.testTag("cooldown_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customWaitInput,
                                onValueChange = { raw ->
                                    customWaitInput = raw.filter { it.isDigit() }.take(3)
                                    customWaitError = null
                                },
                                label = { Text("Custom (${FocusLockManager.MIN_WAIT_MINUTES}–${FocusLockManager.MAX_WAIT_MINUTES} min)") },
                                singleLine = true,
                                enabled = isRuleEditingAllowed,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = customWaitError != null,
                                modifier = Modifier.weight(1f).testTag("custom_wait_minutes_input")
                            )
                            Button(
                                onClick = {
                                    val parsed = customWaitInput.toIntOrNull()
                                    if (parsed == null || parsed !in FocusLockManager.MIN_WAIT_MINUTES..FocusLockManager.MAX_WAIT_MINUTES) {
                                        customWaitError = "Enter ${FocusLockManager.MIN_WAIT_MINUTES}–${FocusLockManager.MAX_WAIT_MINUTES}"
                                    } else {
                                        customWaitError = null
                                        viewModel.setCooldownMinutes(parsed)
                                    }
                                },
                                enabled = isRuleEditingAllowed,
                                modifier = Modifier.testTag("apply_custom_wait_button")
                            ) { Text("Set") }
                        }
                        customWaitError?.let { err ->
                            Spacer(Modifier.height(4.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = FocusCoral)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                AmoledCard(
                    containerColor = AmoledSurface,
                    borderColor = AmoledOutlineSubtle,
                    modifier = Modifier.fillMaxWidth().testTag("password_exit_settings_card")
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = FocusEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.config.hasExitPassword) "Exit Password Enabled" else "Optional Exit Password",
                                style = MaterialTheme.typography.titleSmall,
                                color = OnAmoled
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (state.config.hasExitPassword) {
                                "Unlock or exit an active lock immediately with your password."
                            } else {
                                "Set a PIN/password for emergency instant exit without waiting."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAmoledMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        if (!state.config.hasExitPassword) {
                            Button(
                                onClick = { showSetPasswordDialog = true },
                                enabled = isRuleEditingAllowed,
                                colors = ButtonDefaults.buttonColors(containerColor = FocusEmerald, contentColor = AmoledBlack),
                                modifier = Modifier.fillMaxWidth().testTag("set_exit_password_button")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Set Exit Password", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showSetPasswordDialog = true },
                                    enabled = isRuleEditingAllowed,
                                    modifier = Modifier.weight(1f).testTag("change_exit_password_button")
                                ) { Text("Change", color = OnAmoled) }
                                OutlinedButton(
                                    onClick = viewModel::removeExitPassword,
                                    enabled = isRuleEditingAllowed,
                                    modifier = Modifier.weight(1f).testTag("remove_exit_password_button")
                                ) { Text("Remove", color = FocusCoral) }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
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

    if (showSetPasswordDialog) {
        SetExitPasswordDialog(
            isUpdating = state.config.hasExitPassword,
            onDismiss = { showSetPasswordDialog = false },
            onSave = { password, confirm ->
                if (viewModel.setExitPassword(password, confirm)) {
                    showSetPasswordDialog = false
                }
            }
        )
    }

    if (showPasswordExitDialog) {
        PasswordExitDialog(
            onDismiss = { showPasswordExitDialog = false },
            onVerifyAndUnlock = { password, exitCompletely ->
                val ok = viewModel.unlockWithPassword(password, exitCompletely)
                if (ok) showPasswordExitDialog = false
                ok
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

    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(window.name, style = MaterialTheme.typography.titleSmall, color = OnAmoled)
                Text(
                    "${FocusLockManager.formatMinuteOfDay(window.startMinuteOfDay)} – ${FocusLockManager.formatMinuteOfDay(window.endMinuteOfDay)}",
                    style = TabularStatStyle.copy(fontSize = 13.sp),
                    color = FocusEmerald
                )
                Text(
                    activeDayText.ifBlank { "No days selected" },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnAmoledMuted
                )
            }
            IconButton(onClick = onEdit, enabled = editingAllowed) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit window", tint = OnAmoledMuted)
            }
            IconButton(onClick = onDelete, enabled = editingAllowed) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete window", tint = FocusCoral)
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
        containerColor = AmoledSurfaceElevated,
        title = { Text(if (initial == null) "Add focus window" else "Edit focus window", color = OnAmoled) },
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
                Text("Start time", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(4.dp))
                TimeInput(state = startPicker)

                Spacer(Modifier.height(8.dp))
                Text("End time", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(4.dp))
                TimeInput(state = endPicker)

                Spacer(Modifier.height(10.dp))
                Text("Active days", style = MaterialTheme.typography.labelLarge, color = OnAmoled)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayChips.forEach { (dow, label) ->
                        val active = dow in selectedDays
                        FilterChip(
                            selected = active,
                            onClick = { selectedDays = if (active) selectedDays - dow else selectedDays + dow },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(name, startPicker.hour * 60 + startPicker.minute, endPicker.hour * 60 + endPicker.minute, selectedDays)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SetExitPasswordDialog(
    isUpdating: Boolean,
    onDismiss: () -> Unit,
    onSave: (password: String, confirmPassword: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text(if (isUpdating) "Change exit password" else "Set exit password", color = OnAmoled) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter a password or PIN (min ${FocusLockManager.MIN_PASSWORD_LENGTH} characters) to exit an active lock anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnAmoledMuted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text("Password / PIN") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("set_password_input")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorText = null },
                    label = { Text("Confirm password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_password_input")
                )
                errorText?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = FocusCoral)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.length < FocusLockManager.MIN_PASSWORD_LENGTH ->
                            errorText = "Use at least ${FocusLockManager.MIN_PASSWORD_LENGTH} characters."
                        password != confirmPassword ->
                            errorText = "Passwords do not match."
                        else -> onSave(password, confirmPassword)
                    }
                },
                modifier = Modifier.testTag("save_exit_password_confirm_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PasswordExitDialog(
    onDismiss: () -> Unit,
    onVerifyAndUnlock: (password: String, exitCompletely: Boolean) -> Boolean
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Exit Lock with Password", color = OnAmoled) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter your exit password to unlock for 5 minutes or turn off the active lock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnAmoledMuted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text("Exit password") },
                    singleLine = true,
                    isError = errorText != null,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("unlock_password_input")
                )
                errorText?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, style = MaterialTheme.typography.bodySmall, color = FocusCoral)
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (password.isBlank()) errorText = "Enter your password."
                        else if (!onVerifyAndUnlock(password, true)) errorText = "Incorrect password."
                    },
                    modifier = Modifier.fillMaxWidth().testTag("password_exit_completely_button")
                ) { Text("Remove Lock Now") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (password.isBlank()) errorText = "Enter your password."
                        else if (!onVerifyAndUnlock(password, false)) errorText = "Incorrect password."
                    },
                    modifier = Modifier.fillMaxWidth().testTag("password_temp_unlock_button")
                ) { Text("Unlock for 5 Minutes") }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
