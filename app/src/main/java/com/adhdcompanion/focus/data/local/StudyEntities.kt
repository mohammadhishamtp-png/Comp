package com.adhdcompanion.focus.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.adhdcompanion.focus.model.ExamGoal
import com.adhdcompanion.focus.model.StudySession
import com.adhdcompanion.focus.model.StudySubject
import com.adhdcompanion.focus.model.StudyTimerMode
import com.adhdcompanion.focus.model.StudyTodoItem

@Entity(tableName = "study_subjects")
data class StudySubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: Long,
    val dailyTargetMinutes: Int,
    val sortOrder: Int,
    val isArchived: Boolean
)

fun StudySubjectEntity.toDomain(): StudySubject = StudySubject(
    id = id,
    name = name,
    colorHex = colorHex,
    dailyTargetMinutes = dailyTargetMinutes,
    sortOrder = sortOrder,
    isArchived = isArchived
)

fun StudySubject.toEntity(): StudySubjectEntity = StudySubjectEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    dailyTargetMinutes = dailyTargetMinutes,
    sortOrder = sortOrder,
    isArchived = isArchived
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val subjectColorHex: Long,
    val mode: String,
    val startMillis: Long,
    val endMillis: Long,
    val durationSeconds: Int,
    val dayEpoch: Long,
    val interruptionsCount: Int,
    val notes: String
)

fun StudySessionEntity.toDomain(): StudySession = StudySession(
    id = id,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    mode = runCatching { StudyTimerMode.valueOf(mode) }.getOrDefault(StudyTimerMode.STOPWATCH),
    startMillis = startMillis,
    endMillis = endMillis,
    durationSeconds = durationSeconds,
    dayEpoch = dayEpoch,
    interruptionsCount = interruptionsCount,
    notes = notes
)

fun StudySession.toEntity(): StudySessionEntity = StudySessionEntity(
    id = id,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    mode = mode.name,
    startMillis = startMillis,
    endMillis = endMillis,
    durationSeconds = durationSeconds,
    dayEpoch = dayEpoch,
    interruptionsCount = interruptionsCount,
    notes = notes
)

@Entity(tableName = "study_todos")
data class StudyTodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val subjectColorHex: Long,
    val title: String,
    val dayEpoch: Long,
    val isCompleted: Boolean,
    val estimatedMinutes: Int,
    val createdAtMillis: Long
)

fun StudyTodoEntity.toDomain(): StudyTodoItem = StudyTodoItem(
    id = id,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    title = title,
    dayEpoch = dayEpoch,
    isCompleted = isCompleted,
    estimatedMinutes = estimatedMinutes,
    createdAtMillis = createdAtMillis
)

fun StudyTodoItem.toEntity(): StudyTodoEntity = StudyTodoEntity(
    id = id,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    title = title,
    dayEpoch = dayEpoch,
    isCompleted = isCompleted,
    estimatedMinutes = estimatedMinutes,
    createdAtMillis = createdAtMillis
)

@Entity(tableName = "exam_goals")
data class ExamGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetDateEpoch: Long,
    val targetDailyMinutes: Int,
    val createdAtMillis: Long
)

fun ExamGoalEntity.toDomain(): ExamGoal = ExamGoal(
    id = id,
    title = title,
    targetDateEpoch = targetDateEpoch,
    targetDailyMinutes = targetDailyMinutes,
    createdAtMillis = createdAtMillis
)

fun ExamGoal.toEntity(): ExamGoalEntity = ExamGoalEntity(
    id = id,
    title = title,
    targetDateEpoch = targetDateEpoch,
    targetDailyMinutes = targetDailyMinutes,
    createdAtMillis = createdAtMillis
)
