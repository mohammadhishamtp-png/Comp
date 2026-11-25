package com.adhdcompanion.focus.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adhdcompanion.focus.navigation.FocusDestination

@Composable
fun FocusBottomBar(
    current: FocusDestination,
    onNavigate: (FocusDestination) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = current == FocusDestination.Now,
            onClick = { onNavigate(FocusDestination.Now) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Now") },
            label = { Text("Now", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_now")
        )
        NavigationBarItem(
            selected = current == FocusDestination.Stuck,
            onClick = { onNavigate(FocusDestination.Stuck) },
            icon = { Icon(Icons.Filled.Bolt, contentDescription = "Stuck") },
            label = { Text("Stuck", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_stuck")
        )
        NavigationBarItem(
            selected = current == FocusDestination.DoubtSolver,
            onClick = { onNavigate(FocusDestination.DoubtSolver) },
            icon = { Icon(Icons.Filled.Psychology, contentDescription = "Doubt Solver") },
            label = { Text("Doubts", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_doubts")
        )
        NavigationBarItem(
            selected = current == FocusDestination.Planner,
            onClick = { onNavigate(FocusDestination.Planner) },
            icon = { Icon(Icons.Filled.CalendarToday, contentDescription = "Planner") },
            label = { Text("Planner", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_planner")
        )
        NavigationBarItem(
            selected = current == FocusDestination.BrainDump,
            onClick = { onNavigate(FocusDestination.BrainDump) },
            icon = { Icon(Icons.Filled.Inbox, contentDescription = "Brain Dump") },
            label = { Text("Dump", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.testTag("nav_dump")
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
