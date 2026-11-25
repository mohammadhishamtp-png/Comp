package com.adhdcompanion.focus.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.adhdcompanion.focus.MainActivity
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import java.util.Calendar

/**
 * Schedules the daily morning/transition briefing alarm using
 * AlarmManager.setAlarmClock(...) so Android treats it as a real alarm clock
 * without Doze deferral. Also schedules lock-window boundary evaluations.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = SecurePrefsManager(context)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** Computes the next occurrence in millis for the configured morning briefing time, or null if off/invalid. */
    fun getNextMorningAlarmTriggerMillis(): Long? {
        if (!prefs.morningAlarmEnabled) return null
        val (hour, minute) = parseSavedTime() ?: return null
        return nextCalendarFor(hour, minute).timeInMillis
    }

    private fun parseSavedTime(): Pair<Int, Int>? {
        val timeString = prefs.morningBriefingTime ?: return null
        val parts = timeString.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return hour to minute
    }

    private fun nextCalendarFor(hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    /** Reads the saved "HH:mm" morning time from settings and (re)schedules it if enabled. */
    fun scheduleNextMorningBriefing() {
        if (!prefs.morningAlarmEnabled) {
            cancelDailyAlarm()
            return
        }
        val (hour, minute) = parseSavedTime() ?: return
        scheduleDailyAlarm(hour, minute, "Good morning — your first block is ready whenever you are.")
    }

    fun scheduleDailyAlarm(hour: Int, minute: Int, label: String) {
        val calendar = nextCalendarFor(hour, minute)

        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MORNING_ALARM
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_IS_DAILY, true)
            putExtra(AlarmReceiver.EXTRA_AUTO_SPEAK, true)
        }
        val operationPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_BRIEFING,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW_CLOCK,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_AUTO_SPEAK, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAsAlarmClock(calendar.timeInMillis, showIntent, operationPendingIntent)
    }

    /** Schedules a short test alarm (default 5 seconds) using setAlarmClock so users can test audio + spoken briefing. */
    fun scheduleTestAlarm(
        delaySeconds: Int = 5,
        label: String = "Test alarm — checking your spoken morning briefing."
    ): Long {
        val triggerAtMillis = System.currentTimeMillis() + delaySeconds * 1000L
        val receiverIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TEST_ALARM
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_IS_DAILY, false)
            putExtra(AlarmReceiver.EXTRA_AUTO_SPEAK, true)
        }
        val operationPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TEST_ALARM,
            receiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW_CLOCK,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_AUTO_SPEAK, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAsAlarmClock(triggerAtMillis, showIntent, operationPendingIntent)
        return triggerAtMillis
    }

    private fun scheduleAsAlarmClock(
        triggerAtMillis: Long,
        showIntent: PendingIntent,
        operationPendingIntent: PendingIntent
    ) {
        try {
            val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
            alarmManager.setAlarmClock(info, operationPendingIntent)
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                operationPendingIntent
            )
        }
    }

    /** One-off transition alarm for a specific block's start time today, e.g. "Study block starts now". */
    fun scheduleOneOffTransition(triggerAtMillis: Long, label: String, requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRANSITION_ALARM
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
            putExtra(AlarmReceiver.EXTRA_IS_DAILY, false)
            putExtra(AlarmReceiver.EXTRA_AUTO_SPEAK, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun scheduleLockBoundaryEvaluation(triggerAtMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EVALUATE_LOCK_WINDOWS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_LOCK_BOUNDARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelLockBoundaryEvaluation() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_EVALUATE_LOCK_WINDOWS
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_LOCK_BOUNDARY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun cancelDailyAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_MORNING_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY_BRIEFING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE_DAILY_BRIEFING = 9001
        private const val REQUEST_CODE_SHOW_CLOCK = 9002
        private const val REQUEST_CODE_TEST_ALARM = 9003
        private const val REQUEST_CODE_LOCK_BOUNDARY = 9004
    }
}
