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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adhdcompanion.focus.alarm.AlarmScheduler
import com.adhdcompanion.focus.alarm.AlarmService
import com.adhdcompanion.focus.alarm.TtsAvailabilityStatus
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.ui.components.RotatingClockPicker
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
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
                .padding(20.dp)
        ) {
            // 1. Focus Lock Quick Access Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
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
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Scheduled App Blocker & Anti-Impulse Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            when {
                                lockEval.isBlockingActive -> "Active right now · Cooldown protected"
                                accessibilityEnabled -> "Accessibility ready · Tap to configure windows & apps"
                                else -> "Tap to set up Accessibility Blocker & lock windows"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
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
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            // TTS voice/engine availability warning if missing
            ttsStatus?.message?.let { ttsWarn ->
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Text-to-Speech Setup Needed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ttsWarn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                }
                            ) {
                                Text("Install / Check Voice Data")
                            }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !scheduler.canScheduleExactAlarms()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Exact alarms are currently restricted by the system for this app.",
                    color = MaterialTheme.colorScheme.error,
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
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // 3. Hybrid AI Keys (Groq Primary + Gemini Image/Fallback)
            Text("Groq API key (Fast text primary)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Used first for Doubt Solver text streaming (openai/gpt-oss-120b). Stored encrypted on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(20.dp))
            Text("Gemini API key (Images & fallback)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Used for photo doubts (gemini-3.5-flash-lite), Planner/Stuck, and automatic Groq fallback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
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
                    Text("Use lighter fallback model", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Prefer lighter Flash-Lite models (auto-discovers compatible models if needed).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = useFallback, onCheckedChange = { useFallback = it })
            }

            Spacer(Modifier.height(22.dp))
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Offline LiteRT-LM Model (.litertlm)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (localModelInfo.exists) {
                            "Loaded: ${localModelInfo.fileName} • ${localModelInfo.formattedSize}"
                        } else {
                            "No offline model loaded yet. Import a .litertlm model file for offline Doubt Solver fallback."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (localModelInfo.exists) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isImportingModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_select_offline_model_button")
                    ) {
                        if (isImportingModel) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Importing .litertlm model…")
                        } else {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select Offline Model (.litertlm)")
                        }
                    }
                    modelImportMessage?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // 4. Voice preference
            Text("Voice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
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

            Spacer(Modifier.height(28.dp))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_settings_button")
            ) {
                Text("Save settings")
            }

            savedMessage?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
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
