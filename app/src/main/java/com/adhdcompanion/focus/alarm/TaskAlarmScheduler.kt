package com.adhdcompanion.focus.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.data.local.AppDatabase
import com.adhdcompanion.focus.data.local.toDomain
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.model.DayBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/**
 * Schedules exact RTC_WAKEUP alarms for every individual scheduled task (DayBlock),
 * manages snoozes, cleans up cancelled/stale task alarms, and toggles BootReceiver
 * via PackageManager so boot recovery only runs when needed.
 */
class TaskAlarmScheduler(private val context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val trackedPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleTask(
        task: DayBlock,
        dayEpoch: Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    ): Long? {
        if (task.id <= 0L || task.isCompleted) {
            if (task.id > 0L) cancelTask(task.id)
            return null
        }

        val zone = ZoneId.systemDefault()
        val triggerAtMillis = LocalDate.ofEpochDay(dayEpoch)
            .atStartOfDay(zone)
            .plusMinutes(task.startMinuteOfDay.toLong())
            .toInstant()
            .toEpochMilli()

        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancelTaskInternal(task.id)
            syncBootReceiverState()
            return null
        }

        val notes = formatTaskNotes(task)
        val pendingIntent = buildTaskPendingIntent(
            taskId = task.id,
            taskTitle = task.title,
            taskNotes = notes,
            requestCode = requestCodeForTask(task.id),
            isSnooze = false
        )

        scheduleExactOrFallback(triggerAtMillis, pendingIntent)
        addTrackedTaskId(task.id)
        syncBootReceiverState()
        return triggerAtMillis
    }

    fun scheduleSnooze(
        taskId: Long,
        taskTitle: String,
        taskNotes: String,
        snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES
    ): Long {
        val safeMinutes = snoozeMinutes.coerceIn(1, 60)
        val triggerAtMillis = System.currentTimeMillis() + safeMinutes * 60_000L
        val effectiveId = if (taskId > 0L) taskId else System.currentTimeMillis()
        val pendingIntent = buildTaskPendingIntent(
            taskId = effectiveId,
            taskTitle = taskTitle,
            taskNotes = taskNotes,
            requestCode = requestCodeForSnooze(effectiveId),
            isSnooze = true
        )

        scheduleExactOrFallback(triggerAtMillis, pendingIntent)
        addTrackedTaskId(effectiveId)
        syncBootReceiverState()
        return triggerAtMillis
    }

    fun cancelTask(taskId: Long) {
        cancelTaskInternal(taskId)
        syncBootReceiverState()
    }

    suspend fun rescheduleAllUpcomingTasks() = withContext(Dispatchers.IO) {
        val trackedIds = getTrackedTaskIds().toList()
        trackedIds.forEach { taskId ->
            cancelPendingIntentsFor(taskId)
        }
        trackedPrefs.edit().putStringSet(KEY_TRACKED_TASK_IDS, emptySet()).apply()

        val dao = AppDatabase.getInstance(appContext).blockDao()
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val nowMillis = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val candidateEpochs = listOf(today, today + 1L)
        val scheduledIds = mutableSetOf<Long>()

        for (epoch in candidateEpochs) {
            val blocks = dao.getBlocksForDay(epoch).map { it.toDomain() }
            for (block in blocks) {
                if (block.id <= 0L || block.isCompleted) continue
                val triggerAtMillis = LocalDate.ofEpochDay(epoch)
                    .atStartOfDay(zone)
                    .plusMinutes(block.startMinuteOfDay.toLong())
                    .toInstant()
                    .toEpochMilli()
                if (triggerAtMillis <= nowMillis) continue

                val pendingIntent = buildTaskPendingIntent(
                    taskId = block.id,
                    taskTitle = block.title,
                    taskNotes = formatTaskNotes(block),
                    requestCode = requestCodeForTask(block.id),
                    isSnooze = false
                )
                scheduleExactOrFallback(triggerAtMillis, pendingIntent)
                scheduledIds.add(block.id)
            }
        }

        trackedPrefs.edit()
            .putStringSet(KEY_TRACKED_TASK_IDS, scheduledIds.map { it.toString() }.toSet())
            .apply()
        syncBootReceiverState()
    }

    private fun cancelTaskInternal(taskId: Long) {
        cancelPendingIntentsFor(taskId)
        removeTrackedTaskId(taskId)
    }

    private fun cancelPendingIntentsFor(taskId: Long) {
        val mainIntent = buildTaskPendingIntent(
            taskId = taskId,
            taskTitle = "",
            taskNotes = "",
            requestCode = requestCodeForTask(taskId),
            isSnooze = false
        )
        alarmManager.cancel(mainIntent)
        mainIntent.cancel()

        val snoozeIntent = buildTaskPendingIntent(
            taskId = taskId,
            taskTitle = "",
            taskNotes = "",
            requestCode = requestCodeForSnooze(taskId),
            isSnooze = true
        )
        alarmManager.cancel(snoozeIntent)
        snoozeIntent.cancel()
    }

    private fun buildTaskPendingIntent(
        taskId: Long,
        taskTitle: String,
        taskNotes: String,
        requestCode: Int,
        isSnooze: Boolean
    ): PendingIntent {
        val schemePath = if (isSnooze) "snooze/$taskId" else "task/$taskId"
        val intent = Intent(appContext, TaskAlarmReceiver::class.java).apply {
            action = ACTION_TASK_ALARM
            data = Uri.parse("adhdcompanion://task_alarm/$schemePath")
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, taskTitle)
            putExtra(EXTRA_TASK_NOTES, taskNotes)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleExactOrFallback(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun syncBootReceiverState() {
        val hasScheduledTasks = getTrackedTaskIds().isNotEmpty()
        val prefs = SecurePrefsManager(appContext)
        val hasMorningAlarm = prefs.morningAlarmEnabled && !prefs.morningBriefingTime.isNullOrBlank()
        val hasFocusWindows = runCatching {
            FocusLockManager.getInstance(appContext).config.value.windows.any { it.isEnabled }
        }.getOrDefault(false)

        setBootReceiverEnabled(hasScheduledTasks || hasMorningAlarm || hasFocusWindows)
    }

    fun setBootReceiverEnabled(enabled: Boolean) {
        val receiver = ComponentName(appContext, BootReceiver::class.java)
        val desiredState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (appContext.packageManager.getComponentEnabledSetting(receiver) != desiredState) {
            appContext.packageManager.setComponentEnabledSetting(
                receiver,
                desiredState,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun getTrackedTaskIds(): Set<Long> {
        val raw = trackedPrefs.getStringSet(KEY_TRACKED_TASK_IDS, emptySet()).orEmpty()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    private fun addTrackedTaskId(taskId: Long) {
        val updated = getTrackedTaskIds().map { it.toString() }.toMutableSet()
        updated.add(taskId.toString())
        trackedPrefs.edit().putStringSet(KEY_TRACKED_TASK_IDS, updated).apply()
    }

    private fun removeTrackedTaskId(taskId: Long) {
        val updated = getTrackedTaskIds().map { it.toString() }.toMutableSet()
        updated.remove(taskId.toString())
        trackedPrefs.edit().putStringSet(KEY_TRACKED_TASK_IDS, updated).apply()
    }

    private fun requestCodeForTask(taskId: Long): Int =
        BASE_TASK_REQUEST_CODE + ((taskId and 0x7FFF_FFFFL) % 100_000L).toInt()

    private fun requestCodeForSnooze(taskId: Long): Int =
        BASE_SNOOZE_REQUEST_CODE + ((taskId and 0x7FFF_FFFFL) % 100_000L).toInt()

    private fun formatTaskNotes(task: DayBlock): String {
        val parts = buildList {
            if (task.why.isNotBlank()) {
                add(task.why.trim().trimEnd('.'))
            }
            if (task.microStep.isNotBlank()) {
                add("First tiny step: ${task.microStep.trim().trimEnd('.')}")
            }
        }
        return parts.joinToString(". ")
    }

    companion object {
        private const val PREFS_NAME = "task_alarm_scheduler_prefs"
        private const val KEY_TRACKED_TASK_IDS = "tracked_task_ids"
        private const val BASE_TASK_REQUEST_CODE = 20_000
        private const val BASE_SNOOZE_REQUEST_CODE = 120_000
        const val DEFAULT_SNOOZE_MINUTES = 5

        const val ACTION_TASK_ALARM = "com.adhdcompanion.focus.alarm.ACTION_TASK_ALARM"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_NOTES = "extra_task_notes"

        fun requestExactAlarmPermissionIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }
}
