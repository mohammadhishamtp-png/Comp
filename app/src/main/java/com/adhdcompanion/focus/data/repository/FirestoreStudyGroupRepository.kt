package com.adhdcompanion.focus.data.repository

import android.content.Context
import com.adhdcompanion.focus.data.remote.FirestoreErrorHandler
import com.adhdcompanion.focus.data.remote.FirestoreNudgeDoc
import com.adhdcompanion.focus.data.remote.FirestoreRoomMemberDoc
import com.adhdcompanion.focus.data.remote.FirestoreStudyRoomDoc
import com.adhdcompanion.focus.data.remote.OperationType
import com.adhdcompanion.focus.model.StudyGroupMember
import com.adhdcompanion.focus.model.StudyMemberStatus
import com.adhdcompanion.focus.model.StudyNudgeEvent
import com.adhdcompanion.focus.model.StudyRoom
import com.adhdcompanion.focus.ui.theme.SubjectColorPalette
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreStudyGroupRepository(
    context: Context,
    private val firestore: FirebaseFirestore
) : StudyGroupRepository {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeRoomId = MutableStateFlow(prefs.getString(KEY_ACTIVE_ROOM_ID, "") ?: "")
    private val _displayName = MutableStateFlow(loadInitialDisplayName())

    private var lastStatus: StudyMemberStatus = StudyMemberStatus.OFFLINE
    private var lastSubject: String = "Physics"
    private var lastColorHex: Long = SubjectColorPalette[0]
    private var lastMinutes: Int = 0
    private var lastSyncTimestamp: Long = 0L

    private val authUidFlow: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val u = fa.currentUser
            if (u != null && _displayName.value == "Scholar") {
                val googleName = u.displayName?.trim()?.take(24)
                if (!googleName.isNullOrBlank()) {
                    _displayName.value = googleName
                }
            }
            trySend(u?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private val roomDocsFlow: Flow<List<FirestoreStudyRoomDoc>> = authUidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                val registration = firestore.collection("studyRooms")
                    .whereArrayContains("memberUids", uid)
                    .addSnapshotListener { snap, err ->
                        if (err != null) {
                            FirestoreErrorHandler.logNonFatal(err, OperationType.LIST, "studyRooms")
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        val docs = snap?.documents?.mapNotNull { doc ->
                            doc.toObject(
                                FirestoreStudyRoomDoc::class.java,
                                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                            )
                        }.orEmpty()
                        if (docs.isNotEmpty() && docs.none { it.id == _activeRoomId.value }) {
                            selectRoom(docs.first().id)
                        }
                        if (docs.isEmpty() && !prefs.getBoolean("starter_created_$uid", false)) {
                            prefs.edit().putBoolean("starter_created_$uid", true).apply()
                            scope.launch {
                                createRoom(
                                    name = "My Focus Hall",
                                    description = "Share your invite code so friends can join live",
                                    dailyGoalMinutes = 240
                                )
                            }
                        }
                        trySend(docs)
                    }
                awaitClose { registration.remove() }
            }
        }
    }

    private val activeRoomMembersFlow: Flow<List<StudyGroupMember>> =
        combine(authUidFlow, _activeRoomId) { uid, roomId -> uid to roomId }
            .flatMapLatest { (uid, roomId) ->
                if (uid == null || roomId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    callbackFlow {
                        val registration = firestore.collection("studyRooms")
                            .document(roomId)
                            .collection("members")
                            .addSnapshotListener { snap, err ->
                                if (err != null) {
                                    FirestoreErrorHandler.logNonFatal(
                                        err,
                                        OperationType.LIST,
                                        "studyRooms/$roomId/members"
                                    )
                                    trySend(emptyList())
                                    return@addSnapshotListener
                                }
                                val members = snap?.documents?.mapNotNull { doc ->
                                    val dto = doc.toObject(
                                        FirestoreRoomMemberDoc::class.java,
                                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                    ) ?: return@mapNotNull null
                                    StudyGroupMember(
                                        id = dto.uid.ifBlank { doc.id },
                                        name = dto.displayName.ifBlank { "Scholar" },
                                        status = runCatching {
                                            StudyMemberStatus.valueOf(dto.status)
                                        }.getOrDefault(StudyMemberStatus.OFFLINE),
                                        activeSubject = dto.activeSubject.ifBlank { "Study" },
                                        subjectColorHex = dto.subjectColorHex,
                                        todayMinutes = dto.todayMinutes.coerceAtLeast(0),
                                        isMe = (dto.uid.ifBlank { doc.id }) == uid
                                    )
                                }.orEmpty()
                                trySend(members)
                            }
                        awaitClose { registration.remove() }
                    }
                }
            }

    private val activeRoomNudgesFlow: Flow<List<StudyNudgeEvent>> =
        combine(authUidFlow, _activeRoomId) { uid, roomId -> uid to roomId }
            .flatMapLatest { (uid, roomId) ->
                if (uid == null || roomId.isBlank()) {
                    flowOf(emptyList())
                } else {
                    callbackFlow {
                        val registration = firestore.collection("studyRooms")
                            .document(roomId)
                            .collection("nudges")
                            .orderBy("createdAt", Query.Direction.DESCENDING)
                            .limit(12)
                            .addSnapshotListener { snap, err ->
                                if (err != null) {
                                    FirestoreErrorHandler.logNonFatal(
                                        err,
                                        OperationType.LIST,
                                        "studyRooms/$roomId/nudges"
                                    )
                                    trySend(emptyList())
                                    return@addSnapshotListener
                                }
                                val nudges = snap?.documents?.mapNotNull { doc ->
                                    val dto = doc.toObject(
                                        FirestoreNudgeDoc::class.java,
                                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                                    ) ?: return@mapNotNull null
                                    StudyNudgeEvent(
                                        id = doc.id,
                                        fromName = dto.fromName,
                                        toName = dto.toName,
                                        message = dto.message,
                                        timestampMillis = dto.createdAt?.toDate()?.time
                                            ?: System.currentTimeMillis()
                                    )
                                }.orEmpty()
                                trySend(nudges)
                            }
                        awaitClose { registration.remove() }
                    }
                }
            }

    private val combinedRoomsState = combine(
        roomDocsFlow,
        _activeRoomId,
        activeRoomMembersFlow,
        activeRoomNudgesFlow
    ) { roomDocs, activeId, activeMembers, activeNudges ->
        roomDocs.map { doc ->
            val isSelected = doc.id == activeId || (activeId.isBlank() && roomDocs.firstOrNull()?.id == doc.id)
            StudyRoom(
                id = doc.id,
                name = doc.name,
                code = doc.inviteCode,
                description = doc.description,
                dailyGoalMinutes = doc.dailyGoalMinutes,
                members = if (isSelected) activeMembers else emptyList(),
                recentNudges = if (isSelected) activeNudges else emptyList()
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun observeRooms(): Flow<List<StudyRoom>> = combinedRoomsState
    override fun observeActiveRoomId(): Flow<String> = _activeRoomId.asStateFlow()
    override fun observeDisplayName(): Flow<String> = _displayName.asStateFlow()

    override fun selectRoom(roomId: String) {
        _activeRoomId.value = roomId
        prefs.edit().putString(KEY_ACTIVE_ROOM_ID, roomId).apply()
    }

    override suspend fun setDisplayName(name: String) {
        val clean = name.trim().take(24).ifBlank { "Scholar" }
        _displayName.value = clean
        prefs.edit().putString(KEY_DISPLAY_NAME, clean).apply()

        val uid = auth.currentUser?.uid ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                firestore.collection("users").document(uid)
                    .set(
                        mapOf(
                            "userId" to uid,
                            "displayName" to clean,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                    .await()
            }
            pushPresenceToJoinedRooms(uid)
        }
    }

    override suspend fun createRoom(
        name: String,
        description: String,
        dailyGoalMinutes: Int
    ): Result<StudyRoom> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Sign in first to create a cloud room."))
        val uid = user.uid
        val roomId = "room_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val code = generateRoomCode()
        val cleanName = name.trim().take(60).ifBlank { "Deep Focus Squad" }
        val cleanDesc = description.trim().take(160).ifBlank { "Silent camera-off focus accountability" }
        val safeGoal = dailyGoalMinutes.coerceIn(30, 960)
        val myName = _displayName.value.take(40).ifBlank { "Scholar" }

        try {
            val roomRef = firestore.collection("studyRooms").document(roomId)
            val inviteRef = firestore.collection("roomInvites").document(code)
            val memberRef = roomRef.collection("members").document(uid)

            val roomPayload = mapOf(
                "name" to cleanName,
                "inviteCode" to code,
                "description" to cleanDesc,
                "dailyGoalMinutes" to safeGoal,
                "ownerId" to uid,
                "memberUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val invitePayload = mapOf(
                "inviteCode" to code,
                "roomId" to roomId,
                "ownerId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val memberPayload = mapOf(
                "uid" to uid,
                "displayName" to myName,
                "status" to lastStatus.name,
                "activeSubject" to lastSubject.take(60),
                "subjectColorHex" to lastColorHex,
                "todayMinutes" to lastMinutes.coerceIn(0, 1440),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            // Create room first so isRoomMember() rule on /members succeeds
            roomRef.set(roomPayload).await()
            inviteRef.set(invitePayload).await()
            memberRef.set(memberPayload).await()

            selectRoom(roomId)
            val created = StudyRoom(
                id = roomId,
                name = cleanName,
                code = code,
                description = cleanDesc,
                dailyGoalMinutes = safeGoal,
                members = listOf(
                    StudyGroupMember(
                        id = uid,
                        name = myName,
                        status = lastStatus,
                        activeSubject = lastSubject,
                        subjectColorHex = lastColorHex,
                        todayMinutes = lastMinutes,
                        isMe = true
                    )
                )
            )
            Result.success(created)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.CREATE, "studyRooms/$roomId")
            Result.failure(e)
        }
    }

    override suspend fun joinRoomByCode(code: String): Result<StudyRoom> = withContext(Dispatchers.IO) {
        val user = auth.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Sign in first to join a room."))
        val uid = user.uid
        val normalized = code.trim().uppercase(Locale.US)
        if (normalized.length !in 4..12) {
            return@withContext Result.failure(
                IllegalArgumentException("Enter a 4–12 character invite code.")
            )
        }

        try {
            val inviteSnap = firestore.collection("roomInvites").document(normalized).get().await()
            if (!inviteSnap.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("No room found for invite code '$normalized'.")
                )
            }
            val roomId = inviteSnap.getString("roomId")
                ?: return@withContext Result.failure(IllegalStateException("Invalid invite record."))

            val roomRef = firestore.collection("studyRooms").document(roomId)
            roomRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val myName = _displayName.value.take(40).ifBlank { "Scholar" }
            roomRef.collection("members").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "displayName" to myName,
                    "status" to lastStatus.name,
                    "activeSubject" to lastSubject.take(60),
                    "subjectColorHex" to lastColorHex,
                    "todayMinutes" to lastMinutes.coerceIn(0, 1440),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val roomSnap = roomRef.get().await()
            val dto = roomSnap.toObject(
                FirestoreStudyRoomDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            selectRoom(roomId)

            Result.success(
                StudyRoom(
                    id = roomId,
                    name = dto?.name ?: "Squad $normalized",
                    code = dto?.inviteCode ?: normalized,
                    description = dto?.description ?: "",
                    dailyGoalMinutes = dto?.dailyGoalMinutes ?: 240,
                    members = emptyList()
                )
            )
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.UPDATE, "roomInvites/$normalized")
            Result.failure(e)
        }
    }

    override suspend fun leaveRoom(roomId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.success(Unit)
        try {
            val roomRef = firestore.collection("studyRooms").document(roomId)
            roomRef.collection("members").document(uid).delete().await()
            roomRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val remaining = combinedRoomsState.value.filterNot { it.id == roomId }
            selectRoom(remaining.firstOrNull()?.id ?: "")
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.UPDATE, "studyRooms/$roomId")
            Result.failure(e)
        }
    }

    override fun updateMyLivePresence(
        status: StudyMemberStatus,
        activeSubject: String,
        subjectColorHex: Long,
        todayMinutes: Int
    ) {
        val safeSubject = activeSubject.trim().take(60).ifBlank { "Study" }
        val safeMinutes = todayMinutes.coerceIn(0, 1440)
        val now = System.currentTimeMillis()
        val meaningfulChange = status != lastStatus ||
            safeSubject != lastSubject ||
            subjectColorHex != lastColorHex ||
            safeMinutes != lastMinutes

        lastStatus = status
        lastSubject = safeSubject
        lastColorHex = subjectColorHex
        lastMinutes = safeMinutes

        if (!meaningfulChange && (now - lastSyncTimestamp) < 30_000L) return
        val uid = auth.currentUser?.uid ?: return
        lastSyncTimestamp = now

        scope.launch {
            pushPresenceToJoinedRooms(uid)
        }
    }

    private suspend fun pushPresenceToJoinedRooms(uid: String) {
        val roomIds = combinedRoomsState.value.map { it.id }.ifEmpty {
            listOfNotNull(_activeRoomId.value.takeIf { it.isNotBlank() })
        }
        if (roomIds.isEmpty()) return

        val myName = _displayName.value.take(40).ifBlank { "Scholar" }
        val payload = mapOf(
            "uid" to uid,
            "displayName" to myName,
            "status" to lastStatus.name,
            "activeSubject" to lastSubject,
            "subjectColorHex" to lastColorHex,
            "todayMinutes" to lastMinutes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        roomIds.forEach { roomId ->
            runCatching {
                firestore.collection("studyRooms")
                    .document(roomId)
                    .collection("members")
                    .document(uid)
                    .set(payload, SetOptions.merge())
                    .await()
            }
        }
    }

    override suspend fun sendNudge(
        roomId: String,
        memberId: String,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
            ?: return@withContext Result.failure(IllegalStateException("Sign in required."))
        val activeRoom = combinedRoomsState.value.firstOrNull { it.id == roomId }
        val targetName = activeRoom?.members?.firstOrNull { it.id == memberId }?.name ?: "Friend"
        val myName = _displayName.value.take(40).ifBlank { "Scholar" }
        try {
            firestore.collection("studyRooms")
                .document(roomId)
                .collection("nudges")
                .add(
                    mapOf(
                        "fromUid" to uid,
                        "fromName" to myName,
                        "toUid" to memberId,
                        "toName" to targetName.take(40),
                        "message" to message.trim().take(200),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            FirestoreErrorHandler.logNonFatal(e, OperationType.CREATE, "studyRooms/$roomId/nudges")
            Result.failure(e)
        }
    }

    private fun loadInitialDisplayName(): String {
        val saved = prefs.getString(KEY_DISPLAY_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val google = auth.currentUser?.displayName?.trim()?.take(24)
        if (!google.isNullOrBlank()) return google
        val emailPrefix = auth.currentUser?.email?.substringBefore("@")?.trim()?.take(24)
        return emailPrefix?.ifBlank { "Scholar" } ?: "Scholar"
    }

    private fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    companion object {
        private const val PREFS_NAME = "firestore_groups_prefs"
        private const val KEY_ACTIVE_ROOM_ID = "active_room_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
