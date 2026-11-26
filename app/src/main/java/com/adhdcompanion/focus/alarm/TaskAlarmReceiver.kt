package com.adhdcompanion.focus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.adhdcompanion.focus.blocker.FocusLockManager

/**
 * Receives exact per-task AlarmManager broadcasts, extracts the specific due task's
 * ID, title, and notes, and immediately launches AlarmService in the foreground to
 * announce that task aloud.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskAlarmScheduler.ACTION_TASK_ALARM) return

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val handoffWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ADHDFocusCompanion:TaskAlarmReceiverHandoff"
        ).apply {
            setReferenceCounted(false)
            acquire(10_000L)
        }

        try {
            val taskId = intent.getLongExtra(TaskAlarmScheduler.EXTRA_TASK_ID, -1L)
            val taskTitle = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: "Scheduled focus task"
            val taskNotes = intent.getStringExtra(TaskAlarmScheduler.EXTRA_TASK_NOTES).orEmpty()

            FocusLockManager.getInstance(context).evaluateAndSyncState(context)

            AlarmService.startTaskAlarm(
                context = context,
                taskId = taskId,
                taskTitle = taskTitle,
                taskNotes = taskNotes
            )

            AlarmEventBus.emit(
                AlarmTriggerEvent(
                    label = taskTitle,
                    autoSpeak = true,
                    isTaskAlarm = true,
                    taskId = taskId,
                    taskTitle = taskTitle,
                    taskNotes = taskNotes
                )
            )
        } finally {
            if (handoffWakeLock.isHeld) {
                runCatching { handoffWakeLock.release() }
            }
        }
    }
}
