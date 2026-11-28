package com.adhdcompanion.focus.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adhdcompanion.focus.alarm.AlarmScheduler
import com.adhdcompanion.focus.alarm.AlarmService
import com.adhdcompanion.focus.alarm.TtsAvailabilityStatus
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.ui.components.AmoledCard
import com.adhdcompanion.focus.ui.components.RotatingClockPicker
import com.adhdcompanion.focus.ui.components.SectionLabel
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutline
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.AmoledSurfaceElevated
import com.adhdcompanion.focus.ui.theme.FocusCoral
import com.adhdcompanion.focus.ui.theme.FocusCoralContainer
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.FocusEmeraldDim
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenFocusLock: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val prefs = container.prefs
    val scheduler = remember { AlarmScheduler(context) }
    val scope = rememberCoroutineScope()
    val currentUser = container.authManager.currentUser

    val initialParts = remember {
        val raw = prefs.morningBriefingTime ?: "07:30"
        val split = raw.split(":")
        val h = split.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val m = split.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 30
        h to m
    }

    var groqApiKey by remember { mutableStateOf(prefs.groqApiKey.orEmpty()) }
    var groqKeyVisible by remember { mutableStateOf(false) }

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey.orEmpty()) }
    var keyVisible by remember { mutableStateOf(false) }
    var useFallback by remember { mutableStateOf(prefs.useFlashLiteFallbackModel) }
    var voicePref by remember { mutableStateOf(prefs.voicePreference) }

    var localModelInfo by remember { mutableStateOf(container.localLiteRtEngine.getModelInfo()) }
    var isImportingModel by remember { mutableStateOf(false) }
    var modelImportMessage by remember { mutableStateOf<String?>(null) }

    var alarmHour24 by remember { mutableIntStateOf(initialParts.first) }
    var alarmMinute by remember { mutableIntStateOf(initialParts.second) }
    var alarmEnabled by remember { mutableStateOf(prefs.morningAlarmEnabled) }
    var alarmStatusMessage by remember { mutableStateOf<String?>(null) }
    var ttsStatus by remember { mutableStateOf<TtsAvailabilityStatus?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var isSigningOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AlarmService.checkTtsAvailability(context) { status ->
            ttsStatus = status
        }
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isImportingModel = true
                modelImportMessage = null
                container.localLiteRtEngine.importModelFromUri(uri)
                    .onSuccess { info ->
                        localModelInfo = info
                        isImportingModel = false
                        modelImportMessage = "Imported ${info.fileName} (${info.formattedSize})."
                    }
                    .onFailure { err ->
                        isImportingModel = false
                        modelImportMessage = err.message ?: "Could not import selected model."
                    }
            }
        }
    }

    var pendingActionAfterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingActionAfterPermission?.invoke()
        } else {
            alarmStatusMessage = "Notification permission denied — alarm sound will still ring, but notifications won't show."
            pendingActionAfterPermission?.invoke()
        }
        pendingActionAfterPermission = null
    }

    fun ensureNotificationPermissionThen(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingActionAfterPermission = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        action()
    }

    val accessibilityEnabled = FocusBlockerAccessibilityService.isServiceEnabled(context)
    val lockEval = container.focusLockManager.evaluateNow()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AmoledBlack,
                titleContentColor = OnAmoled,
                navigationIconContentColor = OnAmoled
            ),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // 0. Google Account & Cloud Sync
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(FocusEmeraldDim),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = FocusEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Signed-in Scholar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnAmoled,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUser?.email ?: "Google Account connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmoledMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = FocusEmeraldDim
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = FocusEmerald,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Synced",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FocusEmerald
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isSigningOut = true
                                container.authManager.signOut()
                                isSigningOut = false
                            }
                        },
                        enabled = !isSigningOut,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.75.dp, AmoledOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_sign_out_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = OnAmoledMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isSigningOut) "Signing out…" else "Sign Out",
                            color = OnAmoled
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 1. Focus Lock Quick Access Card
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = FocusEmerald.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFocusLock)
                    .testTag("settings_focus_lock_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = FocusEmerald
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Scheduled App Blocker & Anti-Impulse Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnAmoled
                        )
                        Text(
                            when {
                                lockEval.isBlockingActive -> "Active right now · Cooldown protected"
                                accessibilityEnabled -> "Accessibility ready · Tap to configure windows & apps"
                                else -> "Tap to set up Accessibility Blocker & lock windows"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAmoledMuted
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = OnAmoledMuted
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // 2. Google Clock-Style Rotating Analog Dial Alarm
            RotatingClockPicker(
                hour24 = alarmHour24,
                minute = alarmMinute,
                alarmEnabled = alarmEnabled,
                nextAlarmReadout = formatNextAlarmReadout(alarmHour24, alarmMinute, alarmEnabled),
                onTimeChanged = { h24, min ->
                    alarmHour24 = h24
                    alarmMinute = min
                    val formatted = "%02d:%02d".format(Locale.US, h24, min)
                    prefs.morningBriefingTime = formatted
                    if (alarmEnabled) {
                        scheduler.scheduleNextMorningBriefing()
                    }
                },
                onToggleAlarmEnabled = { wantEnabled ->
                    if (wantEnabled) {
                        ensureNotificationPermissionThen {
                            alarmEnabled = true
                            prefs.morningAlarmEnabled = true
                            prefs.morningBriefingTime = "%02d:%02d".format(Locale.US, alarmHour24, alarmMinute)
                            scheduler.scheduleNextMorningBriefing()
                            alarmStatusMessage = "Daily alarm scheduled using AlarmClock."
                        }
                    } else {
                        alarmEnabled = false
                        prefs.morningAlarmEnabled = false
                        scheduler.cancelDailyAlarm()
                        alarmStatusMessage = "Daily morning alarm turned off."
                    }
                },
                onTestAlarmClick = {
                    ensureNotificationPermissionThen {
                        scheduler.scheduleTestAlarm(delaySeconds = 5)
                        alarmStatusMessage = "Test alarm set! Foreground AlarmService will ring and read your schedule in 5 seconds."
                    }
                }
            )

            alarmStatusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = FocusEmerald)
            }

            ttsStatus?.message?.let { ttsWarn ->
                Spacer(Modifier.height(12.dp))
                AmoledCard(
                    containerColor = FocusCoralContainer,
                    borderColor = FocusCoral.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = FocusCoral
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Text-to-Speech Setup Needed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = FocusCoral
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ttsWarn,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnAmoled
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent("com.android.settings.TTS_SETTINGS")
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            },
                            border = BorderStroke(0.75.dp, FocusCoral.copy(alpha = 0.5f))
                        ) {
                            Text("Install / Check Voice Data", color = OnAmoled)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !scheduler.canScheduleExactAlarms()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Exact alarms are currently restricted by the system for this app.",
                    color = FocusCoral,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }) {
                    Text("Allow exact alarms")
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
            Spacer(Modifier.height(20.dp))

            // 3. Hybrid AI Keys (Groq Primary + Gemini Image/Fallback)
            SectionLabel("Hybrid AI Study Engine")
            Spacer(Modifier.height(6.dp))
            Text("Groq API key (Fast text primary)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnAmoled)
            Spacer(Modifier.height(4.dp))
            Text(
                "Used first for Doubt Solver text streaming (openai/gpt-oss-120b). Stored encrypted on this device only.",
                style = MaterialTheme.typography.bodySmall,
                color = OnAmoledMuted
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = groqApiKey,
                onValueChange = { groqApiKey = it },
                label = { Text("Groq API key") },
                singleLine = true,
                visualTransformation = if (groqKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { groqKeyVisible = !groqKeyVisible }) {
                        Icon(
                            if (groqKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Groq key visibility"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("groq_api_key_input")
            )

            Spacer(Modifier.height(18.dp))
            Text("Gemini API key (Images & fallback)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = OnAmoled)
            Spacer(Modifier.height(4.dp))
            Text(
                "Used for photo doubts (gemini-3.5-flash-lite), Planner/Stuck, and automatic Groq fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = OnAmoledMuted
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle Gemini key visibility"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input")
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Use lighter fallback model", style = MaterialTheme.typography.titleSmall, color = OnAmoled)
                    Text(
                        "Prefer lighter Flash-Lite models (auto-discovers compatible models if needed).",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnAmoledMuted
                    )
                }
                Switch(checked = useFallback, onCheckedChange = { useFallback = it })
            }

            Spacer(Modifier.height(18.dp))
            AmoledCard(
                containerColor = AmoledSurface,
                borderColor = AmoledOutlineSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Offline LiteRT-LM Model (.litertlm)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnAmoled
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (localModelInfo.exists) {
                            "Loaded: ${localModelInfo.fileName} • ${localModelInfo.formattedSize}"
                        } else {
                            "No offline model loaded yet. Import a .litertlm model file for offline Doubt Solver fallback."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (localModelInfo.exists) FocusEmerald else OnAmoledMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isImportingModel,
                        border = BorderStroke(0.75.dp, AmoledOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_select_offline_model_button")
                    ) {
                        if (isImportingModel) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Importing .litertlm model…", color = OnAmoled)
                        } else {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnAmoled)
                            Spacer(Modifier.width(8.dp))
                            Text("Select Offline Model (.litertlm)", color = OnAmoled)
                        }
                    }
                    modelImportMessage?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = FocusEmerald)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(thickness = 0.75.dp, color = AmoledOutlineSubtle)
            Spacer(Modifier.height(20.dp))

            // 4. Voice preference
            SectionLabel("Alarm Voice Engine")
            Spacer(Modifier.height(8.dp))
            VoiceOptionRow(
                label = "Gemini expressive voice (falls back automatically if offline)",
                selected = voicePref == SecurePrefsManager.VOICE_GEMINI_WITH_FALLBACK,
                onSelect = { voicePref = SecurePrefsManager.VOICE_GEMINI_WITH_FALLBACK }
            )
            VoiceOptionRow(
                label = "Android built-in voice only",
                selected = voicePref == SecurePrefsManager.VOICE_ANDROID_ONLY,
                onSelect = { voicePref = SecurePrefsManager.VOICE_ANDROID_ONLY }
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    prefs.groqApiKey = groqApiKey.trim().ifBlank { null }
                    prefs.geminiApiKey = apiKey.trim().ifBlank { null }
                    prefs.useFlashLiteFallbackModel = useFallback
                    prefs.voicePreference = voicePref
                    prefs.morningBriefingTime = "%02d:%02d".format(Locale.US, alarmHour24, alarmMinute)
                    prefs.morningAlarmEnabled = alarmEnabled

                    if (alarmEnabled) {
                        scheduler.scheduleNextMorningBriefing()
                    } else {
                        scheduler.cancelDailyAlarm()
                    }
                    savedMessage = "Settings saved."
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusEmerald,
                    contentColor = AmoledBlack
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Text("Save settings", fontWeight = FontWeight.Bold)
            }

            savedMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = FocusEmerald)
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun VoiceOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnAmoled)
    }
}

private fun formatNextAlarmReadout(hour24: Int, minute: Int, enabled: Boolean): String {
    if (!enabled) return "Alarm off"
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour24)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now.timeInMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val diffMins = ((target.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
    val hrs = diffMins / 60
    val mins = diffMins % 60
    val dayWord = if (target.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) "Today" else "Tomorrow"
    val amPm = if (hour24 < 12) "AM" else "PM"
    val h12 = when (val h = hour24 % 12) {
        0 -> 12
        else -> h
    }
    val timeText = "%d:%02d %s".format(Locale.getDefault(), h12, minute, amPm)
    val deltaText = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
    return "Next: $dayWord at $timeText (in $deltaText)"
}
