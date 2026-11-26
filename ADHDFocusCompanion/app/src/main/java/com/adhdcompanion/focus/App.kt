package com.adhdcompanion.focus

import android.app.Application
import com.adhdcompanion.focus.di.AppContainer
import com.adhdcompanion.focus.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        container.focusLockManager.evaluateAndSyncState(this)
        applicationScope.launch {
            runCatching { container.taskAlarmScheduler.rescheduleAllUpcomingTasks() }
        }
    }
}
