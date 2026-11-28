package com.adhdcompanion.focus.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BlockEntity::class,
        BrainDumpEntity::class,
        StudySubjectEntity::class,
        StudySessionEntity::class,
        StudyTodoEntity::class,
        ExamGoalEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockDao(): BlockDao
    abstract fun brainDumpDao(): BrainDumpDao
    abstract fun studySubjectDao(): StudySubjectDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun studyTodoDao(): StudyTodoDao
    abstract fun examGoalDao(): ExamGoalDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_subjects` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `dailyTargetMinutes` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subjectId` INTEGER NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `subjectColorHex` INTEGER NOT NULL,
                        `mode` TEXT NOT NULL,
                        `startMillis` INTEGER NOT NULL,
                        `endMillis` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `dayEpoch` INTEGER NOT NULL,
                        `interruptionsCount` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subjectId` INTEGER NOT NULL,
                        `subjectName` TEXT NOT NULL,
                        `subjectColorHex` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `dayEpoch` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `estimatedMinutes` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `exam_goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `targetDateEpoch` INTEGER NOT NULL,
                        `targetDailyMinutes` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adhd_focus_companion.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
