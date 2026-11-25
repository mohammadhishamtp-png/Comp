package com.adhdcompanion.focus.data.repository

import com.adhdcompanion.focus.data.local.BlockDao
import com.adhdcompanion.focus.data.local.toDomain
import com.adhdcompanion.focus.data.local.toEntity
import com.adhdcompanion.focus.model.BlockCategory
import com.adhdcompanion.focus.model.DayBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Local-first repository: every read/write goes through Room immediately,
 * and cloud sync (if configured) happens best-effort alongside it.
 */
class BlockRepository(
    private val dao: BlockDao,
    private val cloudSync: CloudSyncInterface = NoOpCloudSync()
) {
    fun todayEpoch(): Long = LocalDate.now(ZoneId.systemDefault()).toEpochDay()

    fun observeBlocksForDay(dayEpoch: Long = todayEpoch()): Flow<List<DayBlock>> =
        dao.observeBlocksForDay(dayEpoch).map { list -> list.map { it.toDomain() } }

    suspend fun getBlocksForDay(dayEpoch: Long = todayEpoch()): List<DayBlock> =
        withContext(Dispatchers.IO) {
            dao.getBlocksForDay(dayEpoch).map { it.toDomain() }
        }

    /** Finds the block that contains "now" (by minute-of-day), if any. */
    suspend fun getCurrentBlock(nowMinuteOfDay: Int, dayEpoch: Long = todayEpoch()): DayBlock? =
        getBlocksForDay(dayEpoch).firstOrNull {
            !it.isCompleted && nowMinuteOfDay in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes)
        } ?: getBlocksForDay(dayEpoch).firstOrNull {
            nowMinuteOfDay in it.startMinuteOfDay until (it.startMinuteOfDay + it.durationMinutes)
        }

    suspend fun getNextBlock(nowMinuteOfDay: Int, dayEpoch: Long = todayEpoch()): DayBlock? =
        getBlocksForDay(dayEpoch)
            .filter { !it.isCompleted && it.startMinuteOfDay > nowMinuteOfDay }
            .minByOrNull { it.startMinuteOfDay }

    suspend fun replaceDayPlan(blocks: List<DayBlock>, dayEpoch: Long = todayEpoch()) {
        withContext(Dispatchers.IO) {
            dao.replaceDay(dayEpoch, blocks.map { it.toEntity(dayEpoch) })
        }
        runCatching { cloudSync.pushBlocks(dayEpoch, blocks) }
    }

    suspend fun upsertBlock(block: DayBlock, dayEpoch: Long = todayEpoch()): Long =
        withContext(Dispatchers.IO) {
            if (block.id == 0L) {
                dao.insert(block.toEntity(dayEpoch))
            } else {
                dao.update(block.toEntity(dayEpoch))
                block.id
            }
        }

    suspend fun markCompleted(block: DayBlock, dayEpoch: Long = todayEpoch()) {
        withContext(Dispatchers.IO) {
            dao.update(block.copy(isCompleted = true).toEntity(dayEpoch))
        }
    }

    suspend fun deleteBlock(block: DayBlock, dayEpoch: Long = todayEpoch()) {
        withContext(Dispatchers.IO) {
            dao.delete(block.toEntity(dayEpoch))
        }
    }

    /**
     * Adds a tiny practice block (e.g. from Doubt Solver) at the next open slot today
     * so it appears immediately in Planner and Now & Briefing.
     */
    suspend fun addQuickMicroStepBlock(
        title: String,
        why: String,
        microStep: String,
        durationMinutes: Int = 10,
        category: BlockCategory = BlockCategory.STUDY,
        dayEpoch: Long = todayEpoch()
    ): DayBlock = withContext(Dispatchers.IO) {
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val roundedNow = ((nowMinute + 4) / 5) * 5
        val existing = dao.getBlocksForDay(dayEpoch).map { it.toDomain() }.sortedBy { it.startMinuteOfDay }

        var candidateStart = roundedNow.coerceIn(0, 23 * 60 + 45)
        for (block in existing) {
            val blockEnd = block.startMinuteOfDay + block.durationMinutes
            if (candidateStart < blockEnd && (candidateStart + durationMinutes) > block.startMinuteOfDay) {
                candidateStart = blockEnd
            }
        }
        candidateStart = candidateStart.coerceAtMost(23 * 60 + (60 - durationMinutes).coerceAtLeast(0))

        val newBlock = DayBlock(
            title = title.take(64),
            why = why,
            microStep = microStep,
            startMinuteOfDay = candidateStart,
            durationMinutes = durationMinutes,
            category = category,
            isDecompressionBuffer = false,
            isCompleted = false
        )
        val insertedId = dao.insert(newBlock.toEntity(dayEpoch))
        newBlock.copy(id = insertedId)
    }
}
