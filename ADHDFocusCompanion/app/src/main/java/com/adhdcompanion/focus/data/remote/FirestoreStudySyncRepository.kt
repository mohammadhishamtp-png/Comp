package com.adhdcompanion.focus.data.remote

import com.adhdcompanion.focus.data.repository.CloudSyncInterface
import com.adhdcompanion.focus.model.BrainDumpEntry
import com.adhdcompanion.focus.model.DayBlock
import com.adhdcompanion.focus.model.ExamGoal
import com.adhdcompanion.focus.model.StudySession
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.model.StudyTimerMode
import com.adhdcompanion.focus.model.StudyTodoItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreStudySyncRepository(
    private val firestore: FirebaseFirestore
) : CloudSyncInterface {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun pushBlocks(dayEpoch: Long, blocks: List<DayBlock>): Result<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid() ?: return@withContext Result.success(Unit)
            val col = firestore.collection("users").document(uid).collection("blocks")
            try {
                blocks.forEach { block ->
                    val docId = "day_${dayEpoch}_${block.startMinuteOfDay}_${block.title.hashCode()}"
                    val payload = mapOf(
                        "userId" to uid,
                        "dayEpoch" to dayEpoch,
                        "title" to block.title.trim().take(120).ifBlank { "Focus Block" },
                        "why" to block.why.trim().take(300),
                        "microStep" to block.microStep.trim().take(300),
                        "startMinuteOfDay" to block.startMinuteOfDay.coerceIn(0, 1439),
                        "durationMinutes" to block.durationMinutes.coerceIn(1, 720),
                        "category" to block.category.name,
                        "decompressionBuffer" to block.isDecompressionBuffer,
                        "completed" to block.isCompleted,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    col.document(docId).set(payload, SetOptions.merge()).await()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                FirestoreErrorHandler.logNonFatal(e, OperationType.WRITE, "users/$uid/blocks")
                Result.failure(e)
            }
        }

    override suspend fun pullBlocks(dayEpoch: Long): Result<List<DayBlock>> =
        withContext(Dispatchers.IO) {
            val uid = currentUid() ?: return@withContext Result.success(emptyList())
            val path = "users/$uid/blocks"
            try {
                val snap = firestore.collection("users").document(uid).collection("blocks")
                    .whereEqualTo("dayEpoch", dayEpoch)
                    .get()
                    .await()
                val items = snap.documents.mapNotNull { doc ->
                    val dto = doc.toObject(
                        FirestoreDayBlockDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    DayBlock(
                        id = 0L,
                        title = dto.title,
                        why = dto.why,
                        microStep = dto.microStep,
                        startMinuteOfDay = dto.startMinuteOfDay,
                        durationMinutes = dto.durationMinutes,
                        category = runCatching {
                            com.adhdcompanion.focus.model.BlockCategory.valueOf(dto.category)
                        }.getOrDefault(com.adhdcompanion.focus.model.BlockCategory.STUDY),
                        isDecompressionBuffer = dto.decompressionBuffer,
                        isCompleted = dto.completed
                    )
                }.sortedBy { it.startMinuteOfDay }
                Result.success(items)
            } catch (e: Exception) {
                FirestoreErrorHandler.logNonFatal(e, OperationType.LIST, path)
                Result.failure(e)
            }
        }

    override suspend fun pushBrainDump(entry: BrainDumpEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            val uid = currentUid() ?: return@withContext Result.success(Unit)
            val path = "users/$uid/brainDumps"
            try {
                val docId = "dump_${entry.createdAtMillis}"
                val payload = mapOf(
                    "userId" to uid,
                    "text" to entry.text.trim().take(600),
                    "createdAtMillis" to entry.createdAtMillis.coerceAtLeast(1L),
                    "sentToPlanner" to entry.sentToPlanner,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(uid).collection("brainDumps")
                    .document(docId)
                    .set(payload, SetOptions.merge())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                FirestoreErrorHandler.logNonFatal(e, OperationType.WRITE, path)
                Result.failure(e)
            }
        }

    suspend fun upsertSubject(subject: StudySubject): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        val docId = "sub_${subject.id}"
        try {
            val payload = mapOf(
                "userId" to uid,
                "localId" to subject.id.coerceAtLeast(0L),
                "name" to subject.name.trim().take(60).ifBlank { "Study" },
                "colorHex" to subject.colorHex,
                "dailyTargetMinutes" to subject.dailyTargetMinutes.coerceIn(5, 720),
                "sortOrder" to subject.sortOrder.coerceAtLeast(0),
                "archived" to subject.isArchived,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).collection("subjects")
                .document(docId)
                .set(payload, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.WRITE, "users/$uid/subjects/$docId")
            Result.failure(e)
        }
    }

    suspend fun pushSession(session: StudySession): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        val docId = "sess_${session.startMillis}_${session.subjectId}"
        try {
            val payload = mapOf(
                "userId" to uid,
                "subjectId" to session.subjectId.coerceAtLeast(0L),
                "subjectName" to session.subjectName.trim().take(60).ifBlank { "Study" },
                "subjectColorHex" to session.subjectColorHex,
                "mode" to session.mode.name,
                "startMillis" to session.startMillis.coerceAtLeast(1L),
                "endMillis" to maxOf(session.endMillis, session.startMillis),
                "durationSeconds" to session.durationSeconds.coerceIn(1, 86400),
                "dayEpoch" to session.dayEpoch.coerceAtLeast(1L),
                "interruptionsCount" to session.interruptionsCount.coerceAtLeast(0),
                "notes" to session.notes.take(300),
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).collection("sessions")
                .document(docId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.CREATE, "users/$uid/sessions/$docId")
            Result.failure(e)
        }
    }

    suspend fun upsertTodo(todo: StudyTodoItem): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        val docId = "todo_${todo.id}_${todo.createdAtMillis}"
        try {
            val payload = mapOf(
                "userId" to uid,
                "localId" to todo.id.coerceAtLeast(0L),
                "subjectId" to todo.subjectId.coerceAtLeast(0L),
                "subjectName" to todo.subjectName.take(60),
                "subjectColorHex" to todo.subjectColorHex,
                "title" to todo.title.trim().take(160).ifBlank { "Task" },
                "dayEpoch" to todo.dayEpoch.coerceAtLeast(1L),
                "completed" to todo.isCompleted,
                "estimatedMinutes" to todo.estimatedMinutes.coerceIn(1, 480),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).collection("todos")
                .document(docId)
                .set(payload, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.WRITE, "users/$uid/todos/$docId")
            Result.failure(e)
        }
    }

    suspend fun deleteTodo(todo: StudyTodoItem): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        val docId = "todo_${todo.id}_${todo.createdAtMillis}"
        try {
            firestore.collection("users").document(uid).collection("todos")
                .document(docId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.DELETE, "users/$uid/todos/$docId")
            Result.failure(e)
        }
    }

    suspend fun upsertExamGoal(goal: ExamGoal): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        val docId = "goal_${goal.id}_${goal.createdAtMillis}"
        try {
            val payload = mapOf(
                "userId" to uid,
                "localId" to goal.id.coerceAtLeast(0L),
                "title" to goal.title.trim().take(100).ifBlank { "Exam Target" },
                "targetDateEpoch" to goal.targetDateEpoch.coerceAtLeast(1L),
                "targetDailyMinutes" to goal.targetDailyMinutes.coerceIn(15, 960),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("users").document(uid).collection("examGoals")
                .document(docId)
                .set(payload, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.WRITE, "users/$uid/examGoals/$docId")
            Result.failure(e)
        }
    }

    suspend fun deleteExamGoal(goal: ExamGoal): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext Result.success(Unit)
        val docId = "goal_${goal.id}_${goal.createdAtMillis}"
        try {
            firestore.collection("users").document(uid).collection("examGoals")
                .document(docId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.DELETE, "users/$uid/examGoals/$docId")
            Result.failure(e)
        }
    }

    suspend fun fetchCloudSubjects(): List<StudySubject> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext emptyList()
        runCatching {
            val snap = firestore.collection("users").document(uid).collection("subjects")
                .whereEqualTo("archived", false)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val dto = doc.toObject(
                    FirestoreSubjectDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@mapNotNull null
                StudySubject(
                    id = dto.localId,
                    name = dto.name,
                    colorHex = dto.colorHex,
                    dailyTargetMinutes = dto.dailyTargetMinutes,
                    sortOrder = dto.sortOrder,
                    isArchived = dto.archived
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchRecentCloudSessions(limit: Long = 200): List<StudySession> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext emptyList()
        runCatching {
            val snap = firestore.collection("users").document(uid).collection("sessions")
                .orderBy("startMillis", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val dto = doc.toObject(
                    FirestoreSessionDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@mapNotNull null
                StudySession(
                    id = 0L,
                    subjectId = dto.subjectId,
                    subjectName = dto.subjectName,
                    subjectColorHex = dto.subjectColorHex,
                    mode = runCatching { StudyTimerMode.valueOf(dto.mode) }.getOrDefault(StudyTimerMode.STOPWATCH),
                    startMillis = dto.startMillis,
                    endMillis = dto.endMillis,
                    durationSeconds = dto.durationSeconds,
                    dayEpoch = dto.dayEpoch,
                    interruptionsCount = dto.interruptionsCount,
                    notes = dto.notes
                )
            }
        }.getOrElse { emptyList() }
    }

    suspend fun fetchCloudExamGoals(): List<ExamGoal> = withContext(Dispatchers.IO) {
        val uid = currentUid() ?: return@withContext emptyList()
        runCatching {
            val snap = firestore.collection("users").document(uid).collection("examGoals")
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                val dto = doc.toObject(
                    FirestoreExamGoalDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: return@mapNotNull null
                ExamGoal(
                    id = dto.localId,
                    title = dto.title,
                    targetDateEpoch = dto.targetDateEpoch,
                    targetDailyMinutes = dto.targetDailyMinutes
                )
            }
        }.getOrElse { emptyList() }
    }
}
