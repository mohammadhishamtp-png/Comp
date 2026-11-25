package com.adhdcompanion.focus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.notification.NotificationHelper

/**
 * Handles morning briefing alarms, test alarms, dismiss actions, and
 * scheduled focus-lock window boundary evaluations.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS_ALARM -> {
                AlarmSoundPlayer.stop()
                NotificationHelper.cancelAlarmNotification(context)
                return
            }

            ACTION_EVALUATE_LOCK_WINDOWS -> {
                FocusLockManager.getInstance(context).evaluateAndSyncState(context)
                return
            }
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ADHDFocusCompanion:AlarmWakeLock"
        )
        wakeLock.acquire(20_000L)

        try {
            val label = intent.getStringExtra(EXTRA_LABEL) ?: "Time to check in"
            val autoSpeak = intent.getBooleanExtra(EXTRA_AUTO_SPEAK, true)

            // Re-evaluate scheduled focus lock windows whenever an alarm fires
            FocusLockManager.getInstance(context).evaluateAndSyncState(context)

            // Play alarm sound on AudioAttributes.USAGE_ALARM (with TYPE_NOTIFICATION fallback)
            AlarmSoundPlayer.playAlarmChime(context)

            // Post high-priority alarm notification with EXTRA_AUTO_SPEAK
            NotificationHelper.showAlarmNotification(
                context = context,
                title = "Focus Companion Alarm",
                body = label,
                autoSpeak = autoSpeak
            )

            // Notify foreground UI (if currently open) to surface banner & trigger SpeechManager.speak
            AlarmEventBus.emit(
                AlarmTriggerEvent(
                    label = label,
                    autoSpeak = autoSpeak
                )
            )

            // Re-schedule tomorrow's recurring morning alarm if this was the daily one
            if (intent.getBooleanExtra(EXTRA_IS_DAILY, false)) {
                AlarmScheduler(context).scheduleNextMorningBriefing()
            }
        } finally {
            if (wakeLock.isHeld) {
                runCatching { wakeLock.release() }
            }
        }
    }

    companion object {
        const val ACTION_MORNING_ALARM = "com.adhdcompanion.focus.ACTION_MORNING_ALARM"
        const val ACTION_TEST_ALARM = "com.adhdcompanion.focus.ACTION_TEST_ALARM"
        const val ACTION_TRANSITION_ALARM = "com.adhdcompanion.focus.ACTION_TRANSITION_ALARM"
        const val ACTION_DISMISS_ALARM = "com.adhdcompanion.focus.ACTION_DISMISS_ALARM"
        const val ACTION_EVALUATE_LOCK_WINDOWS = "com.adhdcompanion.focus.ACTION_EVALUATE_LOCK_WINDOWS"

        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_IS_DAILY = "extra_is_daily"
        const val EXTRA_AUTO_SPEAK = "extra_auto_speak"
    }
}
