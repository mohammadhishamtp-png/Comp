package com.adhdcompanion.focus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager

/**
 * Restores scheduled morning briefing alarms AND re-evaluates active focus
 * lock windows + boundary alarms after device reboot or app update so
 * distracting apps never remain unblocked after a restart.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = SecurePrefsManager(context)
            if (prefs.morningAlarmEnabled && !prefs.morningBriefingTime.isNullOrBlank()) {
                AlarmScheduler(context).scheduleNextMorningBriefing()
            }
            FocusLockManager.getInstance(context).evaluateAndSyncState(context)
        }
    }
}
