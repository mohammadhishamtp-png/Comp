package com.adhdcompanion.focus.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Scholar",
    val email: String = "",
    val dailyGoalMinutes: Int = 240,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreSubjectDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val name: String = "",
    val colorHex: Long = 0xFF38BDF8L,
    val dailyTargetMinutes: Int = 90,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreSessionDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: Long = 0L,
    val subjectName: String = "",
    val subjectColorHex: Long = 0xFF38BDF8L,
    val mode: String = "STOPWATCH",
    val startMillis: Long = 0L,
    val endMillis: Long = 0L,
    val durationSeconds: Int = 0,
    val dayEpoch: Long = 0L,
    val interruptionsCount: Int = 0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreTodoDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val subjectId: Long = 0L,
    val subjectName: String = "",
    val subjectColorHex: Long = 0xFF2EE6A6L,
    val title: String = "",
    val dayEpoch: Long = 0L,
    val completed: Boolean = false,
    val estimatedMinutes: Int = 30,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreExamGoalDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val localId: Long = 0L,
    val title: String = "",
    val targetDateEpoch: Long = 0L,
    val targetDailyMinutes: Int = 240,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreDayBlockDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dayEpoch: Long = 0L,
    val title: String = "",
    val why: String = "",
    val microStep: String = "",
    val startMinuteOfDay: Int = 0,
    val durationMinutes: Int = 30,
    val category: String = "STUDY",
    val decompressionBuffer: Boolean = false,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreBrainDumpDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val text: String = "",
    val createdAtMillis: Long = 0L,
    val sentToPlanner: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreStudyRoomDoc(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val description: String = "",
    val dailyGoalMinutes: Int = 240,
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreRoomMemberDoc(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "Scholar",
    val status: String = "OFFLINE",
    val activeSubject: String = "Study",
    val subjectColorHex: Long = 0xFF2EE6A6L,
    val todayMinutes: Int = 0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreNudgeDoc(
    @DocumentId val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val toUid: String = "",
    val toName: String = "",
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
