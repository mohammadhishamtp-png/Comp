package com.adhdcompanion.focus.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySubjectDao {
    @Query("SELECT * FROM study_subjects WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActiveSubjects(): Flow<List<StudySubjectEntity>>

    @Query("SELECT COUNT(*) FROM study_subjects")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<StudySubjectEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subject: StudySubjectEntity): Long

    @Update
    suspend fun update(subject: StudySubjectEntity)

    @Query("UPDATE study_subjects SET isArchived = 1 WHERE id = :subjectId")
    suspend fun archiveById(subjectId: Long)
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions WHERE dayEpoch = :dayEpoch ORDER BY startMillis ASC")
    fun observeSessionsForDay(dayEpoch: Long): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE dayEpoch BETWEEN :startEpoch AND :endEpoch ORDER BY dayEpoch ASC, startMillis ASC")
    fun observeSessionsInRange(startEpoch: Long, endEpoch: Long): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY startMillis DESC")
    fun observeAllSessions(): Flow<List<StudySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: StudySessionEntity): Long

    @Delete
    suspend fun delete(session: StudySessionEntity)
}

@Dao
interface StudyTodoDao {
    @Query("SELECT * FROM study_todos WHERE dayEpoch = :dayEpoch ORDER BY isCompleted ASC, createdAtMillis DESC")
    fun observeTodosForDay(dayEpoch: Long): Flow<List<StudyTodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: StudyTodoEntity): Long

    @Update
    suspend fun update(todo: StudyTodoEntity)

    @Delete
    suspend fun delete(todo: StudyTodoEntity)
}

@Dao
interface ExamGoalDao {
    @Query("SELECT * FROM exam_goals ORDER BY targetDateEpoch ASC")
    fun observeGoals(): Flow<List<ExamGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: ExamGoalEntity): Long

    @Delete
    suspend fun delete(goal: ExamGoalEntity)
}
