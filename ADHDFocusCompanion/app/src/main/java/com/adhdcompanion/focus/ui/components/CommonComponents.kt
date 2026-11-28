package com.adhdcompanion.focus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhdcompanion.focus.navigation.FocusDestination
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import java.util.Locale

private data class PrimaryNavItem(
    val destination: FocusDestination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val PrimaryNavItems = listOf(
    PrimaryNavItem(FocusDestination.Timer, "Focus", Icons.Filled.Timer, Icons.Outlined.Timer, "nav_timer"),
    PrimaryNavItem(FocusDestination.Planner, "Plan", Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday, "nav_planner"),
    PrimaryNavItem(FocusDestination.Stats, "Insights", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_stats"),
    PrimaryNavItem(FocusDestination.Groups, "Hall", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_groups"),
    PrimaryNavItem(FocusDestination.FocusLock, "Shield", Icons.Filled.Shield, Icons.Outlined.Shield, "nav_shield")
)

/**
 * Minimalist true-black OLED bottom bar without bulky Material indicator ovals.
 */
@Composable
fun FocusBottomBar(
    current: FocusDestination,
    onNavigate: (FocusDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmoledBlack)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryNavItems.forEach { item ->
                val selected = current == item.destination
                val tint = if (selected) FocusEmerald else OnAmoledSubtle
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onNavigate(item.destination) }
                        )
                        .testTag(item.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) FocusEmerald.copy(alpha = 0.14f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) OnAmoled else OnAmoledSubtle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FocusNavigationRail(
    current: FocusDestination,
    onNavigate: (FocusDestination) -> Unit,
    headerContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            headerContent?.invoke(this)
            Spacer(Modifier.weight(0.15f))
            PrimaryNavItems.forEach { item ->
                val selected = current == item.destination
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AmoledSurfaceHigh else Color.Transparent)
                        .clickable { onNavigate(item.destination) }
                        .padding(vertical = 10.dp)
                        .testTag("${item.testTag}_rail"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (selected) FocusEmerald else OnAmoledSubtle,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) OnAmoled else OnAmoledMuted,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
        VerticalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
    }
}

@Composable
fun AmoledCard(
    modifier: Modifier = Modifier,
    containerColor: Color = AmoledSurface,
    borderColor: Color = AmoledOutlineSubtle,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(0.75.dp, borderColor),
        modifier = modifier
    ) {
        Column(content = content)
    }
}

@Composable
fun PremiumGlowCard(
    modifier: Modifier = Modifier,
    accentColor: Color = FocusEmerald,
    borderColor: Color = accentColor.copy(alpha = 0.35f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AmoledSurface,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            AmoledSurface.copy(alpha = 0.96f),
                            AmoledSurface
                        )
                    )
                )
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun SegmentedModeBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = FocusEmerald,
    testTagPrefix: String = "seg"
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AmoledSurface,
        border = BorderStroke(0.75.dp, AmoledOutlineSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            options.forEachIndexed { idx, label ->
                val selected = idx == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) AmoledSurfaceHigh else Color.Transparent)
                        .clickable(enabled = enabled) { onSelect(idx) }
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag("${testTagPrefix}_$idx"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) accentColor else OnAmoledMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectPill(
    name: String,
    colorHex: Long,
    selected: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(colorHex)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else AmoledSurface,
        border = BorderStroke(0.85.dp, if (selected) accent else AmoledOutline),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) OnAmoled else OnAmoledMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = TabularStatStyle.copy(fontSize = 10.sp),
                        color = if (selected) accent else OnAmoledSubtle
                    )
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.1.sp,
        color = OnAmoledMuted,
        modifier = modifier.padding(bottom = 2.dp)
    )
}

fun formatHms(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return "%02d:%02d:%02d".format(Locale.getDefault(), hrs, mins, secs)
}

fun formatCompactTimer(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hrs = safe / 3600
    val mins = (safe % 3600) / 60
    val secs = safe % 60
    return if (hrs > 0) {
        "%02d:%02d:%02d".format(Locale.getDefault(), hrs, mins, secs)
    } else {
        "%02d:%02d".format(Locale.getDefault(), mins, secs)
    }
}

fun formatMinutesReadable(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hrs = safe / 60
    val mins = safe % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        else -> "${mins}m"
    }
}
