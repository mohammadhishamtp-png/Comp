package com.adhdcompanion.focus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores all upcoming per-task exact alarms, daily morning briefing alarms, and
 * scheduled focus-lock windows after device reboot or app update. Disabled by default
 * in AndroidManifest.xml and enabled via PackageManager when alarms/locks are active.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != ACTION_QUICKBOOT_POWERON
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskAlarmScheduler(appContext).rescheduleAllUpcomingTasks()

                val prefs = SecurePrefsManager(appContext)
                if (prefs.morningAlarmEnabled && !prefs.morningBriefingTime.isNullOrBlank()) {
                    AlarmScheduler(appContext).scheduleNextMorningBriefing()
                }

                FocusLockManager.getInstance(appContext).evaluateAndSyncState(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
