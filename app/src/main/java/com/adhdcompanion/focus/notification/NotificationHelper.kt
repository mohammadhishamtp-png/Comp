package com.adhdcompanion.focus.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.adhdcompanion.focus.MainActivity
import com.adhdcompanion.focus.alarm.AlarmReceiver

object NotificationHelper {
    const val CHANNEL_ID = "briefing_channel"
    const val ALARM_CHANNEL_ID = "morning_alarm_channel"
    const val LOCK_CHANNEL_ID = "focus_lock_channel"

    const val NOTIFICATION_ID = 1001
    const val ALARM_NOTIFICATION_ID = 1002
    const val LOCK_NOTIFICATION_ID = 1003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Briefings & transitions",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Your morning briefing and block-transition nudges."
                    }
                )
            }

            if (manager.getNotificationChannel(ALARM_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ALARM_CHANNEL_ID,
                        "Morning briefing alarm",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "High-priority daily wake and transition alarms."
                        setSound(null, null) // AlarmSoundPlayer plays on USAGE_ALARM directly
                        enableVibration(true)
                    }
                )
            }

            if (manager.getNotificationChannel(LOCK_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        LOCK_CHANNEL_ID,
                        "Active focus lock",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows when a scheduled distraction lock window is active."
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    fun showBriefingNotification(context: Context, title: String, body: String) {
        ensureChannel(context)
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun buildAlarmNotification(
        context: Context,
        title: String,
        body: String,
        autoSpeak: Boolean,
        taskId: Long = -1L,
        taskTitle: String = "",
        taskNotes: String = "",
        includeSnoozeAction: Boolean = false
    ): android.app.Notification {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_AUTO_SPEAK, autoSpeak)
            putExtra(MainActivity.EXTRA_ALARM_LABEL, body)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            ALARM_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_NOTIFICATION_ID + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Open Now", contentPendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)

        if (includeSnoozeAction) {
            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_SNOOZE_ALARM
                putExtra(com.adhdcompanion.focus.alarm.TaskAlarmScheduler.EXTRA_TASK_ID, taskId)
                putExtra(
                    com.adhdcompanion.focus.alarm.TaskAlarmScheduler.EXTRA_TASK_TITLE,
                    taskTitle.ifBlank { title }
                )
                putExtra(com.adhdcompanion.focus.alarm.TaskAlarmScheduler.EXTRA_TASK_NOTES, taskNotes)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_NOTIFICATION_ID + 2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Snooze 5m", snoozePendingIntent)
        }

        return builder.build()
    }

    fun showAlarmNotification(
        context: Context,
        title: String,
        body: String,
        autoSpeak: Boolean
    ) {
        val notification = buildAlarmNotification(context, title, body, autoSpeak)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(ALARM_NOTIFICATION_ID, notification)
    }

    fun cancelAlarmNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(ALARM_NOTIFICATION_ID)
    }

    fun updateFocusLockNotification(
        context: Context,
        windowName: String,
        untilText: String
    ) {
        runCatching {
            ensureChannel(context)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_FOCUS_LOCK, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                LOCK_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, LOCK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Focus Lock Active: $windowName")
                .setContentText("Distracting apps blocked until $untilText")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(LOCK_NOTIFICATION_ID, notification)
        }
    }

    fun cancelFocusLockNotification(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(LOCK_NOTIFICATION_ID)
        }
    }
}
