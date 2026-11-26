package com.adhdcompanion.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.adhdcompanion.focus.alarm.AlarmEventBus
import com.adhdcompanion.focus.blocker.FocusBlockerAccessibilityService
import com.adhdcompanion.focus.navigation.AppNavigation
import com.adhdcompanion.focus.navigation.BlockedRedirectPrompt
import com.adhdcompanion.focus.ui.theme.ADHDFocusCompanionTheme

class MainActivity : ComponentActivity() {

    private var autoSpeakTriggerId by mutableIntStateOf(0)
    private var navigateToNowTriggerId by mutableIntStateOf(0)
    private var navigateToFocusLockTriggerId by mutableIntStateOf(0)
    private var blockedPrompt by mutableStateOf<BlockedRedirectPrompt?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as App).container
        handleIntent(intent)

        setContent {
            ADHDFocusCompanionTheme {
                // Navigate to Now when a scheduled alarm fires; AlarmService owns foreground audio + full schedule TTS until dismissed
                LaunchedEffect(Unit) {
                    AlarmEventBus.events.collect {
                        navigateToNowTriggerId++
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        container = container,
                        autoSpeakTriggerId = autoSpeakTriggerId,
                        navigateToNowTriggerId = navigateToNowTriggerId,
                        navigateToFocusLockTriggerId = navigateToFocusLockTriggerId,
                        blockedPrompt = blockedPrompt,
                        onDismissBlockedPrompt = { blockedPrompt = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        FocusBlockerAccessibilityService.setMainActivityResumed(true)
    }

    override fun onPause() {
        FocusBlockerAccessibilityService.setMainActivityResumed(false)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        if (!blockedPkg.isNullOrBlank()) {
            val appName = intent.getStringExtra(EXTRA_BLOCKED_APP_NAME) ?: blockedPkg
            val reason = intent.getStringExtra(EXTRA_LOCK_BLOCKED_REASON) ?: ""
            blockedPrompt = BlockedRedirectPrompt(
                packageName = blockedPkg,
                appLabel = appName,
                reason = reason
            )
            intent.removeExtra(EXTRA_BLOCKED_PACKAGE)
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_FOCUS_LOCK, false)) {
            navigateToFocusLockTriggerId++
            intent.removeExtra(EXTRA_OPEN_FOCUS_LOCK)
        }

        if (intent.getBooleanExtra(EXTRA_AUTO_SPEAK, false)) {
            navigateToNowTriggerId++
            if (!AlarmEventBus.isRinging.value) {
                autoSpeakTriggerId++
            }
            intent.removeExtra(EXTRA_AUTO_SPEAK)
        }
    }

    companion object {
        const val EXTRA_AUTO_SPEAK = "extra_auto_speak"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_OPEN_FOCUS_LOCK = "extra_open_focus_lock"
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_APP_NAME = "extra_blocked_app_name"
        const val EXTRA_LOCK_BLOCKED_REASON = "extra_lock_blocked_reason"
    }
}
