package com.adhdcompanion.focus.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class DialMode { HOUR, MINUTE }

/**
 * Google Clock–inspired circular rotating analog dial picker for the daily
 * morning briefing alarm:
 * - Hour ring (1–12) + AM/PM segmented pill toggle
 * - Minute ring (0–59, labeled every 5 minutes)
 * - Smooth draggable pointer/dial with haptic feedback on value changes
 * - Interactive digital readout, on/off switch, next-alarm readout, and Test alarm (5s)
 */
@Composable
fun RotatingClockPicker(
    hour24: Int,
    minute: Int,
    alarmEnabled: Boolean,
    nextAlarmReadout: String,
    onTimeChanged: (hour24: Int, minute: Int) -> Unit,
    onToggleAlarmEnabled: (Boolean) -> Unit,
    onTestAlarmClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialMode by remember { mutableStateOf(DialMode.HOUR) }
    var isDragging by remember { mutableStateOf(false) }

    val isPm = hour24 >= 12
    val hour12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }

    val currentHour24 by rememberUpdatedState(hour24)
    val currentMinute by rememberUpdatedState(minute)
    val currentIsPm by rememberUpdatedState(isPm)
    val currentMode by rememberUpdatedState(dialMode)
    val currentOnTimeChanged by rememberUpdatedState(onTimeChanged)

    val targetClockDeg = when (dialMode) {
        DialMode.HOUR -> (hour12 % 12) * 30f
        DialMode.MINUTE -> minute * 6f
    }

    val animatedDeg = remember { Animatable(targetClockDeg) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(targetClockDeg, isDragging) {
        val current = animatedDeg.value
        val delta = shortestAngleDelta(current, targetClockDeg)
        val destination = current + delta
        if (isDragging) {
            animatedDeg.snapTo(destination)
        } else {
            animatedDeg.animateTo(
                targetValue = destination,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Morning briefing alarm",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        nextAlarmReadout,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (alarmEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = alarmEnabled,
                    onCheckedChange = onToggleAlarmEnabled,
                    modifier = Modifier.testTag("alarm_enabled_switch")
                )
            }

            Spacer(Modifier.height(18.dp))

            // Digital time readout + AM/PM pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                DigitalSegment(
                    text = "%d".format(Locale.getDefault(), hour12),
                    selected = dialMode == DialMode.HOUR,
                    onClick = { dialMode = DialMode.HOUR },
                    modifier = Modifier.testTag("clock_hour_segment")
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
                DigitalSegment(
                    text = "%02d".format(Locale.getDefault(), minute),
                    selected = dialMode == DialMode.MINUTE,
                    onClick = { dialMode = DialMode.MINUTE },
                    modifier = Modifier.testTag("clock_minute_segment")
                )

                Spacer(Modifier.width(14.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AmPmChip(
                        label = "AM",
                        selected = !isPm,
                        onClick = {
                            if (isPm) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTimeChanged(hour24 - 12, minute)
                            }
                        },
                        modifier = Modifier.testTag("clock_am_button")
                    )
                    AmPmChip(
                        label = "PM",
                        selected = isPm,
                        onClick = {
                            if (!isPm) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnTimeChanged(hour24 + 12, minute)
                            }
                        },
                        modifier = Modifier.testTag("clock_pm_button")
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Circular rotating analog dial
            val primaryColor = MaterialTheme.colorScheme.primary
            val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
            val dialBgColor = MaterialTheme.colorScheme.surface
            val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            val labelColor = MaterialTheme.colorScheme.onSurface

            BoxWithConstraints(
                modifier = Modifier
                    .size(252.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isDragging = true
                            val center = Offset(size.width / 2f, size.height / 2f)

                            fun applyPointerPosition(pos: Offset) {
                                val dx = pos.x - center.x
                                val dy = pos.y - center.y
                                val deg = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 450.0) % 360.0).toFloat()

                                if (currentMode == DialMode.HOUR) {
                                    val snapped = (((deg + 15f) / 30f).toInt() % 12).let { if (it == 0) 12 else it }
                                    val newHour24 = when {
                                        currentIsPm && snapped == 12 -> 12
                                        !currentIsPm && snapped == 12 -> 0
                                        currentIsPm -> snapped + 12
                                        else -> snapped
                                    }
                                    if (newHour24 != currentHour24) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentOnTimeChanged(newHour24, currentMinute)
                                    }
                                } else {
                                    val snappedMin = ((deg + 3f) / 6f).toInt() % 60
                                    if (snappedMin != currentMinute) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentOnTimeChanged(currentHour24, snappedMin)
                                    }
                                }
                            }

                            applyPointerPosition(down.position)
                            val dragged = drag(down.id) { change ->
                                change.consume()
                                applyPointerPosition(change.position)
                            }
                            isDragging = false
                            if (dragged && currentMode == DialMode.HOUR) {
                                scope.launch { dialMode = DialMode.MINUTE }
                            } else if (!dragged && currentMode == DialMode.HOUR) {
                                dialMode = DialMode.MINUTE
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current
                val boxSizePx = with(density) { maxWidth.toPx() }
                val radiusPx = boxSizePx / 2f
                val numberRadiusPx = radiusPx - with(density) { 30.dp.toPx() }
                val puckRadiusPx = with(density) { 21.dp.toPx() }
                val labelBoxSize = 40.dp
                val labelHalfPx = with(density) { (labelBoxSize / 2).toPx() }

                val angleRad = Math.toRadians((animatedDeg.value - 90f).toDouble())
                val puckCenter = Offset(
                    x = radiusPx + (numberRadiusPx * cos(angleRad)).toFloat(),
                    y = radiusPx + (numberRadiusPx * sin(angleRad)).toFloat()
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)

                    // Dial face
                    drawCircle(color = dialBgColor, radius = radiusPx, center = center)

                    // Subtle ticks around perimeter
                    val totalTicks = if (dialMode == DialMode.MINUTE) 60 else 12
                    for (i in 0 until totalTicks) {
                        val tickDeg = i * (360f / totalTicks)
                        val tickRad = Math.toRadians((tickDeg - 90f).toDouble())
                        val outerR = radiusPx - 6.dp.toPx()
                        val innerR = if (i % 5 == 0 || dialMode == DialMode.HOUR) outerR - 6.dp.toPx() else outerR - 3.dp.toPx()
                        drawLine(
                            color = tickColor,
                            start = Offset(
                                center.x + (innerR * cos(tickRad)).toFloat(),
                                center.y + (innerR * sin(tickRad)).toFloat()
                            ),
                            end = Offset(
                                center.x + (outerR * cos(tickRad)).toFloat(),
                                center.y + (outerR * sin(tickRad)).toFloat()
                            ),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Rotating hand
                    drawLine(
                        color = primaryColor,
                        start = center,
                        end = puckCenter,
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Center pivot dot
                    drawCircle(
                        color = primaryColor,
                        radius = 4.5.dp.toPx(),
                        center = center
                    )

                    // Selector puck
                    drawCircle(
                        color = primaryColor,
                        radius = puckRadiusPx,
                        center = puckCenter
                    )

                    // Small inner dot when selecting a non-5-minute tick
                    if (dialMode == DialMode.MINUTE && minute % 5 != 0) {
                        drawCircle(
                            color = onPrimaryColor,
                            radius = 3.dp.toPx(),
                            center = puckCenter
                        )
                    }
                }

                // Ring numbers
                val items = if (dialMode == DialMode.HOUR) {
                    (0 until 12).map { idx ->
                        val h = if (idx == 0) 12 else idx
                        h to h.toString()
                    }
                } else {
                    (0 until 12).map { idx ->
                        val m = idx * 5
                        m to "%02d".format(Locale.getDefault(), m)
                    }
                }

                items.forEachIndexed { index, (value, label) ->
                    val itemAngleRad = (index * 30.0 - 90.0) * PI / 180.0
                    val xPx = radiusPx + numberRadiusPx * cos(itemAngleRad) - labelHalfPx
                    val yPx = radiusPx + numberRadiusPx * sin(itemAngleRad) - labelHalfPx
                    val isSelected = if (dialMode == DialMode.HOUR) {
                        value == hour12
                    } else {
                        value == minute
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                            .size(labelBoxSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) onPrimaryColor else labelColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (dialMode == DialMode.HOUR) "Drag dial for hour" else "Drag dial for minute",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onTestAlarmClick,
                    modifier = Modifier.testTag("test_alarm_button")
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Test alarm (5s)")
                }
            }
        }
    }
}

@Composable
private fun DigitalSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun AmPmChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shortestAngleDelta(fromDeg: Float, toDeg: Float): Float {
    var diff = (toDeg - fromDeg) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return diff
}
