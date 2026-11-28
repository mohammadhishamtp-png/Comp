package com.adhdcompanion.focus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.SoundscapeType
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.model.StudyTimerMode
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.components.SegmentedModeBar
import com.adhdcompanion.focus.ui.components.formatCompactTimer
import com.adhdcompanion.focus.ui.components.formatHms
import com.adhdcompanion.focus.ui.components.formatMinutesReadable
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusAmber
import com.adhdcompanion.focus.ui.theme.FocusCoral
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import com.adhdcompanion.focus.ui.theme.SubjectColorPalette
import com.adhdcompanion.focus.ui.theme.TabularHeroTimerStyle
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import com.adhdcompanion.focus.ui.viewmodel.StudyTimerUiState
import com.adhdcompanion.focus.ui.viewmodel.StudyTimerViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    container: AppContainer,
    timerViewModel: StudyTimerViewModel = viewModel(factory = ViewModelFactories.studyTimer(container)),
    onOpenNowBriefing: () -> Unit,
    onOpenDoubtSolver: () -> Unit,
    onOpenStuck: () -> Unit,
    onOpenBrainDump: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenShield: () -> Unit
) {
    val state by timerViewModel.uiState.collectAsState()
    var showSubjectManager by remember { mutableStateOf(false) }
    var showSoundscapeSheet by remember { mutableStateOf(false) }

    if (state.isFullScreenFocus) {
        FullScreenFocusOverlay(
            state = state,
            onPause = { timerViewModel.pauseSession("Paused from full-screen") },
            onResume = timerViewModel::startOrResume,
            onFinish = timerViewModel::finishAndSaveSession,
            onExitFullScreen = { timerViewModel.setFullScreenFocus(false) },
            onToggleSoundscape = timerViewModel::toggleSoundscape
        )
        return
    }

    val selectedSubject = state.selectedSubject
    val subjectColor = Color(selectedSubject?.colorHex ?: SubjectColorPalette[0])
    val liveActiveExtraSec = if (state.isRunning && !state.isPomodoroBreak) state.activeSegmentElapsedSeconds else 0
    val liveTodayTotalSeconds = state.todaysTotalSeconds + liveActiveExtraSec

    val primaryGoal = state.examGoals.firstOrNull()
    val dDayLabel = remember(primaryGoal) {
        primaryGoal?.let {
            val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
            val left = (it.targetDateEpoch - today).coerceAtLeast(0L)
            "${it.title} · D-$left"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // 1. Minimalist Top Utility Strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dDayLabel ?: "TODAY'S FOCUS",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.0.sp,
                    color = if (dDayLabel != null) FocusAmber else OnAmoledMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatHms(liveTodayTotalSeconds),
                    style = TabularStatStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    color = OnAmoled,
                    modifier = Modifier.testTag("today_total_timer")
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onOpenDoubtSolver,
                    modifier = Modifier.testTag("timer_to_doubt_button")
                ) {
                    Icon(Icons.Filled.Psychology, contentDescription = "AI Doubt Solver", tint = FocusEmerald, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onOpenStuck,
                    modifier = Modifier.testTag("timer_to_stuck_button")
                ) {
                    Icon(Icons.Outlined.Bolt, contentDescription = "I'm Stuck", tint = FocusAmber, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onOpenNowBriefing,
                    modifier = Modifier.testTag("timer_to_now_button")
                ) {
                    Icon(Icons.Outlined.Home, contentDescription = "Now & Alarms", tint = OnAmoledMuted, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onOpenBrainDump,
                    modifier = Modifier.testTag("timer_to_dump_button")
                ) {
                    Icon(Icons.Outlined.Inbox, contentDescription = "Brain Dump", tint = OnAmoledMuted, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("timer_to_settings_button")
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = OnAmoledMuted, modifier = Modifier.size(20.dp))
                }
            }
        }

        state.feedbackMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            AmoledCard(containerColor = AmoledSurfaceHigh, borderColor = FocusEmerald.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = OnAmoled, modifier = Modifier.weight(1f))
                    TextButton(onClick = timerViewModel::clearFeedback) { Text("OK", color = FocusEmerald) }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2. Unified Segmented Mode Switcher
        val modeIndex = when (state.mode) {
            StudyTimerMode.STOPWATCH -> 0
            StudyTimerMode.POMODORO -> 1
            StudyTimerMode.COUNTDOWN -> 2
        }
        SegmentedModeBar(
            options = listOf("Stopwatch", "Pomodoro", "Countdown"),
            selectedIndex = modeIndex,
            onSelect = { idx ->
                val chosen = when (idx) {
                    1 -> StudyTimerMode.POMODORO
                    2 -> StudyTimerMode.COUNTDOWN
                    else -> StudyTimerMode.STOPWATCH
                }
                timerViewModel.setMode(chosen)
            },
            enabled = !state.isRunning && !state.isPaused,
            accentColor = subjectColor,
            testTagPrefix = "mode_seg"
        )

        if (!state.isRunning && !state.isPaused && state.mode != StudyTimerMode.STOPWATCH) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.mode == StudyTimerMode.POMODORO) {
                    listOf(25 to 5, 45 to 10, 50 to 10, 60 to 15).forEach { (f, b) ->
                        val active = state.pomodoroFocusMinutes == f && state.pomodoroBreakMinutes == b
                        PresetTinyPill(
                            label = "$f/$b",
                            selected = active,
                            accent = subjectColor,
                            onClick = { timerViewModel.setPomodoroConfig(f, b) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        val active = state.countdownTargetMinutes == mins
                        PresetTinyPill(
                            label = "${mins}m",
                            selected = active,
                            accent = subjectColor,
                            onClick = { timerViewModel.setCountdownMinutes(mins) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // 3. Minimalist Hero Focus Dial (No bulky card!)
        val dialAccent = if (state.isPomodoroBreak) FocusAmber else subjectColor
        val targetFraction = remember(state.mode, state.displaySeconds, state.isPomodoroBreak, state.pomodoroFocusMinutes, state.pomodoroBreakMinutes, state.countdownTargetMinutes, selectedSubject, state.subjectMinutesToday, state.activeSegmentElapsedSeconds) {
            when (state.mode) {
                StudyTimerMode.STOPWATCH -> {
                    val subjectGoalSec = ((selectedSubject?.dailyTargetMinutes ?: 90) * 60).coerceAtLeast(60)
                    val todaySubSec = ((state.subjectMinutesToday[selectedSubject?.id] ?: 0) * 60) +
                        if (state.isRunning) state.activeSegmentElapsedSeconds else 0
                    (todaySubSec.toFloat() / subjectGoalSec.toFloat()).coerceIn(0.03f, 1f)
                }
                StudyTimerMode.POMODORO -> {
                    val total = (if (state.isPomodoroBreak) state.pomodoroBreakMinutes else state.pomodoroFocusMinutes) * 60
                    val done = (total - state.displaySeconds).coerceAtLeast(0)
                    (done.toFloat() / total.coerceAtLeast(1).toFloat()).coerceIn(0.02f, 1f)
                }
                StudyTimerMode.COUNTDOWN -> {
                    val total = state.countdownTargetMinutes * 60
                    val done = (total - state.displaySeconds).coerceAtLeast(0)
                    (done.toFloat() / total.coerceAtLeast(1).toFloat()).coerceIn(0.02f, 1f)
                }
            }
        }
        val animatedSweep by animateFloatAsState(
            targetValue = targetFraction * 360f,
            animationSpec = tween(durationMillis = 500),
            label = "focus_dial_sweep"
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(232.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 5.dp.toPx()
                    val pad = strokeW + 8.dp.toPx()
                    val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
                    val topLeft = Offset(pad, pad)

                    if (state.isRunning) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(dialAccent.copy(alpha = 0.14f), Color.Transparent)
                            ),
                            radius = size.minDimension / 2f
                        )
                    }

                    drawArc(
                        color = AmoledOutlineSubtle,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW)
                    )

                    drawArc(
                        color = dialAccent,
                        startAngle = -90f,
                        sweepAngle = animatedSweep.coerceAtLeast(8f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = when {
                            state.isPomodoroBreak -> "BREAK · #${state.pomodoroCyclesCompleted}"
                            state.isRunning -> "FOCUSING"
                            state.isPaused -> "PAUSED"
                            else -> "READY"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = dialAccent
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatCompactTimer(state.displaySeconds),
                        style = TabularHeroTimerStyle.copy(fontSize = 44.sp),
                        color = OnAmoled,
                        modifier = Modifier.testTag("hero_timer_display")
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = selectedSubject?.name ?: "Select Subject",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnAmoledMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { timerViewModel.setFullScreenFocus(true) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .testTag("fullscreen_focus_button")
            ) {
                Icon(
                    Icons.Filled.Fullscreen,
                    contentDescription = "Full-Screen Zen Mode",
                    tint = OnAmoledSubtle
                )
            }
        }

        if (state.currentSessionInterruptions > 0 || state.blockedDuringSessionCount > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pauses ${state.currentSessionInterruptions} · Deflected ${state.blockedDuringSessionCount}",
                style = TabularStatStyle.copy(fontSize = 11.5.sp),
                color = OnAmoledSubtle,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(Modifier.height(18.dp))

        // 4. Primary Action Row (Clean high-contrast pills)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!state.isRunning) {
                Button(
                    onClick = timerViewModel::startOrResume,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = dialAccent,
                        contentColor = AmoledBlack
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("timer_start_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.isPaused) "Resume ${selectedSubject?.name ?: ""}".trim() else "Start Focus",
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { timerViewModel.pauseSession("Break") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmoledSurfaceHigh,
                        contentColor = OnAmoled
                    ),
                    border = BorderStroke(0.75.dp, AmoledOutline),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("timer_pause_button")
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Pause", fontWeight = FontWeight.SemiBold)
                }
            }

            if (state.isRunning || state.isPaused) {
                if (state.isPomodoroBreak) {
                    OutlinedButton(
                        onClick = timerViewModel::skipPomodoroBreak,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("timer_skip_break_button")
                    ) {
                        Icon(Icons.Filled.SkipNext, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Skip")
                    }
                } else {
                    OutlinedButton(
                        onClick = timerViewModel::finishAndSaveSession,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.75.dp, FocusCoral.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("timer_finish_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, tint = FocusCoral, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Finish", color = FocusCoral)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val lockCfg by container.focusLockManager.config.collectAsState()
        val activeGuardsCount = (if (state.isStrictLockEnabled) 1 else 0) +
            (if (lockCfg.blockShortsAndReels) 1 else 0) +
            (if (lockCfg.youtubeStudyModeEnabled) 1 else 0)

        // 5. Compact 2-Pill Strip: Strict Shield + Soundscape Drawer Trigger
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (activeGuardsCount > 0) FocusEmerald.copy(alpha = 0.1f) else AmoledSurface,
                border = BorderStroke(
                    0.75.dp,
                    if (activeGuardsCount > 0) FocusEmerald.copy(alpha = 0.45f) else AmoledOutlineSubtle
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { timerViewModel.toggleStrictLock(!state.isStrictLockEnabled) }
                    .testTag("strict_lock_switch")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (activeGuardsCount > 0) Icons.Filled.Shield else Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = if (activeGuardsCount > 0) FocusEmerald else OnAmoledMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = when {
                                state.isStrictLockEnabled -> "Shield On"
                                lockCfg.youtubeStudyModeEnabled -> "YT Study On"
                                lockCfg.blockShortsAndReels -> "Shorts Off"
                                else -> "Shield Off"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (activeGuardsCount > 0) FocusEmerald else OnAmoledMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Rules",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnAmoledSubtle,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenShield)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (state.isSoundscapePlaying) FocusEmerald.copy(alpha = 0.1f) else AmoledSurface,
                border = BorderStroke(
                    0.75.dp,
                    if (state.isSoundscapePlaying) FocusEmerald.copy(alpha = 0.45f) else AmoledOutlineSubtle
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showSoundscapeSheet = true }
                    .testTag("open_soundscape_sheet_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (state.isSoundscapePlaying) FocusEmerald else OnAmoledMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (state.isSoundscapePlaying) state.soundscapeType.label else "Soundscape",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.isSoundscapePlaying) FocusEmerald else OnAmoledMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // 6. YPT-Style Subject Matrix (1-Tap Play directly on every subject row!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Subjects · Tap play to start")
            TextButton(
                onClick = { showSubjectManager = true },
                modifier = Modifier.testTag("manage_subjects_button")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = FocusEmerald)
                Spacer(Modifier.width(4.dp))
                Text("Manage", color = FocusEmerald, style = MaterialTheme.typography.labelMedium)
            }
        }

        AmoledCard(
            containerColor = AmoledSurface,
            borderColor = AmoledOutlineSubtle,
            modifier = Modifier.fillMaxWidth()
        ) {
            state.subjects.forEachIndexed { idx, subject ->
                val isSelected = subject.id == selectedSubject?.id
                val isThisRunning = isSelected && state.isRunning && !state.isPomodoroBreak
                val accent = Color(subject.colorHex)
                val baseMins = state.subjectMinutesToday[subject.id] ?: 0
                val liveExtraSec = if (isThisRunning) state.activeSegmentElapsedSeconds else 0
                val totalSubSeconds = (baseMins * 60) + liveExtraSec
                val targetSecs = (subject.dailyTargetMinutes * 60).coerceAtLeast(60)
                val progress = (totalSubSeconds.toFloat() / targetSecs.toFloat()).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) AmoledSurfaceElevated else Color.Transparent)
                        .clickable { timerViewModel.selectSubject(subject.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("subject_pill_${subject.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = subject.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) OnAmoled else OnAmoledMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isThisRunning || totalSubSeconds > 0) {
                                    "${formatHms(totalSubSeconds)} / ${formatMinutesReadable(subject.dailyTargetMinutes)}"
                                } else {
                                    "Goal ${formatMinutesReadable(subject.dailyTargetMinutes)}"
                                },
                                style = TabularStatStyle.copy(fontSize = 11.5.sp),
                                color = if (isThisRunning) accent else OnAmoledSubtle
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accent,
                            trackColor = AmoledSurfaceHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isThisRunning) accent else AmoledSurfaceHigh,
                        border = BorderStroke(0.75.dp, if (isThisRunning) accent else AmoledOutline),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { timerViewModel.startOrSwitchSubject(subject.id) }
                            .testTag("subject_play_${subject.name}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isThisRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isThisRunning) "Pause ${subject.name}" else "Start ${subject.name}",
                                tint = if (isThisRunning) AmoledBlack else OnAmoled,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (idx < state.subjects.lastIndex) {
                    HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
                }
            }
        }

        // 7. Compact Logged Today Summary (only when sessions exist)
        if (state.todaysSessions.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Logged today · ${state.todaysSessions.size} sessions")
            Spacer(Modifier.height(6.dp))
            AmoledCard(containerColor = AmoledSurface, borderColor = AmoledOutlineSubtle) {
                state.todaysSessions.takeLast(4).reversed().forEachIndexed { i, sess ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(sess.subjectColorHex))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${sess.subjectName} · ${sess.mode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = formatHms(sess.durationSeconds),
                            style = TabularStatStyle.copy(fontSize = 12.sp),
                            color = Color(sess.subjectColorHex)
                        )
                    }
                    if (i < minOf(3, state.todaysSessions.lastIndex)) {
                        HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showSoundscapeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSoundscapeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AmoledSurfaceElevated,
            contentColor = OnAmoled
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                SectionLabel("Offline Focus Audio")
                Text("Focus Soundscapes", style = MaterialTheme.typography.titleLarge, color = OnAmoled)
                Spacer(Modifier.height(14.dp))
                listOf(
                    SoundscapeType.BROWN_NOISE,
                    SoundscapeType.SOFT_RAIN,
                    SoundscapeType.TONE_40HZ
                ).forEach { sound ->
                    val active = state.soundscapeType == sound && state.isSoundscapePlaying
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) FocusEmerald.copy(alpha = 0.14f) else AmoledSurface,
                        border = BorderStroke(0.75.dp, if (active) FocusEmerald else AmoledOutlineSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { timerViewModel.toggleSoundscape(sound) }
                            .testTag("soundscape_${sound.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(sound.label, style = MaterialTheme.typography.titleSmall, color = OnAmoled)
                                Text(sound.subtitle, style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                            }
                            Text(
                                text = if (active) "Playing" else "Tap",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) FocusEmerald else OnAmoledSubtle
                            )
                        }
                    }
                }
                if (state.isSoundscapePlaying) {
                    Spacer(Modifier.height(14.dp))
                    Text("Volume", style = MaterialTheme.typography.labelMedium, color = OnAmoledMuted)
                    Slider(
                        value = state.soundscapeVolume,
                        onValueChange = timerViewModel::setSoundscapeVolume,
                        colors = SliderDefaults.colors(
                            thumbColor = FocusEmerald,
                            activeTrackColor = FocusEmerald
                        )
                    )
                }
            }
        }
    }

    if (showSubjectManager) {
        SubjectManagerDialog(
            subjects = state.subjects,
            onDismiss = { showSubjectManager = false },
            onAddSubject = timerViewModel::addSubject,
            onUpdateSubject = timerViewModel::updateSubject,
            onArchiveSubject = timerViewModel::archiveSubject
        )
    }
}

@Composable
private fun PresetTinyPill(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else AmoledSurface,
        border = BorderStroke(0.75.dp, if (selected) accent else AmoledOutlineSubtle),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = TabularStatStyle.copy(fontSize = 12.sp),
                color = if (selected) accent else OnAmoledMuted
            )
        }
    }
}

@Composable
private fun FullScreenFocusOverlay(
    state: StudyTimerUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onExitFullScreen: () -> Unit,
    onToggleSoundscape: (SoundscapeType) -> Unit
) {
    BackHandler { onExitFullScreen() }

    val subject = state.selectedSubject
    val accent = if (state.isPomodoroBreak) FocusAmber else Color(subject?.colorHex ?: SubjectColorPalette[0])

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AmoledBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = subject?.name ?: "Deep Focus",
                        style = MaterialTheme.typography.titleSmall,
                        color = OnAmoled
                    )
                }
                IconButton(onClick = onExitFullScreen) {
                    Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit full-screen", tint = OnAmoledMuted)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (state.isPomodoroBreak) "BREAK" else state.mode.name,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = accent
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = formatCompactTimer(state.displaySeconds),
                    style = TabularHeroTimerStyle.copy(fontSize = 62.sp, lineHeight = 68.sp),
                    color = OnAmoled
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (state.isStrictLockEnabled) "Shield Locked · Stay Still" else "Zen Focus Active",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isStrictLockEnabled) FocusEmerald else OnAmoledSubtle
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SoundscapeType.BROWN_NOISE to "Brown",
                        SoundscapeType.SOFT_RAIN to "Rain",
                        SoundscapeType.TONE_40HZ to "40Hz"
                    ).forEach { (sound, label) ->
                        val active = state.soundscapeType == sound && state.isSoundscapePlaying
                        FilterChip(
                            selected = active,
                            onClick = { onToggleSoundscape(sound) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FocusEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = FocusEmerald
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = if (state.isRunning) onPause else onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AmoledBlack),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Icon(if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isRunning) "Pause" else "Resume", fontWeight = FontWeight.Bold)
                    }
                    if (state.isRunning || state.isPaused) {
                        OutlinedButton(
                            onClick = onFinish,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.75.dp, FocusCoral.copy(alpha = 0.5f)),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Finish", color = FocusCoral)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectManagerDialog(
    subjects: List<StudySubject>,
    onDismiss: () -> Unit,
    onAddSubject: (String, Long, Int) -> Unit,
    onUpdateSubject: (StudySubject) -> Unit,
    onArchiveSubject: (Long) -> Unit
) {
    var editing by remember { mutableStateOf<StudySubject?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var targetMin by remember { mutableIntStateOf(90) }
    var selectedColor by remember { mutableLongStateOf(SubjectColorPalette[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurfaceElevated,
        title = { Text("Manage Subjects", color = OnAmoled) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                subjects.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(Color(sub.colorHex))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.titleSmall, color = OnAmoled, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatMinutesReadable(sub.dailyTargetMinutes)}/day", style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                        }
                        IconButton(onClick = {
                            editing = sub
                            nameInput = sub.name
                            targetMin = sub.dailyTargetMinutes
                            selectedColor = sub.colorHex
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit subject", tint = OnAmoledMuted)
                        }
                        IconButton(onClick = { onArchiveSubject(sub.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Archive subject", tint = FocusCoral)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                SectionLabel(if (editing == null) "Add new subject" else "Edit '${editing?.name}'")
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Subject name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Daily target: ${formatMinutesReadable(targetMin)}", style = MaterialTheme.typography.bodySmall, color = OnAmoledMuted)
                Slider(
                    value = targetMin.toFloat(),
                    onValueChange = { targetMin = ((it.toInt() / 15) * 15).coerceIn(15, 480) },
                    valueRange = 15f..360f
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectColorPalette.forEach { hex ->
                        val isChosen = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isChosen) 2.5.dp else 0.dp,
                                    color = if (isChosen) OnAmoled else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChosen) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = AmoledBlack, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val currentEdit = editing
                        if (currentEdit == null) {
                            onAddSubject(nameInput, selectedColor, targetMin)
                        } else {
                            onUpdateSubject(
                                currentEdit.copy(
                                    name = nameInput,
                                    colorHex = selectedColor,
                                    dailyTargetMinutes = targetMin
                                )
                            )
                            editing = null
                        }
                        nameInput = ""
                    },
                    enabled = nameInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editing == null) "Add Subject" else "Save Changes")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = FocusEmerald) }
        }
    )
}
