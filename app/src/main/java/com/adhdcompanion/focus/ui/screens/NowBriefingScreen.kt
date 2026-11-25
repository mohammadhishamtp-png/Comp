package com.adhdcompanion.focus.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.alarm.AlarmEventBus
import com.adhdcompanion.focus.alarm.AlarmSoundPlayer
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.viewmodel.NowViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import java.util.Locale

@Composable
fun NowBriefingScreen(
    container: AppContainer,
    autoSpeakTriggerId: Int = 0,
    onOpenSettings: () -> Unit,
    onOpenFocusLock: () -> Unit,
    onOpenDashboard: () -> Unit,
    onOpenPlanner: () -> Unit
) {
    val viewModel = viewModel<NowViewModel>(factory = ViewModelFactories.now(container))
    val state by viewModel.uiState.collectAsState()
    val isAlarmRinging by AlarmEventBus.isRinging.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional at startup */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.refresh()
    }

    LaunchedEffect(autoSpeakTriggerId) {
        if (autoSpeakTriggerId > 0) {
            viewModel.speakBriefing()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Right now", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenDashboard,
                    modifier = Modifier.testTag("open_dashboard_button")
                ) {
                    Icon(
                        Icons.Filled.BarChart,
                        contentDescription = "Focus Dashboard",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onOpenFocusLock,
                    modifier = Modifier.testTag("open_focus_lock_button")
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = "App Blocker & Lock",
                        tint = if (state.lockEvaluation.isBlockingActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }

        if (isAlarmRinging) {
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Briefing alarm ringing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            AlarmSoundPlayer.stop()
                            viewModel.speakBriefing()
                        }
                    ) {
                        Icon(Icons.Filled.AlarmOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop & Speak")
                    }
                }
            }
        }

        if (state.lockEvaluation.isBlockingActive && state.lockEvaluation.activeWindow != null) {
            val window = state.lockEvaluation.activeWindow!!
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFocusLock)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Focus Lock Active · ${window.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Distractions guarded until ${FocusLockManager.formatMinuteOfDay(window.endMinuteOfDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!container.prefs.hasApiKey()) {
            ApiKeyNudgeCard(onOpenSettings)
            Spacer(Modifier.height(16.dp))
        }

        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.currentBlock != null -> CurrentBlockCard(
                block = state.currentBlock!!,
                nextBlock = state.nextBlock,
                isSpeaking = state.isSpeaking,
                onSpeakBriefing = viewModel::speakBriefing,
                onStopSpeaking = viewModel::stopSpeaking,
                onSpeakStep = viewModel::speakMicroStepOnly,
                microStepSecondsRemaining = state.microStepSecondsRemaining,
                onStartMicroStep = viewModel::startFiveMinuteMicroStep,
                onStopMicroStep = viewModel::cancelMicroStepTimer,
                onComplete = viewModel::markCurrentBlockCompleted
            )

            state.nextBlock != null -> NextUpHeroCard(
                nextBlock = state.nextBlock!!,
                nowMinuteOfDay = state.nowMinuteOfDay,
                isSpeaking = state.isSpeaking,
                onSpeakBriefing = viewModel::speakBriefing,
                onStopSpeaking = viewModel::stopSpeaking,
                onSpeakStep = viewModel::speakMicroStepOnly,
                microStepSecondsRemaining = state.microStepSecondsRemaining,
                onStartMicroStep = viewModel::startFiveMinuteMicroStep,
                onStopMicroStep = viewModel::cancelMicroStepTimer,
                onComplete = { viewModel.markBlockCompleted(state.nextBlock!!) }
            )

            state.todaysBlocks.isNotEmpty() -> AllDoneTodayCard(
                completedCount = state.completedCount,
                onOpenPlanner = onOpenPlanner
            )

            else -> EmptyNowCard(onOpenPlanner = onOpenPlanner)
        }

        state.briefingScript?.let { script ->
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Briefing script")
                    Text(script, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // Always show upcoming / today's blocks cleanly so Now never feels stuck empty
        if (state.todaysBlocks.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Today's schedule (${state.completedCount}/${state.totalCount})")
                Text(
                    "Edit in Planner",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenPlanner)
                )
            }
            Spacer(Modifier.height(6.dp))
            state.todaysBlocks.forEach { block ->
                val isActive = block.id == state.currentBlock?.id
                TodayTimelineRow(
                    block = block,
                    isActive = isActive,
                    onMarkComplete = {
                        if (!block.isCompleted) viewModel.markBlockCompleted(block)
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CurrentBlockCard(
    block: DayBlock,
    nextBlock: DayBlock?,
    isSpeaking: Boolean,
    onSpeakBriefing: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakStep: () -> Unit,
    microStepSecondsRemaining: Int?,
    onStartMicroStep: () -> Unit,
    onStopMicroStep: () -> Unit,
    onComplete: () -> Unit
) {
    val cardOn = MaterialTheme.colorScheme.onPrimary
    val cardBg = MaterialTheme.colorScheme.primary
    val filledOnCard = ButtonDefaults.buttonColors(
        containerColor = cardOn,
        contentColor = cardBg,
        disabledContainerColor = cardOn.copy(alpha = 0.55f),
        disabledContentColor = cardBg.copy(alpha = 0.75f)
    )
    val outlinedOnCard = ButtonDefaults.outlinedButtonColors(
        contentColor = cardOn,
        disabledContentColor = cardOn.copy(alpha = 0.6f)
    )
    val outlineBorder = BorderStroke(1.5.dp, cardOn)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "ACTIVE NOW · ${formatTimeRange(block.startMinuteOfDay, block.durationMinutes)}",
                style = MaterialTheme.typography.labelLarge,
                color = cardOn.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                block.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = cardOn
            )
            if (block.why.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    block.why,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cardOn.copy(alpha = 0.9f)
                )
            }

            Spacer(Modifier.height(18.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.14f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "TINY FIRST STEP",
                        style = MaterialTheme.typography.labelLarge,
                        color = cardOn.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        block.microStep.ifBlank { "Just open what you need and look at it. That's it." },
                        style = MaterialTheme.typography.titleMedium,
                        color = cardOn
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = if (isSpeaking) onStopSpeaking else onSpeakBriefing,
                    colors = filledOnCard
                ) {
                    Icon(
                        if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(if (isSpeaking) "Stop audio" else "Speak briefing")
                }
                OutlinedButton(
                    onClick = onSpeakStep,
                    enabled = !isSpeaking,
                    colors = outlinedOnCard,
                    border = outlineBorder
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Read step")
                }
            }

            Spacer(Modifier.height(12.dp))
            if (microStepSecondsRemaining != null) {
                val mins = microStepSecondsRemaining / 60
                val secs = microStepSecondsRemaining % 60
                Text(
                    "%d:%02d".format(Locale.getDefault(), mins, secs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = cardOn,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStopMicroStep,
                    modifier = Modifier.fillMaxWidth(),
                    colors = filledOnCard
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Stop timer")
                }
            } else {
                Button(
                    onClick = onStartMicroStep,
                    modifier = Modifier.fillMaxWidth(),
                    colors = filledOnCard
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Start 5-Min Micro-Step")
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedOnCard,
                border = outlineBorder
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Mark block complete")
            }

            if (nextBlock != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Next up (${FocusLockManager.formatMinuteOfDay(nextBlock.startMinuteOfDay)}): ${nextBlock.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cardOn.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun NextUpHeroCard(
    nextBlock: DayBlock,
    nowMinuteOfDay: Int,
    isSpeaking: Boolean,
    onSpeakBriefing: () -> Unit,
    onStopSpeaking: () -> Unit,
    onSpeakStep: () -> Unit,
    microStepSecondsRemaining: Int?,
    onStartMicroStep: () -> Unit,
    onStopMicroStep: () -> Unit,
    onComplete: () -> Unit
) {
    val minutesUntil = (nextBlock.startMinuteOfDay - nowMinuteOfDay).coerceAtLeast(0)
    val timingBadge = if (minutesUntil == 0) "Starting now" else "Up next in $minutesUntil min"

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(
                "$timingBadge · ${FocusLockManager.formatMinuteOfDay(nextBlock.startMinuteOfDay)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(nextBlock.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (nextBlock.why.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    nextBlock.why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (nextBlock.microStep.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        SectionLabel("Tiny first step")
                        Text(nextBlock.microStep, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = if (isSpeaking) onStopSpeaking else onSpeakBriefing) {
                    Icon(
                        if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSpeaking) "Stop audio" else "Speak briefing")
                }
                OutlinedButton(onClick = onSpeakStep, enabled = !isSpeaking) {
                    Text("Read step")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (microStepSecondsRemaining != null) {
                val mins = microStepSecondsRemaining / 60
                val secs = microStepSecondsRemaining % 60
                Text(
                    "%d:%02d".format(Locale.getDefault(), mins, secs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onStopMicroStep, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop 5-min timer")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onStartMicroStep, modifier = Modifier.weight(1f)) {
                        Text("Start 5-Min Step")
                    }
                    OutlinedButton(onClick = onComplete, modifier = Modifier.weight(1f)) {
                        Text("Mark done")
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayTimelineRow(
    block: DayBlock,
    isActive: Boolean,
    onMarkComplete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isActive -> MaterialTheme.colorScheme.primaryContainer
                block.isDecompressionBuffer -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMarkComplete) {
                Icon(
                    if (block.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = "Complete block",
                    tint = if (block.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatTimeRange(block.startMinuteOfDay, block.durationMinutes)} · ${block.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (block.isCompleted) TextDecoration.LineThrough else null
                )
                if (block.microStep.isNotBlank()) {
                    Text(
                        block.microStep,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDoneTodayCard(completedCount: Int, onOpenPlanner: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "All $completedCount blocks finished",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Nice steady work today. Add another block in Planner if you want, or rest guilt-free.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onOpenPlanner) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open Planner")
            }
        }
    }
}

@Composable
private fun EmptyNowCard(onOpenPlanner: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text("Nothing scheduled yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Head to the Planner tab to lay out a low-friction day or add your first block — no pressure.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenPlanner, modifier = Modifier.testTag("empty_open_planner_button")) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Plan my day")
            }
        }
    }
}

@Composable
private fun ApiKeyNudgeCard(onOpenSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Add your Gemini key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "The AI Planner, Doubt Solver, and Supporter need it to generate plans and breakdowns.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onOpenSettings) { Text("Add") }
        }
    }
}

private fun formatTimeRange(startMinute: Int, durationMinutes: Int): String {
    val endMinute = (startMinute + durationMinutes).coerceAtMost(23 * 60 + 59)
    return "${FocusLockManager.formatMinuteOfDay(startMinute)}–${FocusLockManager.formatMinuteOfDay(endMinute)}"
}
