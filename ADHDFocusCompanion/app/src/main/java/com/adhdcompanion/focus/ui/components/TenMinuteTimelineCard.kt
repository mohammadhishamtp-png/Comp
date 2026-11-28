package com.adhdcompanion.focus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhdcompanion.focus.model.TimelineTenMinCell
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import java.util.Locale

/**
 * YPT-inspired 24-hour x 6-column (10-minute slot) study timeline grid.
 * Each row is 1 hour (00..23), divided into six 10-minute color-coded blocks.
 */
@Composable
fun TenMinuteTimelineCard(
    cells: List<TimelineTenMinCell>,
    modifier: Modifier = Modifier
) {
    var showFull24Hours by remember { mutableStateOf(false) }
    var inspectedCell by remember { mutableStateOf<TimelineTenMinCell?>(null) }

    val byHour = remember(cells) { cells.groupBy { it.hourOfDay } }
    val visibleHours = remember(showFull24Hours, cells) {
        if (showFull24Hours) {
            (0..23).toList()
        } else {
            val activeHours = cells.filter { it.minutesCovered > 0 }.map { it.hourOfDay }
            val minActive = activeHours.minOrNull() ?: 6
            val startHour = minOf(6, minActive)
            (startHour..23).toList()
        }
    }

    val totalStudiedMins = remember(cells) { cells.sumOf { it.minutesCovered } }

    AmoledCard(
        containerColor = AmoledSurface,
        borderColor = AmoledOutlineSubtle,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("10-Minute Timeline")
                    Text(
                        text = if (totalStudiedMins > 0) {
                            "${formatMinutesReadable(totalStudiedMins)} logged across today's blocks"
                        } else {
                            "Start a timer to paint 10-minute focus blocks"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoledMuted
                    )
                }
                TextButton(onClick = { showFull24Hours = !showFull24Hours }) {
                    Text(
                        text = if (showFull24Hours) "6AM–12AM" else "24h View",
                        style = MaterialTheme.typography.labelMedium,
                        color = FocusEmerald
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(42.dp))
                listOf("00", "10", "20", "30", "40", "50").forEach { minLabel ->
                    Text(
                        text = ":$minLabel",
                        style = TabularStatStyle.copy(fontSize = 10.sp),
                        color = OnAmoledSubtle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                visibleHours.forEach { hour ->
                    val slots = byHour[hour].orEmpty().sortedBy { it.slotIndex }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%02d:00".format(Locale.getDefault(), hour),
                            style = TabularStatStyle.copy(fontSize = 10.sp),
                            color = OnAmoledMuted,
                            modifier = Modifier.width(42.dp)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (slotIdx in 0 until 6) {
                                val cell = slots.firstOrNull { it.slotIndex == slotIdx }
                                    ?: TimelineTenMinCell(hourOfDay = hour, slotIndex = slotIdx)
                                val hasStudy = cell.minutesCovered > 0 && cell.colorHex != null
                                val fillColor = if (hasStudy) {
                                    val alpha = (0.45f + (cell.minutesCovered / 10f) * 0.55f).coerceIn(0.45f, 1f)
                                    Color(cell.colorHex!!).copy(alpha = alpha)
                                } else {
                                    AmoledSurfaceElevated
                                }
                                val isInspected = inspectedCell?.hourOfDay == hour && inspectedCell?.slotIndex == slotIdx

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(17.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(fillColor)
                                        .border(
                                            width = if (isInspected) 1.5.dp else 0.5.dp,
                                            color = if (isInspected) OnAmoled else AmoledOutlineSubtle,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { inspectedCell = cell }
                                )
                            }
                        }
                    }
                }
            }

            inspectedCell?.let { cell ->
                Spacer(Modifier.height(10.dp))
                val startMin = cell.slotIndex * 10
                val endMin = startMin + 10
                val slotLabel = "%02d:%02d–%02d:%02d".format(
                    Locale.getDefault(),
                    cell.hourOfDay,
                    startMin,
                    if (endMin == 60) (cell.hourOfDay + 1) % 24 else cell.hourOfDay,
                    endMin % 60
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AmoledSurfaceElevated)
                        .border(0.75.dp, AmoledOutline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (cell.subjectName != null) {
                            "$slotLabel · ${cell.subjectName} (${cell.minutesCovered}m focused)"
                        } else {
                            "$slotLabel · No study logged"
                        },
                        style = TabularStatStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = OnAmoled
                    )
                }
            }
        }
    }
}
