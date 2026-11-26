package com.adhdcompanion.focus.di

import android.content.Context
import com.adhdcompanion.focus.alarm.TaskAlarmScheduler
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.data.ai.HybridDoubtEngine
import com.adhdcompanion.focus.data.ai.LocalLiteRtEngine
import com.adhdcompanion.focus.data.local.AppDatabase
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.data.remote.GeminiRepository
import com.adhdcompanion.focus.data.repository.BlockRepository
import com.adhdcompanion.focus.data.repository.BrainDumpRepository
import com.adhdcompanion.focus.data.repository.NoOpCloudSync
import com.adhdcompanion.focus.speech.SpeechRecognitionManager
import com.adhdcompanion.focus.tts.SpeechManager

/**
 * Simple hand-rolled dependency container (no Hilt/Dagger) so the project
 * compiles with zero annotation-processing surprises. Everything is built
 * once, lazily, and handed out from here.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val prefs: SecurePrefsManager by lazy { SecurePrefsManager(appContext) }

    val focusLockManager: FocusLockManager by lazy { FocusLockManager.getInstance(appContext) }

    val taskAlarmScheduler: TaskAlarmScheduler by lazy { TaskAlarmScheduler(appContext) }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    private val cloudSync = NoOpCloudSync()

    val blockRepository: BlockRepository by lazy {
        BlockRepository(database.blockDao(), cloudSync, taskAlarmScheduler)
    }

    val brainDumpRepository: BrainDumpRepository by lazy {
        BrainDumpRepository(database.brainDumpDao(), cloudSync)
    }

    val geminiRepository: GeminiRepository by lazy {
        GeminiRepository(prefs)
    }

    val localLiteRtEngine: LocalLiteRtEngine by lazy {
        LocalLiteRtEngine(appContext)
    }

    val hybridDoubtEngine: HybridDoubtEngine by lazy {
        HybridDoubtEngine(appContext, prefs, localLiteRtEngine)
    }

    val speechManager: SpeechManager by lazy {
        SpeechManager(appContext, geminiRepository, prefs)
    }

    val speechRecognitionManager: SpeechRecognitionManager by lazy {
        SpeechRecognitionManager(appContext)
    }
}
