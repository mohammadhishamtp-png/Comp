package com.adhdcompanion.focus.di

import android.content.Context
import com.adhdcompanion.focus.R
import com.adhdcompanion.focus.alarm.TaskAlarmScheduler
import com.adhdcompanion.focus.audio.FocusSoundscapeEngine
import com.adhdcompanion.focus.blocker.AndroidFocusBlockerController
import com.adhdcompanion.focus.blocker.FocusBlockerController
import com.adhdcompanion.focus.blocker.FocusLockManager
import com.adhdcompanion.focus.data.ai.HybridDoubtEngine
import com.adhdcompanion.focus.data.ai.LocalLiteRtEngine
import com.adhdcompanion.focus.data.local.AppDatabase
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.data.remote.AuthManager
import com.adhdcompanion.focus.data.remote.FirestoreStudySyncRepository
import com.adhdcompanion.focus.data.remote.GeminiRepository
import com.adhdcompanion.focus.data.repository.BlockRepository
import com.adhdcompanion.focus.data.repository.BrainDumpRepository
import com.adhdcompanion.focus.data.repository.FirestoreStudyGroupRepository
import com.adhdcompanion.focus.data.repository.StudyGroupRepository
import com.adhdcompanion.focus.data.repository.StudyRepository
import com.adhdcompanion.focus.speech.SpeechRecognitionManager
import com.adhdcompanion.focus.tts.SpeechManager
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val prefs: SecurePrefsManager by lazy { SecurePrefsManager(appContext) }

    val firestore: FirebaseFirestore by lazy {
        val dbId = appContext.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    val authManager: AuthManager by lazy {
        AuthManager(appContext, firestore)
    }

    val focusLockManager: FocusLockManager by lazy { FocusLockManager.getInstance(appContext) }

    val focusBlockerController: FocusBlockerController by lazy {
        AndroidFocusBlockerController(appContext, focusLockManager)
    }

    val taskAlarmScheduler: TaskAlarmScheduler by lazy { TaskAlarmScheduler(appContext) }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val firestoreStudySync: FirestoreStudySyncRepository by lazy {
        FirestoreStudySyncRepository(firestore)
    }

    val blockRepository: BlockRepository by lazy {
        BlockRepository(database.blockDao(), firestoreStudySync, taskAlarmScheduler)
    }

    val brainDumpRepository: BrainDumpRepository by lazy {
        BrainDumpRepository(database.brainDumpDao(), firestoreStudySync)
    }

    val studyRepository: StudyRepository by lazy {
        StudyRepository(
            subjectDao = database.studySubjectDao(),
            sessionDao = database.studySessionDao(),
            todoDao = database.studyTodoDao(),
            examGoalDao = database.examGoalDao(),
            cloudSync = firestoreStudySync
        )
    }

    val studyGroupRepository: StudyGroupRepository by lazy {
        FirestoreStudyGroupRepository(appContext, firestore)
    }

    val soundscapeEngine: FocusSoundscapeEngine by lazy {
        FocusSoundscapeEngine()
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
