package com.adhdcompanion.focus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.model.BlockedAppSummary
import com.adhdcompanion.focus.model.DailyFocusStat
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.viewmodel.DashboardViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenFocusLock: () -> Unit
) {
    BackHandler { onBack() }

    val viewModel = viewModel<DashboardViewModel>(factory = ViewModelFactories.dashboard(container))
    val stats by viewModel.stats.collectAsState()

    var selectedFocusDayIndex by remember { mutableIntStateOf(6) }
    var selectedBlockedDayIndex by remember { mutableIntStateOf(6) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Focus Dashboard") },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("dashboard_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // 1. Hero Streak & Weekly Totals Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "STREAK",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${stats.currentStreakDays} day${if (stats.currentStreakDays == 1) "" else "s"}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Best streak: ${stats.bestStreakDays}d",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            stats.dailyStats.forEach { day ->
                                val active = day.focusedMinutes > 0
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                        )
                                )
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        SectionLabel("Past 7 days")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatMinutesBrief(stats.totalWeeklyFocusMinutes),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${stats.totalWeeklyCompletedBlocks} focus blocks done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${stats.totalWeeklyBlockedAttempts} distractions blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // 2. Native Compose Canvas Bar Chart: Focus Minutes over Past 7 Days
            if (stats.dailyStats.isNotEmpty()) {
                val selectedFocusDay = stats.dailyStats.getOrElse(selectedFocusDayIndex) { stats.dailyStats.last() }
                FocusMinutesBarChartCard(
                    days = stats.dailyStats,
                    selectedIndex = selectedFocusDayIndex.coerceIn(0, stats.dailyStats.lastIndex),
                    selectedDay = selectedFocusDay,
                    onSelectDay = { selectedFocusDayIndex = it },
                    onOpenPlanner = onOpenPlanner
                )
            }

            Spacer(Modifier.height(20.dp))

            // 3. Native Compose Canvas Area/Line Chart: Blocked App Redirects over Past 7 Days
            if (stats.dailyStats.isNotEmpty()) {
                val selectedBlockedDay = stats.dailyStats.getOrElse(selectedBlockedDayIndex) { stats.dailyStats.last() }
                BlockedAttemptsLineChartCard(
                    days = stats.dailyStats,
                    selectedIndex = selectedBlockedDayIndex.coerceIn(0, stats.dailyStats.lastIndex),
                    selectedDay = selectedBlockedDay,
                    onSelectDay = { selectedBlockedDayIndex = it }
                )
            }

            Spacer(Modifier.height(20.dp))

            // 4. Top Blocked Apps Breakdown
            BlockedAppsBreakdownCard(
                apps = stats.topBlockedApps,
                totalAttempts = stats.totalWeeklyBlockedAttempts,
                onOpenFocusLock = onOpenFocusLock
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FocusMinutesBarChartCard(
    days: List<DailyFocusStat>,
    selectedIndex: Int,
    selectedDay: DailyFocusStat,
    onSelectDay: (Int) -> Unit,
    onOpenPlanner: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val maxMinutes = (days.maxOfOrNull { it.focusedMinutes } ?: 0).coerceAtLeast(30)
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val emptyBarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    val animatedFractions = days.mapIndexed { idx, day ->
        val raw = if (maxMinutes > 0) day.focusedMinutes.toFloat() / maxMinutes.toFloat() else 0f
        animateFloatAsState(
            targetValue = raw.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "bar_$idx"
        ).value
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("focus_bar_chart_card")
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Focus time (7 days)")
                    Text(
                        "${selectedDay.dayLabel} (${selectedDay.dateLabel}): ${selectedDay.focusedMinutes} min · ${selectedDay.completedBlocks} blocks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Y-axis scale labels
                Column(
                    modifier = Modifier
                        .height(150.dp)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("${maxMinutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${maxMinutes / 2}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Canvas bars
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp)
                        .pointerInput(days.size) {
                            detectTapGestures { offset ->
                                if (days.isNotEmpty()) {
                                    val slotWidth = size.width / days.size.toFloat()
                                    val tapped = (offset.x / slotWidth).toInt().coerceIn(0, days.lastIndex)
                                    if (tapped != selectedIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSelectDay(tapped)
                                }
                            }
                        }
                ) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (step in listOf(0f, 0.5f, 1f)) {
                        val y = size.height * (1f - step)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (step == 0f) null else dashEffect
                        )
                    }

                    val slotWidth = size.width / days.size.toFloat()
                    val barWidth = (slotWidth * 0.54f).coerceAtMost(36.dp.toPx())
                    val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())

                    days.forEachIndexed { index, day ->
                        val fraction = animatedFractions[index]
                        val x = index * slotWidth + (slotWidth - barWidth) / 2f
                        val isSelected = index == selectedIndex

                        if (day.focusedMinutes <= 0) {
                            val minCapHeight = 4.dp.toPx()
                            drawRoundRect(
                                color = if (isSelected) primaryColor else emptyBarColor,
                                topLeft = Offset(x, size.height - minCapHeight),
                                size = Size(barWidth, minCapHeight),
                                cornerRadius = cornerRadius
                            )
                        } else {
                            val barHeight = (size.height * fraction).coerceAtLeast(8.dp.toPx())
                            drawRoundRect(
                                color = if (isSelected) primaryColor else secondaryBarColor,
                                topLeft = Offset(x, size.height - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // X-axis day labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEachIndexed { index, day ->
                    val selected = index == selectedIndex
                    Text(
                        text = day.dayLabel.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (days.all { it.focusedMinutes == 0 }) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Complete blocks in Planner or Now to build your streak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onOpenPlanner) {
                        Text("Open Planner")
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedAttemptsLineChartCard(
    days: List<DailyFocusStat>,
    selectedIndex: Int,
    selectedDay: DailyFocusStat,
    onSelectDay: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val maxAttempts = (days.maxOfOrNull { it.blockedAttempts } ?: 0).coerceAtLeast(4)
    val accentColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    val animatedPoints = days.mapIndexed { idx, day ->
        val raw = if (maxAttempts > 0) day.blockedAttempts.toFloat() / maxAttempts.toFloat() else 0f
        animateFloatAsState(
            targetValue = raw.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "line_$idx"
        ).value
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("blocked_line_chart_card")
    ) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel("Blocked app redirects (7 days)")
            Text(
                "${selectedDay.dayLabel} (${selectedDay.dateLabel}): ${selectedDay.blockedAttempts} intercepted open${if (selectedDay.blockedAttempts == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(
                    modifier = Modifier
                        .height(130.dp)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("$maxAttempts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${maxAttempts / 2}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .pointerInput(days.size) {
                            detectTapGestures { offset ->
                                if (days.isNotEmpty()) {
                                    val slotWidth = size.width / days.size.toFloat()
                                    val tapped = (offset.x / slotWidth).toInt().coerceIn(0, days.lastIndex)
                                    if (tapped != selectedIndex) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    onSelectDay(tapped)
                                }
                            }
                        }
                ) {
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (step in listOf(0f, 0.5f, 1f)) {
                        val y = size.height * (1f - step)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (step == 0f) null else dashEffect
                        )
                    }

                    val slotWidth = size.width / days.size.toFloat()
                    val topPad = 8.dp.toPx()
                    val usableHeight = (size.height - topPad).coerceAtLeast(1f)

                    val coords = days.indices.map { i ->
                        val x = i * slotWidth + slotWidth / 2f
                        val y = size.height - (usableHeight * animatedPoints[i])
                        Offset(x, y)
                    }

                    if (coords.isNotEmpty()) {
                        val linePath = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 1 until coords.size) {
                                lineTo(coords[i].x, coords[i].y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(coords.last().x, size.height)
                            lineTo(coords.first().x, size.height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.28f),
                                    accentColor.copy(alpha = 0.02f)
                                )
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = accentColor,
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        coords.forEachIndexed { index, pt ->
                            val isSelected = index == selectedIndex
                            if (isSelected) {
                                drawCircle(
                                    color = accentColor.copy(alpha = 0.25f),
                                    radius = 10.dp.toPx(),
                                    center = pt
                                )
                            }
                            drawCircle(
                                color = accentColor,
                                radius = if (isSelected) 5.5.dp.toPx() else 3.5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = surfaceColor,
                                radius = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                days.forEachIndexed { index, day ->
                    val selected = index == selectedIndex
                    Text(
                        text = day.dayLabel.take(3),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedAppsBreakdownCard(
    apps: List<BlockedAppSummary>,
    totalAttempts: Int,
    onOpenFocusLock: () -> Unit
) {
    val maxAttempts = (apps.maxOfOrNull { it.attemptCount } ?: 1).coerceAtLeast(1)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Most blocked distracting apps")
                    Text(
                        if (totalAttempts > 0) "$totalAttempts total redirects this week"
                        else "No blocked app opens yet this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onOpenFocusLock) {
                    Icon(Icons.Filled.Shield, contentDescription = "Configure App Blocker")
                }
            }

            if (apps.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenFocusLock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configure Scheduled App Blocker")
                }
            } else {
                Spacer(Modifier.height(14.dp))
                apps.take(6).forEach { app ->
                    val fraction = (app.attemptCount.toFloat() / maxAttempts.toFloat()).coerceIn(0.08f, 1f)
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                app.appLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    "${app.attemptCount}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatMinutesBrief(totalMinutes: Int): String {
    if (totalMinutes < 60) return "${totalMinutes}m"
    val hrs = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (mins == 0) "${hrs}h" else "${hrs}h ${mins}m"
}
