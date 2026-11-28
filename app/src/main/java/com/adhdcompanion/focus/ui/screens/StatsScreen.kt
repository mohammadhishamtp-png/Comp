package com.adhdcompanion.focus.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockedAppSummary
import com.adhdcompanion.focus.model.DailyFocusStat
import com.adhdcompanion.focus.model.HeatmapDayCell
import com.adhdcompanion.focus.model.SubjectBreakdownItem
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.components.formatMinutesReadable
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusAmber
import com.adhdcompanion.focus.ui.theme.FocusCyan
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import com.adhdcompanion.focus.ui.viewmodel.StatsViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories

@Composable
fun StatsScreen(
    container: AppContainer,
    onOpenShield: () -> Unit
) {
    val viewModel = viewModel<StatsViewModel>(factory = ViewModelFactories.stats(container))
    val stats by viewModel.overview.collectAsState()

    var selectedDayIndex by remember { mutableIntStateOf(6) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = OnAmoled
        )
        Text(
            text = "Consistency heatmap, subject split & shield deflections",
            style = MaterialTheme.typography.bodySmall,
            color = OnAmoledMuted
        )

        Spacer(Modifier.height(16.dp))

        // 1. Minimalist 2x2 KPI Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                label = "TODAY",
                value = formatMinutesReadable(stats.todayStudyMinutes),
                accent = FocusEmerald,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                label = "7-DAY TOTAL",
                value = formatMinutesReadable(stats.weeklyStudyMinutes),
                accent = FocusCyan,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                label = "DAILY AVG",
                value = formatMinutesReadable(stats.dailyAverageMinutes),
                accent = OnAmoled,
                modifier = Modifier.weight(1f)
            )
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle,
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = FocusAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("STREAK", style = MaterialTheme.typography.labelSmall, color = OnAmoledMuted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${stats.currentStreakDays}d · Best ${stats.bestStreakDays}d",
                        style = TabularStatStyle.copy(fontSize = 17.sp),
                        color = FocusAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. 91-Day Consistency Heatmap
        StudyHeatmapCard(cells = stats.heatmapDays)

        Spacer(Modifier.height(16.dp))

        // 3. Subject Breakdown (Past 7 Days)
        SubjectBreakdownCard(items = stats.subjectBreakdown)

        Spacer(Modifier.height(16.dp))

        // 4. 7-Day Focus Bar Chart
        if (stats.weeklyDailyStats.isNotEmpty()) {
            val safeIdx = selectedDayIndex.coerceIn(0, stats.weeklyDailyStats.lastIndex)
            WeeklyFocusBarCard(
                days = stats.weeklyDailyStats,
                selectedIndex = safeIdx,
                selectedDay = stats.weeklyDailyStats[safeIdx],
                onSelectDay = { selectedDayIndex = it }
            )
        }

        Spacer(Modifier.height(16.dp))

        // 5. Regain Distraction Shield Analytics
        BlockedAppAnalyticsCard(
            apps = stats.topBlockedApps,
            totalAttempts = stats.totalWeeklyBlockedAttempts,
            onOpenShield = onOpenShield
        )

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnAmoledMuted)
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = TabularStatStyle.copy(fontSize = 21.sp),
                color = accent
            )
        }
    }
}

@Composable
private fun StudyHeatmapCard(cells: List<HeatmapDayCell>) {
    var inspected by remember(cells) { mutableStateOf(cells.lastOrNull()) }

    val columns = remember(cells) {
        cells.chunked(7)
    }

    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("study_heatmap_card")
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("91-Day Consistency")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Less", style = MaterialTheme.typography.labelSmall, color = OnAmoledSubtle)
                    (0..4).forEach { lvl ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatmapColor(lvl))
                        )
                    }
                    Text("More", style = MaterialTheme.typography.labelSmall, color = OnAmoledSubtle)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                columns.forEach { weekCol ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        weekCol.forEach { cell ->
                            val isSelected = inspected?.dayEpoch == cell.dayEpoch
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(heatmapColor(cell.intensityLevel))
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) OnAmoled else Color.Transparent,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .clickable { inspected = cell }
                            )
                        }
                    }
                }
            }

            inspected?.let { day ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${day.dateLabel} · ${formatMinutesReadable(day.focusedMinutes)} studied",
                    style = TabularStatStyle.copy(fontSize = 12.sp),
                    color = FocusEmerald
                )
            }
        }
    }
}

private fun heatmapColor(level: Int): Color {
    return when (level) {
        1 -> FocusEmerald.copy(alpha = 0.25f)
        2 -> FocusEmerald.copy(alpha = 0.48f)
        3 -> FocusEmerald.copy(alpha = 0.72f)
        4 -> FocusEmerald
        else -> AmoledSurfaceElevated
    }
}

@Composable
private fun SubjectBreakdownCard(items: List<SubjectBreakdownItem>) {
    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionLabel("Subject Split (7 Days)")
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(
                    "Log your first study session on Focus to see subject distribution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnAmoledMuted
                )
            } else {
                items.forEach { item ->
                    val color = Color(item.colorHex)
                    val pct = (item.shareFraction * 100).toInt()
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.subjectName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = OnAmoled,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "${formatMinutesReadable(item.totalMinutes)} ($pct%)",
                                style = TabularStatStyle.copy(fontSize = 12.sp),
                                color = color
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(AmoledSurfaceElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.shareFraction.coerceAtLeast(if (item.totalMinutes > 0) 0.04f else 0f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyFocusBarCard(
    days: List<DailyFocusStat>,
    selectedIndex: Int,
    selectedDay: DailyFocusStat,
    onSelectDay: (Int) -> Unit
) {
    val maxMinutes = (days.maxOfOrNull { it.focusedMinutes } ?: 0).coerceAtLeast(30)
    val animatedFractions = days.mapIndexed { idx, day ->
        val raw = day.focusedMinutes.toFloat() / maxMinutes.toFloat()
        animateFloatAsState(
            targetValue = raw.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "stat_bar_$idx"
        ).value
    }

    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            SectionLabel("7-Day Daily Volume")
            Text(
                "${selectedDay.dayLabel} (${selectedDay.dateLabel}) · ${formatMinutesReadable(selectedDay.focusedMinutes)}",
                style = TabularStatStyle.copy(fontSize = 13.sp),
                color = OnAmoled
            )
            Spacer(Modifier.height(14.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .pointerInput(days.size) {
                        detectTapGestures { offset ->
                            if (days.isNotEmpty()) {
                                val slotWidth = size.width / days.size.toFloat()
                                onSelectDay((offset.x / slotWidth).toInt().coerceIn(0, days.lastIndex))
                            }
                        }
                    }
            ) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                for (step in listOf(0f, 0.5f, 1f)) {
                    val y = size.height * (1f - step)
                    drawLine(
                        color = AmoledOutline,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 0.75.dp.toPx(),
                        pathEffect = if (step == 0f) null else dash
                    )
                }
                val slotWidth = size.width / days.size.toFloat()
                val barWidth = (slotWidth * 0.50f).coerceAtMost(32.dp.toPx())
                val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                days.forEachIndexed { idx, day ->
                    val x = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val isSel = idx == selectedIndex
                    val barH = if (day.focusedMinutes <= 0) 4.dp.toPx()
                    else (size.height * animatedFractions[idx]).coerceAtLeast(8.dp.toPx())
                    val color = when {
                        day.focusedMinutes <= 0 -> AmoledSurfaceElevated
                        isSel -> FocusEmerald
                        else -> FocusEmerald.copy(alpha = 0.42f)
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, size.height - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = radius
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { idx, day ->
                    Text(
                        text = day.dayLabel.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (idx == selectedIndex) FocusEmerald else OnAmoledMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedAppAnalyticsCard(
    apps: List<BlockedAppSummary>,
    totalAttempts: Int,
    onOpenShield: () -> Unit
) {
    val maxAttempts = (apps.maxOfOrNull { it.attemptCount } ?: 1).coerceAtLeast(1)

    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Shield Deflections (7 Days)")
                    Text(
                        text = if (totalAttempts > 0) "$totalAttempts distractions paused this week"
                        else "No blocked app attempts yet this week",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoledMuted
                    )
                }
                IconButton(onClick = onOpenShield) {
                    Icon(Icons.Filled.Shield, contentDescription = "Open Focus Shield", tint = FocusEmerald)
                }
            }

            if (apps.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onOpenShield,
                    border = BorderStroke(0.75.dp, AmoledOutline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnAmoled),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configure Shield & Allowed Tools")
                }
            } else {
                Spacer(Modifier.height(12.dp))
                apps.take(5).forEach { app ->
                    val frac = (app.attemptCount.toFloat() / maxAttempts.toFloat()).coerceIn(0.08f, 1f)
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                app.appLabel,
                                style = MaterialTheme.typography.titleSmall,
                                color = OnAmoled,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AmoledSurfaceHigh
                            ) {
                                Text(
                                    "${app.attemptCount}x",
                                    style = TabularStatStyle.copy(fontSize = 12.sp),
                                    color = FocusAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(AmoledSurfaceElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(FocusAmber)
                            )
                        }
                    }
                }
            }
        }
    }
}
