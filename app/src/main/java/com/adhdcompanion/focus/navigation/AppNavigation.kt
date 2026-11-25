package com.adhdcompanion.focus.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.ui.components.FocusBottomBar
import com.adhdcompanion.focus.ui.screens.BrainDumpScreen
import com.adhdcompanion.focus.ui.screens.DoubtSolverScreen
import com.adhdcompanion.focus.ui.screens.FocusLockScreen
import com.adhdcompanion.focus.ui.screens.NowBriefingScreen
import com.adhdcompanion.focus.ui.screens.PlannerScreen
import com.adhdcompanion.focus.ui.screens.SettingsScreen
import com.adhdcompanion.focus.ui.screens.StuckScreen

sealed class FocusDestination(val route: String) {
    data object Now : FocusDestination("now")
    data object Stuck : FocusDestination("stuck")
    data object DoubtSolver : FocusDestination("doubt_solver")
    data object Planner : FocusDestination("planner")
    data object BrainDump : FocusDestination("brain_dump")
    data object Settings : FocusDestination("settings")
    data object FocusLock : FocusDestination("focus_lock")
}

data class BlockedRedirectPrompt(
    val packageName: String,
    val appLabel: String,
    val reason: String
)

@Composable
fun AppNavigation(
    container: AppContainer,
    autoSpeakTriggerId: Int = 0,
    navigateToNowTriggerId: Int = 0,
    navigateToFocusLockTriggerId: Int = 0,
    blockedPrompt: BlockedRedirectPrompt? = null,
    onDismissBlockedPrompt: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    val showBottomBar = currentRoute != FocusDestination.Settings.route &&
        currentRoute != FocusDestination.FocusLock.route

    val currentTab = when (currentRoute) {
        FocusDestination.Stuck.route -> FocusDestination.Stuck
        FocusDestination.DoubtSolver.route -> FocusDestination.DoubtSolver
        FocusDestination.Planner.route -> FocusDestination.Planner
        FocusDestination.BrainDump.route -> FocusDestination.BrainDump
        else -> FocusDestination.Now
    }

    LaunchedEffect(navigateToNowTriggerId) {
        if (navigateToNowTriggerId > 0) {
            navController.navigate(FocusDestination.Now.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    LaunchedEffect(navigateToFocusLockTriggerId) {
        if (navigateToFocusLockTriggerId > 0) {
            navController.navigate(FocusDestination.FocusLock.route) {
                launchSingleTop = true
            }
        }
    }

    if (blockedPrompt != null) {
        FocusLockShieldScreen(
            container = container,
            prompt = blockedPrompt,
            onReturnToFocus = {
                onDismissBlockedPrompt()
                navController.navigate(FocusDestination.Now.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onOpenStuck = {
                onDismissBlockedPrompt()
                navController.navigate(FocusDestination.Stuck.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onOpenLockSettings = {
                onDismissBlockedPrompt()
                navController.navigate(FocusDestination.FocusLock.route) {
                    launchSingleTop = true
                }
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                FocusBottomBar(current = currentTab) { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = FocusDestination.Now.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(FocusDestination.Now.route) {
                NowBriefingScreen(
                    container = container,
                    autoSpeakTriggerId = autoSpeakTriggerId,
                    onOpenSettings = { navController.navigate(FocusDestination.Settings.route) },
                    onOpenFocusLock = { navController.navigate(FocusDestination.FocusLock.route) },
                    onOpenPlanner = {
                        navController.navigate(FocusDestination.Planner.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(FocusDestination.Stuck.route) {
                StuckScreen(container = container)
            }
            composable(FocusDestination.DoubtSolver.route) {
                DoubtSolverScreen(container = container)
            }
            composable(FocusDestination.Planner.route) {
                PlannerScreen(container = container)
            }
            composable(FocusDestination.BrainDump.route) {
                BrainDumpScreen(container = container)
            }
            composable(FocusDestination.Settings.route) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenFocusLock = { navController.navigate(FocusDestination.FocusLock.route) }
                )
            }
            composable(FocusDestination.FocusLock.route) {
                FocusLockScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun FocusLockShieldScreen(
    container: AppContainer,
    prompt: BlockedRedirectPrompt,
    onReturnToFocus: () -> Unit,
    onOpenStuck: () -> Unit,
    onOpenLockSettings: () -> Unit
) {
    val eval = container.focusLockManager.evaluateNow()
    val endText = eval.activeWindow?.let { FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay) }
        ?: "your scheduled end time"

    val isTamper = prompt.reason == FocusBlockerAccessibilityService.REASON_TAMPER

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (isTamper) "Lock rules are protected" else "Paused ${prompt.appLabel}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isTamper) {
                    "App Info, Uninstall, and Accessibility Settings are guarded during your focus window (active until $endText). Start the cooldown timer inside Lock Settings if you genuinely need to adjust rules."
                } else {
                    "Your scheduled focus window (${eval.activeWindow?.name ?: "Focus"}) is active until $endText. Take one slow breath — no shame, just a gentle redirect."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "EASY NEXT MOVE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Go back to your current block's tiny first step, or tap 'I'm stuck' if friction feels high right now.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onReturnToFocus, modifier = Modifier.fillMaxWidth()) {
                Text("Return to Focus")
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onOpenStuck, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("I'm stuck")
                }
                OutlinedButton(onClick = onOpenLockSettings, modifier = Modifier.weight(1f)) {
                    Text("Lock rules")
                }
            }
        }
    }
}
