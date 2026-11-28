package com.adhdcompanion.focus.data.repository

import com.adhdcompanion.focus.data.local.ExamGoalDao
import com.adhdcompanion.focus.data.local.StudySessionDao
import com.adhdcompanion.focus.data.local.StudySubjectDao
import com.adhdcompanion.focus.data.local.StudySubjectEntity
import com.adhdcompanion.focus.data.local.StudyTodoDao
import com.adhdcompanion.focus.data.local.toDomain
import com.adhdcompanion.focus.data.local.toEntity
import com.adhdcompanion.focus.data.remote.FirestoreStudySyncRepository
import com.adhdcompanion.focus.model.ExamGoal
import com.adhdcompanion.focus.model.HeatmapDayCell
import com.adhdcompanion.focus.model.StudySession
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.model.StudyTimerMode
import com.adhdcompanion.focus.model.StudyTodoItem
import com.adhdcompanion.focus.model.SubjectBreakdownItem
import com.adhdcompanion.focus.model.TimelineTenMinCell
import com.adhdcompanion.focus.ui.theme.SubjectColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class StudyRepository(
    private val subjectDao: StudySubjectDao,
    private val sessionDao: StudySessionDao,
    private val todoDao: StudyTodoDao,
    private val examGoalDao: ExamGoalDao,
    private val cloudSync: FirestoreStudySyncRepository? = null
) {
    private val seedMutex = Mutex()
    private val cloudPullMutex = Mutex()
    private var lastPulledUid: String? = null

    fun todayEpoch(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    suspend fun ensureDefaultSubjects() {
        seedMutex.withLock {
            withContext(Dispatchers.IO) {
                if (subjectDao.getCount() == 0) {
                    val defaults = listOf(
                        StudySubjectEntity(
                            name = "Physics",
                            colorHex = SubjectColorPalette[0],
                            dailyTargetMinutes = 90,
                            sortOrder = 0,
                            isArchived = false
                        ),
                        StudySubjectEntity(
                            name = "Chemistry",
                            colorHex = SubjectColorPalette[1],
                            dailyTargetMinutes = 90,
                            sortOrder = 1,
                            isArchived = false
                        ),
                        StudySubjectEntity(
                            name = "Mathematics",
                            colorHex = SubjectColorPalette[2],
                            dailyTargetMinutes = 120,
                            sortOrder = 2,
                            isArchived = false
                        ),
                        StudySubjectEntity(
                            name = "Revision",
                            colorHex = SubjectColorPalette[3],
                            dailyTargetMinutes = 60,
                            sortOrder = 3,
                            isArchived = false
                        ),
                        StudySubjectEntity(
                            name = "Mock Tests",
                            colorHex = SubjectColorPalette[4],
                            dailyTargetMinutes = 60,
                            sortOrder = 4,
                            isArchived = false
                        )
                    )
                    val ids = subjectDao.insertAll(defaults)
                    defaults.zip(ids).forEach { (entity, id) ->
                        runCatching { cloudSync?.upsertSubject(entity.copy(id = id).toDomain()) }
                    }
                }
            }
        }
    }

    suspend fun syncFromCloudIfNeeded(uid: String) {
        if (cloudSync == null) return
        cloudPullMutex.withLock {
            if (lastPulledUid == uid) return
            withContext(Dispatchers.IO) {
                runCatching {
                    val cloudSubjects = cloudSync.fetchCloudSubjects()
                    if (cloudSubjects.isNotEmpty()) {
                        val existingNames = subjectDao.observeActiveSubjects().first()
                            .map { it.name.lowercase(Locale.US) }.toSet()
                        cloudSubjects.forEach { cs ->
                            if (cs.name.lowercase(Locale.US) !in existingNames) {
                                subjectDao.insert(cs.copy(id = 0L).toEntity())
                            }
                        }
                    }

                    val cloudSessions = cloudSync.fetchRecentCloudSessions()
                    if (cloudSessions.isNotEmpty()) {
                        val existingKeys = sessionDao.observeAllSessions().first()
                            .map { "${it.startMillis}_${it.subjectName}" }.toSet()
                        cloudSessions.forEach { sess ->
                            val key = "${sess.startMillis}_${sess.subjectName}"
                            if (key !in existingKeys) {
                                sessionDao.insert(sess.copy(id = 0L).toEntity())
                            }
                        }
                    }

                    val cloudGoals = cloudSync.fetchCloudExamGoals()
                    if (cloudGoals.isNotEmpty()) {
                        val existingGoals = examGoalDao.observeGoals().first()
                            .map { "${it.title}_${it.targetDateEpoch}" }.toSet()
                        cloudGoals.forEach { goal ->
                            val key = "${goal.title}_${goal.targetDateEpoch}"
                            if (key !in existingGoals) {
                                examGoalDao.insert(goal.copy(id = 0L).toEntity())
                            }
                        }
                    }
                    lastPulledUid = uid
                }
            }
        }
    }

    fun observeActiveSubjects(): Flow<List<StudySubject>> =
        subjectDao.observeActiveSubjects().map { list -> list.map { it.toDomain() } }

    suspend fun addSubject(
        name: String,
        colorHex: Long,
        dailyTargetMinutes: Int
    ): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim().ifBlank { "Study" }
        val count = subjectDao.getCount()
        val entity = StudySubjectEntity(
            name = trimmed,
            colorHex = colorHex,
            dailyTargetMinutes = dailyTargetMinutes.coerceIn(10, 720),
            sortOrder = count,
            isArchived = false
        )
        val insertedId = subjectDao.insert(entity)
        runCatching { cloudSync?.upsertSubject(entity.copy(id = insertedId).toDomain()) }
        insertedId
    }

    suspend fun updateSubject(subject: StudySubject) {
        withContext(Dispatchers.IO) {
            val cleaned = subject.copy(
                name = subject.name.trim().ifBlank { "Study" },
                dailyTargetMinutes = subject.dailyTargetMinutes.coerceIn(10, 720)
            )
            subjectDao.update(cleaned.toEntity())
            runCatching { cloudSync?.upsertSubject(cleaned) }
        }
    }

    suspend fun archiveSubject(subjectId: Long) {
        withContext(Dispatchers.IO) {
            val existing = subjectDao.observeActiveSubjects().first().firstOrNull { it.id == subjectId }
            subjectDao.archiveById(subjectId)
            existing?.let {
                runCatching { cloudSync?.upsertSubject(it.toDomain().copy(isArchived = true)) }
            }
        }
    }

    fun observeSessionsForDay(dayEpoch: Long = todayEpoch()): Flow<List<StudySession>> =
        sessionDao.observeSessionsForDay(dayEpoch).map { list -> list.map { it.toDomain() } }

    fun observeSessionsInRange(startEpoch: Long, endEpoch: Long): Flow<List<StudySession>> =
        sessionDao.observeSessionsInRange(startEpoch, endEpoch).map { list -> list.map { it.toDomain() } }

    fun observeAllSessions(): Flow<List<StudySession>> =
        sessionDao.observeAllSessions().map { list -> list.map { it.toDomain() } }

    suspend fun recordStudySession(
        subject: StudySubject,
        mode: StudyTimerMode,
        startMillis: Long,
        endMillis: Long,
        interruptionsCount: Int = 0,
        notes: String = ""
    ) {
        val safeEnd = maxOf(endMillis, startMillis + 1000L)
        val totalSeconds = ((safeEnd - startMillis) / 1000L).toInt().coerceAtLeast(1)
        if (totalSeconds < 5) return

        val zone = ZoneId.systemDefault()
        withContext(Dispatchers.IO) {
            var cursorStart = startMillis
            var remainingInterruptions = interruptionsCount.coerceAtLeast(0)
            while (cursorStart < safeEnd) {
                val startLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(cursorStart), zone)
                val day = startLdt.toLocalDate()
                val nextMidnightMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val segmentEnd = minOf(safeEnd, nextMidnightMillis)
                val segSeconds = ((segmentEnd - cursorStart) / 1000L).toInt().coerceAtLeast(1)

                val session = StudySession(
                    subjectId = subject.id,
                    subjectName = subject.name,
                    subjectColorHex = subject.colorHex,
                    mode = mode,
                    startMillis = cursorStart,
                    endMillis = segmentEnd,
                    durationSeconds = segSeconds,
                    dayEpoch = day.toEpochDay(),
                    interruptionsCount = remainingInterruptions,
                    notes = notes
                )
                val id = sessionDao.insert(session.toEntity())
                runCatching { cloudSync?.pushSession(session.copy(id = id)) }
                remainingInterruptions = 0
                cursorStart = segmentEnd
            }
        }
    }

    fun observeTodosForDay(dayEpoch: Long = todayEpoch()): Flow<List<StudyTodoItem>> =
        todoDao.observeTodosForDay(dayEpoch).map { list -> list.map { it.toDomain() } }

    suspend fun addTodo(
        subject: StudySubject?,
        title: String,
        estimatedMinutes: Int = 30,
        dayEpoch: Long = todayEpoch()
    ): Long = withContext(Dispatchers.IO) {
        val item = StudyTodoItem(
            subjectId = subject?.id ?: 0L,
            subjectName = subject?.name ?: "General",
            subjectColorHex = subject?.colorHex ?: SubjectColorPalette[0],
            title = title.trim(),
            dayEpoch = dayEpoch,
            isCompleted = false,
            estimatedMinutes = estimatedMinutes.coerceIn(5, 360),
            createdAtMillis = System.currentTimeMillis()
        )
        val insertedId = todoDao.insert(item.toEntity())
        runCatching { cloudSync?.upsertTodo(item.copy(id = insertedId)) }
        insertedId
    }

    suspend fun toggleTodo(item: StudyTodoItem) {
        withContext(Dispatchers.IO) {
            val toggled = item.copy(isCompleted = !item.isCompleted)
            todoDao.update(toggled.toEntity())
            runCatching { cloudSync?.upsertTodo(toggled) }
        }
    }

    suspend fun deleteTodo(item: StudyTodoItem) {
        withContext(Dispatchers.IO) {
            todoDao.delete(item.toEntity())
            runCatching { cloudSync?.deleteTodo(item) }
        }
    }

    fun observeExamGoals(): Flow<List<ExamGoal>> =
        examGoalDao.observeGoals().map { list -> list.map { it.toDomain() } }

    suspend fun upsertExamGoal(goal: ExamGoal): Long = withContext(Dispatchers.IO) {
        val id = examGoalDao.insert(goal.toEntity())
        runCatching { cloudSync?.upsertExamGoal(goal.copy(id = id)) }
        id
    }

    suspend fun deleteExamGoal(goal: ExamGoal) {
        withContext(Dispatchers.IO) {
            examGoalDao.delete(goal.toEntity())
            runCatching { cloudSync?.deleteExamGoal(goal) }
        }
    }

    companion object {
        fun buildTenMinuteTimeline(
            sessions: List<StudySession>,
            dayEpoch: Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        ): List<TimelineTenMinCell> {
            val zone = ZoneId.systemDefault()
            val dayStartMillis = LocalDate.ofEpochDay(dayEpoch).atStartOfDay(zone).toInstant().toEpochMilli()
            val slotMs = 10 * 60_000L

            val cells = ArrayList<TimelineTenMinCell>(24 * 6)
            for (hour in 0 until 24) {
                for (slot in 0 until 6) {
                    val slotIndexTotal = hour * 6 + slot
                    val cellStart = dayStartMillis + slotIndexTotal * slotMs
                    val cellEnd = cellStart + slotMs

                    var bestSession: StudySession? = null
                    var bestOverlapMs = 0L

                    for (session in sessions) {
                        val overlapStart = maxOf(cellStart, session.startMillis)
                        val overlapEnd = minOf(cellEnd, session.endMillis)
                        val overlap = overlapEnd - overlapStart
                        if (overlap > bestOverlapMs) {
                            bestOverlapMs = overlap
                            bestSession = session
                        }
                    }

                    val coveredMinutes = ((bestOverlapMs + 30_000L) / 60_000L).toInt().coerceIn(0, 10)
                    cells.add(
                        TimelineTenMinCell(
                            hourOfDay = hour,
                            slotIndex = slot,
                            subjectName = if (bestOverlapMs >= 15_000L) bestSession?.subjectName else null,
                            colorHex = if (bestOverlapMs >= 15_000L) bestSession?.subjectColorHex else null,
                            minutesCovered = if (bestOverlapMs >= 15_000L) coveredMinutes.coerceAtLeast(1) else 0,
                            sessionId = if (bestOverlapMs >= 15_000L) bestSession?.id else null
                        )
                    )
                }
            }
            return cells
        }

        fun buildHeatmapDays(
            minutesByEpoch: Map<Long, Int>,
            daysCount: Int = 91
        ): List<HeatmapDayCell> {
            val today = LocalDate.now(ZoneId.systemDefault())
            val todayEpoch = today.toEpochDay()
            val startEpoch = todayEpoch - (daysCount - 1).coerceAtLeast(6)
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

            return (startEpoch..todayEpoch).map { epoch ->
                val date = LocalDate.ofEpochDay(epoch)
                val mins = minutesByEpoch[epoch] ?: 0
                val level = when {
                    mins <= 0 -> 0
                    mins < 45 -> 1
                    mins < 120 -> 2
                    mins < 240 -> 3
                    else -> 4
                }
                HeatmapDayCell(
                    dayEpoch = epoch,
                    dateLabel = date.format(formatter),
                    focusedMinutes = mins,
                    intensityLevel = level
                )
            }
        }

        fun buildSubjectBreakdown(
            sessions: List<StudySession>,
            subjects: List<StudySubject>
        ): List<SubjectBreakdownItem> {
            val groupedSeconds = sessions.groupBy { it.subjectId }
                .mapValues { (_, list) -> list.sumOf { it.durationSeconds } }
            val totalSeconds = groupedSeconds.values.sum().coerceAtLeast(1)

            val subjectById = subjects.associateBy { it.id }
            val items = mutableListOf<SubjectBreakdownItem>()

            groupedSeconds.forEach { (subjectId, secs) ->
                val sample = sessions.firstOrNull { it.subjectId == subjectId }
                val sub = subjectById[subjectId]
                val mins = (secs / 60).coerceAtLeast(if (secs > 0) 1 else 0)
                items.add(
                    SubjectBreakdownItem(
                        subjectId = subjectId,
                        subjectName = sub?.name ?: sample?.subjectName ?: "Study",
                        colorHex = sub?.colorHex ?: sample?.subjectColorHex ?: SubjectColorPalette[0],
                        totalMinutes = mins,
                        dailyTargetMinutes = sub?.dailyTargetMinutes ?: 90,
                        shareFraction = (secs.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
                    )
                )
            }

            subjects.forEach { sub ->
                if (items.none { it.subjectId == sub.id }) {
                    items.add(
                        SubjectBreakdownItem(
                            subjectId = sub.id,
                            subjectName = sub.name,
                            colorHex = sub.colorHex,
                            totalMinutes = 0,
                            dailyTargetMinutes = sub.dailyTargetMinutes,
                            shareFraction = 0f
                        )
                    )
                }
            }

            return items.sortedByDescending { it.totalMinutes }
        }
    }
}
