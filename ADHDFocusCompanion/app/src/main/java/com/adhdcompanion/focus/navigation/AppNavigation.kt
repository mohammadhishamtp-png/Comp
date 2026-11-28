package com.adhdcompanion.focus.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.FocusBottomBar
import com.adhdcompanion.focus.ui.components.FocusNavigationRail
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.components.TenMinuteTimelineCard
import com.adhdcompanion.focus.ui.components.formatMinutesReadable
import com.adhdcompanion.focus.ui.screens.AuthScreen
import com.adhdcompanion.focus.ui.screens.BrainDumpScreen
import com.adhdcompanion.focus.ui.screens.DashboardScreen
import com.adhdcompanion.focus.ui.screens.DoubtSolverScreen
import com.adhdcompanion.focus.ui.screens.FocusLockScreen
import com.adhdcompanion.focus.ui.screens.GroupsScreen
import com.adhdcompanion.focus.ui.screens.NowBriefingScreen
import com.adhdcompanion.focus.ui.screens.PasswordExitDialog
import com.adhdcompanion.focus.ui.screens.PlannerScreen
import com.adhdcompanion.focus.ui.screens.SettingsScreen
import com.adhdcompanion.focus.ui.screens.StatsScreen
import com.adhdcompanion.focus.ui.screens.StuckScreen
import com.adhdcompanion.focus.ui.screens.TimerScreen
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceHigh
import com.adhdcompanion.focus.ui.theme.FocusCyan
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.TabularStatStyle
import com.adhdcompanion.focus.ui.viewmodel.StudyTimerViewModel
import com.adhdcompanion.focus.ui.viewmodel.ViewModelFactories
import kotlinx.coroutines.delay

sealed class FocusDestination(val route: String) {
    // 5 Primary Study Tabs
    data object Timer : FocusDestination("timer")
    data object Planner : FocusDestination("planner")
    data object Stats : FocusDestination("stats")
    data object Groups : FocusDestination("groups")
    data object FocusLock : FocusDestination("focus_lock")

    // Secondary / ADHD & AI Companion Screens
    data object Now : FocusDestination("now")
    data object Stuck : FocusDestination("stuck")
    data object DoubtSolver : FocusDestination("doubt_solver")
    data object BrainDump : FocusDestination("brain_dump")
    data object Settings : FocusDestination("settings")
    data object Dashboard : FocusDestination("dashboard")
}

data class BlockedRedirectPrompt(
    val packageName: String,
    val appLabel: String,
    val reason: String
)

private val PrimaryRoutes = setOf(
    FocusDestination.Timer.route,
    FocusDestination.Planner.route,
    FocusDestination.Stats.route,
    FocusDestination.Groups.route,
    FocusDestination.FocusLock.route
)

@Composable
fun AppNavigation(
    container: AppContainer,
    autoSpeakTriggerId: Int = 0,
    navigateToNowTriggerId: Int = 0,
    navigateToFocusLockTriggerId: Int = 0,
    openPasswordExitTriggerId: Int = 0,
    blockedPrompt: BlockedRedirectPrompt? = null,
    onDismissBlockedPrompt: () -> Unit = {}
) {
    val authStateFlow = remember(container.authManager) { container.authManager.authStateFlow() }
    val currentUser by authStateFlow.collectAsState(initial = container.authManager.currentUser)

    val user = currentUser
    if (user == null) {
        if (blockedPrompt != null) {
            FocusLockShieldScreen(
                container = container,
                prompt = blockedPrompt,
                openPasswordExitTriggerId = openPasswordExitTriggerId,
                onReturnToFocus = onDismissBlockedPrompt,
                onOpenStuck = onDismissBlockedPrompt,
                onOpenLockSettings = onDismissBlockedPrompt,
                onPasswordUnlocked = onDismissBlockedPrompt
            )
            return
        }
        AuthScreen(authManager = container.authManager)
        return
    }

    key(user.uid) {
        LaunchedEffect(user.uid) {
            container.studyRepository.syncFromCloudIfNeeded(user.uid)
        }

        AuthenticatedAppShell(
            uid = user.uid,
            container = container,
            autoSpeakTriggerId = autoSpeakTriggerId,
            navigateToNowTriggerId = navigateToNowTriggerId,
            navigateToFocusLockTriggerId = navigateToFocusLockTriggerId,
            openPasswordExitTriggerId = openPasswordExitTriggerId,
            blockedPrompt = blockedPrompt,
            onDismissBlockedPrompt = onDismissBlockedPrompt
        )
    }
}

@Composable
private fun AuthenticatedAppShell(
    uid: String,
    container: AppContainer,
    autoSpeakTriggerId: Int,
    navigateToNowTriggerId: Int,
    navigateToFocusLockTriggerId: Int,
    openPasswordExitTriggerId: Int,
    blockedPrompt: BlockedRedirectPrompt?,
    onDismissBlockedPrompt: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    // Shared timer ViewModel scoped cleanly per authenticated uid
    val sharedTimerViewModel = viewModel<StudyTimerViewModel>(
        key = uid,
        factory = ViewModelFactories.studyTimer(container)
    )

    val isPrimaryTab = currentRoute == null || currentRoute in PrimaryRoutes

    val currentTab = when (currentRoute) {
        FocusDestination.Planner.route -> FocusDestination.Planner
        FocusDestination.Stats.route -> FocusDestination.Stats
        FocusDestination.Groups.route -> FocusDestination.Groups
        FocusDestination.FocusLock.route -> FocusDestination.FocusLock
        else -> FocusDestination.Timer
    }

    val navigatePrimary: (FocusDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(navigateToNowTriggerId) {
        if (navigateToNowTriggerId > 0) {
            navController.navigate(FocusDestination.Now.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(navigateToFocusLockTriggerId) {
        if (navigateToFocusLockTriggerId > 0) {
            navigatePrimary(FocusDestination.FocusLock)
        }
    }

    if (blockedPrompt != null) {
        FocusLockShieldScreen(
            container = container,
            prompt = blockedPrompt,
            openPasswordExitTriggerId = openPasswordExitTriggerId,
            onReturnToFocus = {
                onDismissBlockedPrompt()
                navigatePrimary(FocusDestination.Timer)
            },
            onOpenStuck = {
                onDismissBlockedPrompt()
                navController.navigate(FocusDestination.Stuck.route) {
                    launchSingleTop = true
                }
            },
            onOpenLockSettings = {
                onDismissBlockedPrompt()
                navigatePrimary(FocusDestination.FocusLock)
            },
            onPasswordUnlocked = onDismissBlockedPrompt
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        val useNavigationRail = maxWidth >= 600.dp
        val isWideTablet = maxWidth >= 840.dp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            if (useNavigationRail && isPrimaryTab) {
                FocusNavigationRail(
                    current = currentTab,
                    onNavigate = navigatePrimary
                )
            }

            Scaffold(
                containerColor = AmoledBlack,
                modifier = Modifier.weight(1f),
                bottomBar = {
                    if (!useNavigationRail && isPrimaryTab) {
                        FocusBottomBar(
                            current = currentTab,
                            onNavigate = navigatePrimary
                        )
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = FocusDestination.Timer.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    composable(FocusDestination.Timer.route) {
                        val timerState by sharedTimerViewModel.uiState.collectAsState()
                        var tabletPaneIndex by remember { mutableIntStateOf(0) }

                        if (isWideTablet && !timerState.isFullScreenFocus) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(AmoledBlack)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.52f)
                                        .fillMaxHeight()
                                ) {
                                    TimerScreen(
                                        container = container,
                                        timerViewModel = sharedTimerViewModel,
                                        onOpenNowBriefing = { navController.navigate(FocusDestination.Now.route) },
                                        onOpenDoubtSolver = { tabletPaneIndex = 1 },
                                        onOpenStuck = { navController.navigate(FocusDestination.Stuck.route) },
                                        onOpenBrainDump = { navController.navigate(FocusDestination.BrainDump.route) },
                                        onOpenSettings = { navController.navigate(FocusDestination.Settings.route) },
                                        onOpenShield = { navigatePrimary(FocusDestination.FocusLock) }
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(0.75.dp)
                                        .fillMaxHeight()
                                        .background(AmoledOutlineSubtle)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(0.48f)
                                        .fillMaxHeight()
                                        .background(AmoledBlack)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AmoledSurface,
                                        border = BorderStroke(0.75.dp, AmoledOutlineSubtle),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(if (tabletPaneIndex == 0) AmoledSurfaceHigh else Color.Transparent)
                                                    .clickable { tabletPaneIndex = 0 }
                                                    .padding(vertical = 8.dp)
                                                    .testTag("tablet_pane_timeline_tab"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "10-Min Timeline",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (tabletPaneIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (tabletPaneIndex == 0) FocusEmerald else OnAmoledMuted
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(if (tabletPaneIndex == 1) AmoledSurfaceHigh else Color.Transparent)
                                                    .clickable { tabletPaneIndex = 1 }
                                                    .padding(vertical = 8.dp)
                                                    .testTag("tablet_pane_doubt_tab"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "AI Doubt Solver",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (tabletPaneIndex == 1) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (tabletPaneIndex == 1) FocusEmerald else OnAmoledMuted
                                                )
                                            }
                                        }
                                    }
                                    if (tabletPaneIndex == 0) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            TenMinuteTimelineCard(cells = timerState.timelineCells)
                                            Spacer(Modifier.height(14.dp))
                                            AmoledCard(
                                                containerColor = AmoledSurface,
                                                borderColor = AmoledOutlineSubtle,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(Modifier.padding(16.dp)) {
                                                    SectionLabel("Today's Subject Targets")
                                                    Spacer(Modifier.height(6.dp))
                                                    timerState.subjects.forEach { subject ->
                                                        val mins = timerState.subjectMinutesToday[subject.id] ?: 0
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = subject.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = OnAmoled
                                                            )
                                                            Text(
                                                                text = "${formatMinutesReadable(mins)} / ${formatMinutesReadable(subject.dailyTargetMinutes)}",
                                                                style = TabularStatStyle.copy(fontSize = 12.sp),
                                                                color = OnAmoledMuted
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(20.dp))
                                        }
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            DoubtSolverScreen(container = container)
                                        }
                                    }
                                }
                            }
                        } else {
                            TimerScreen(
                                container = container,
                                timerViewModel = sharedTimerViewModel,
                                onOpenNowBriefing = { navController.navigate(FocusDestination.Now.route) },
                                onOpenDoubtSolver = { navController.navigate(FocusDestination.DoubtSolver.route) },
                                onOpenStuck = { navController.navigate(FocusDestination.Stuck.route) },
                                onOpenBrainDump = { navController.navigate(FocusDestination.BrainDump.route) },
                                onOpenSettings = { navController.navigate(FocusDestination.Settings.route) },
                                onOpenShield = { navigatePrimary(FocusDestination.FocusLock) }
                            )
                        }
                    }

                    composable(FocusDestination.Planner.route) {
                        PlannerScreen(
                            container = container,
                            onStartFocusForSubject = { subjectId, subjectName ->
                                sharedTimerViewModel.focusSubjectIfIdle(subjectId, subjectName)
                                navigatePrimary(FocusDestination.Timer)
                            }
                        )
                    }

                    composable(FocusDestination.Stats.route) {
                        StatsScreen(
                            container = container,
                            onOpenShield = { navigatePrimary(FocusDestination.FocusLock) }
                        )
                    }

                    composable(FocusDestination.Groups.route) {
                        GroupsScreen(container = container)
                    }

                    composable(FocusDestination.FocusLock.route) {
                        FocusLockScreen(
                            container = container,
                            onBack = null
                        )
                    }

                    composable(FocusDestination.Now.route) {
                        SecondaryScreenWrapper(
                            title = "Now Briefing & Alarms",
                            onBack = { navController.popBackStack() }
                        ) {
                            NowBriefingScreen(
                                container = container,
                                autoSpeakTriggerId = autoSpeakTriggerId,
                                onOpenSettings = { navController.navigate(FocusDestination.Settings.route) },
                                onOpenFocusLock = { navigatePrimary(FocusDestination.FocusLock) },
                                onOpenDashboard = { navigatePrimary(FocusDestination.Stats) },
                                onOpenPlanner = { navigatePrimary(FocusDestination.Planner) }
                            )
                        }
                    }

                    composable(FocusDestination.Stuck.route) {
                        SecondaryScreenWrapper(
                            title = "I'm Stuck Supporter",
                            onBack = { navController.popBackStack() }
                        ) {
                            StuckScreen(container = container)
                        }
                    }

                    composable(FocusDestination.DoubtSolver.route) {
                        SecondaryScreenWrapper(
                            title = "Hybrid AI Doubt Solver",
                            onBack = { navController.popBackStack() }
                        ) {
                            DoubtSolverScreen(container = container)
                        }
                    }

                    composable(FocusDestination.BrainDump.route) {
                        SecondaryScreenWrapper(
                            title = "Quick Brain Dump",
                            onBack = { navController.popBackStack() }
                        ) {
                            BrainDumpScreen(container = container)
                        }
                    }

                    composable(FocusDestination.Settings.route) {
                        SettingsScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenFocusLock = { navigatePrimary(FocusDestination.FocusLock) }
                        )
                    }

                    composable(FocusDestination.Dashboard.route) {
                        DashboardScreen(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenPlanner = { navigatePrimary(FocusDestination.Planner) },
                            onOpenFocusLock = { navigatePrimary(FocusDestination.FocusLock) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryScreenWrapper(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AmoledBlack,
                titleContentColor = OnAmoled,
                navigationIconContentColor = OnAmoled
            ),
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("secondary_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun FocusLockShieldScreen(
    container: AppContainer,
    prompt: BlockedRedirectPrompt,
    openPasswordExitTriggerId: Int,
    onReturnToFocus: () -> Unit,
    onOpenStuck: () -> Unit,
    onOpenLockSettings: () -> Unit,
    onPasswordUnlocked: () -> Unit
) {
    val config by container.focusLockManager.config.collectAsState()
    val eval = container.focusLockManager.evaluateNow()
    val endText = eval.activeWindow?.let { FocusLockManager.formatMinuteOfDay(it.endMinuteOfDay) }
        ?: "your scheduled end time"

    val isTamper = prompt.reason == FocusBlockerAccessibilityService.REASON_TAMPER
    var showPasswordExitDialog by remember { mutableStateOf(false) }

    // Regain-style 3-second mindful breathing countdown
    var breathSecondsRemaining by remember(prompt.packageName) { mutableIntStateOf(3) }
    LaunchedEffect(prompt.packageName) {
        while (breathSecondsRemaining > 0) {
            delay(1000L)
            breathSecondsRemaining -= 1
        }
    }

    val breathScale by rememberInfiniteTransition(label = "mindful_breath").animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    LaunchedEffect(openPasswordExitTriggerId) {
        if (openPasswordExitTriggerId > 0 && config.hasExitPassword) {
            showPasswordExitDialog = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AmoledBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Regain Mindful Breathing Halo
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val outerRadius = size.minDimension / 2f
                    drawCircle(
                        color = AmoledOutlineSubtle,
                        radius = outerRadius - 3.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = FocusEmerald.copy(alpha = 0.14f),
                        radius = (outerRadius - 8.dp.toPx()) * breathScale
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(FocusEmerald, FocusCyan, FocusEmerald)),
                        startAngle = -90f,
                        sweepAngle = if (breathSecondsRemaining > 0) {
                            ((3 - breathSecondsRemaining) / 3f) * 360f + 45f
                        } else {
                            360f
                        },
                        useCenter = false,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = FocusEmerald,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (breathSecondsRemaining > 0) {
                    "TAKE ONE SLOW BREATH · ${breathSecondsRemaining}S"
                } else {
                    "MINDFUL REDIRECT READY"
                },
                style = TabularStatStyle.copy(fontSize = 11.sp),
                color = FocusEmerald
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isTamper) "Lock rules are protected" else "Paused ${prompt.appLabel}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = OnAmoled,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isTamper) {
                    "App Info, Uninstall, and Accessibility Settings are guarded during your focus window (active until $endText). Start the waiting timer inside Focus Shield if you genuinely need to adjust rules."
                } else {
                    "Your focus shield (${eval.activeWindow?.name ?: "Study Lock"}) is active until $endText. Pause for one breath — no shame, just protect your focus."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OnAmoledMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionLabel("Easy Next Move")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Return to your active study timer, or tap 'I'm stuck' if starting feels heavy right now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoled
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onReturnToFocus,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusEmerald,
                    contentColor = AmoledBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Return to Study Timer", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenStuck,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.75.dp, AmoledOutline),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnAmoled)
                    Spacer(Modifier.width(4.dp))
                    Text("I'm stuck", color = OnAmoled)
                }
                OutlinedButton(
                    onClick = onOpenLockSettings,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.75.dp, AmoledOutline),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Shield rules", color = OnAmoled)
                }
            }
            if (config.hasExitPassword) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showPasswordExitDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shield_password_exit_button")
                ) {
                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnAmoledMuted)
                    Spacer(Modifier.width(6.dp))
                    Text("Exit Lock with Password", color = OnAmoledMuted)
                }
            }
        }
    }

    if (showPasswordExitDialog) {
        PasswordExitDialog(
            onDismiss = { showPasswordExitDialog = false },
            onVerifyAndUnlock = { password, exitCompletely ->
                val ok = container.focusLockManager.unlockWithPassword(password, exitCompletely)
                if (ok) {
                    showPasswordExitDialog = false
                    onPasswordUnlocked()
                }
                ok
            }
        )
    }
}
