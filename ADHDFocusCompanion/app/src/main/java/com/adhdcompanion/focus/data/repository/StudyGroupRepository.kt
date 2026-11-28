package com.adhdcompanion.focus.data.repository

import android.content.Context
import com.adhdcompanion.focus.model.StudyGroupMember
import com.adhdcompanion.focus.model.StudyMemberStatus
import com.adhdcompanion.focus.model.StudyNudgeEvent
import com.adhdcompanion.focus.model.StudyRoom
import com.adhdcompanion.focus.ui.theme.SubjectColorPalette
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.UUID

interface StudyGroupRepository {
    fun observeRooms(): Flow<List<StudyRoom>>
    fun observeActiveRoomId(): Flow<String>
    fun observeDisplayName(): Flow<String>

    fun selectRoom(roomId: String)
    suspend fun setDisplayName(name: String)
    suspend fun createRoom(name: String, description: String, dailyGoalMinutes: Int): Result<StudyRoom>
    suspend fun joinRoomByCode(code: String): Result<StudyRoom>
    suspend fun leaveRoom(roomId: String): Result<Unit>
    fun updateMyLivePresence(
        status: StudyMemberStatus,
        activeSubject: String,
        subjectColorHex: Long,
        todayMinutes: Int
    )
    suspend fun sendNudge(roomId: String, memberId: String, message: String): Result<Unit>
}

class LocalStudyGroupRepository(context: Context) : StudyGroupRepository {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val suspendMutex = Mutex()

    private val _displayName = MutableStateFlow(
        prefs.getString(KEY_DISPLAY_NAME, "You") ?: "You"
    )
    private val _activeRoomId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_ROOM_ID, DEFAULT_ROOM_ID) ?: DEFAULT_ROOM_ID
    )
    private val _rooms = MutableStateFlow(loadOrSeedRooms())

    init {
        if (prefs.getString(KEY_ROOMS_JSON, null) == null) {
            saveRooms(_rooms.value)
        }
        scope.launch {
            while (isActive) {
                delay(45_000L)
                advancePeerStudyProgress()
            }
        }
    }

    override fun observeRooms(): Flow<List<StudyRoom>> = _rooms.asStateFlow()
    override fun observeActiveRoomId(): Flow<String> = _activeRoomId.asStateFlow()
    override fun observeDisplayName(): Flow<String> = _displayName.asStateFlow()

    @Synchronized
    override fun selectRoom(roomId: String) {
        if (_rooms.value.any { it.id == roomId }) {
            _activeRoomId.value = roomId
            prefs.edit().putString(KEY_ACTIVE_ROOM_ID, roomId).apply()
        }
    }

    override suspend fun setDisplayName(name: String) = suspendMutex.withLock {
        val clean = name.trim().take(24).ifBlank { "You" }
        _displayName.value = clean
        prefs.edit().putString(KEY_DISPLAY_NAME, clean).apply()
        val updated = _rooms.value.map { room ->
            room.copy(
                members = room.members.map { m ->
                    if (m.isMe) m.copy(name = clean) else m
                }
            )
        }
        saveRooms(updated)
    }

    override suspend fun createRoom(
        name: String,
        description: String,
        dailyGoalMinutes: Int
    ): Result<StudyRoom> = suspendMutex.withLock {
        val id = "room_${UUID.randomUUID().toString().take(8)}"
        val code = generateRoomCode()
        val meTemplate = currentMeMember()
        val newRoom = StudyRoom(
            id = id,
            name = name.trim().ifBlank { "Deep Focus Squad" },
            code = code,
            description = description.trim().ifBlank { "Silent accountability & daily sprint targets" },
            dailyGoalMinutes = dailyGoalMinutes.coerceIn(60, 720),
            members = listOf(meTemplate),
            recentNudges = emptyList()
        )
        val updated = listOf(newRoom) + _rooms.value
        saveRooms(updated)
        selectRoom(newRoom.id)
        Result.success(newRoom)
    }

    override suspend fun joinRoomByCode(code: String): Result<StudyRoom> = suspendMutex.withLock {
        val normalized = code.trim().uppercase(Locale.US)
        if (normalized.length < 4) {
            return@withLock Result.failure(IllegalArgumentException("Enter a valid 4+ character room code."))
        }
        val existing = _rooms.value.firstOrNull { it.code.equals(normalized, ignoreCase = true) }
        if (existing != null) {
            selectRoom(existing.id)
            return@withLock Result.success(existing)
        }
        val meTemplate = currentMeMember()
        val room = StudyRoom(
            id = "room_${normalized.lowercase(Locale.US)}",
            name = "Squad $normalized",
            code = normalized,
            description = "Joined via invite code $normalized",
            dailyGoalMinutes = 240,
            members = listOf(meTemplate),
            recentNudges = emptyList()
        )
        saveRooms(_rooms.value + room)
        selectRoom(room.id)
        Result.success(room)
    }

    override suspend fun leaveRoom(roomId: String): Result<Unit> = suspendMutex.withLock {
        val remaining = _rooms.value.filterNot { it.id == roomId }
        val nextRooms = remaining.ifEmpty { seedDefaultRooms() }
        saveRooms(nextRooms)
        if (_activeRoomId.value == roomId) {
            selectRoom(nextRooms.first().id)
        }
        Result.success(Unit)
    }

    @Synchronized
    override fun updateMyLivePresence(
        status: StudyMemberStatus,
        activeSubject: String,
        subjectColorHex: Long,
        todayMinutes: Int
    ) {
        val updated = _rooms.value.map { room ->
            room.copy(
                members = room.members.map { member ->
                    if (member.isMe) {
                        member.copy(
                            name = _displayName.value,
                            status = status,
                            activeSubject = activeSubject.ifBlank { member.activeSubject },
                            subjectColorHex = subjectColorHex,
                            todayMinutes = todayMinutes.coerceAtLeast(0)
                        )
                    } else member
                }
            )
        }
        saveRooms(updated)
    }

    override suspend fun sendNudge(roomId: String, memberId: String, message: String): Result<Unit> = suspendMutex.withLock {
        val now = System.currentTimeMillis()
        val myName = _displayName.value
        val updated = _rooms.value.map { room ->
            if (room.id != roomId) return@map room
            val target = room.members.firstOrNull { it.id == memberId } ?: return@map room
            val nextMembers = room.members.map { m ->
                if (m.id == memberId) m.copy(lastNudgeFromMeMillis = now) else m
            }
            val nudge = StudyNudgeEvent(
                id = UUID.randomUUID().toString(),
                fromName = myName,
                toName = target.name,
                message = message,
                timestampMillis = now
            )
            room.copy(
                members = nextMembers,
                recentNudges = (listOf(nudge) + room.recentNudges).take(12)
            )
        }
        saveRooms(updated)
        Result.success(Unit)
    }

    @Synchronized
    private fun advancePeerStudyProgress() {
        val updated = _rooms.value.map { room ->
            room.copy(
                members = room.members.map { m ->
                    if (!m.isMe && m.status == StudyMemberStatus.STUDYING) {
                        m.copy(todayMinutes = m.todayMinutes + 1)
                    } else m
                }
            )
        }
        saveRooms(updated)
    }

    private fun currentMeMember(): StudyGroupMember {
        val existingMe = _rooms.value.firstOrNull()?.members?.firstOrNull { it.isMe }
        return existingMe?.copy(name = _displayName.value) ?: StudyGroupMember(
            id = "me_local",
            name = _displayName.value,
            status = StudyMemberStatus.OFFLINE,
            activeSubject = "Physics",
            subjectColorHex = SubjectColorPalette[0],
            todayMinutes = 0,
            isMe = true
        )
    }

    private fun saveRooms(rooms: List<StudyRoom>) {
        _rooms.value = rooms
        prefs.edit().putString(KEY_ROOMS_JSON, gson.toJson(rooms)).apply()
    }

    private fun loadOrSeedRooms(): List<StudyRoom> {
        val raw = prefs.getString(KEY_ROOMS_JSON, null) ?: return seedDefaultRooms()
        val type = object : TypeToken<List<StudyRoom>>() {}.type
        val parsed = runCatching { gson.fromJson<List<StudyRoom>>(raw, type) }.getOrNull()
        return if (parsed.isNullOrEmpty()) seedDefaultRooms() else parsed
    }

    private fun seedDefaultRooms(): List<StudyRoom> {
        val myName = _displayName.value
        val me = StudyGroupMember(
            id = "me_local",
            name = myName,
            status = StudyMemberStatus.OFFLINE,
            activeSubject = "Physics",
            subjectColorHex = SubjectColorPalette[0],
            todayMinutes = 0,
            isMe = true
        )
        return listOf(
            StudyRoom(
                id = DEFAULT_ROOM_ID,
                name = "Focus Hall",
                code = "FOCUS1",
                description = "Silent accountability · 4h daily goal",
                dailyGoalMinutes = 240,
                members = listOf(me),
                recentNudges = emptyList()
            )
        )
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    companion object {
        private const val PREFS_NAME = "study_groups_prefs"
        private const val KEY_ROOMS_JSON = "rooms_json"
        private const val KEY_ACTIVE_ROOM_ID = "active_room_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val DEFAULT_ROOM_ID = "room_stem_main"
    }
}
