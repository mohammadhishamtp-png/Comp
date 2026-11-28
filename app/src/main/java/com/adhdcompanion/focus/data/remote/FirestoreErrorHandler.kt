package com.adhdcompanion.focus.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreSync"

    fun handleFirestoreError(
        error: Exception,
        operationType: OperationType,
        path: String?
    ): Nothing {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val detail = buildString {
            append("Firestore ")
            append(operationType.name)
            append(" failed")
            if (!path.isNullOrBlank()) {
                append(" at path='").append(path).append('\'')
            }
            append(" uid=").append(currentUser?.uid ?: "signed_out")
            append(" msg=").append(error.message ?: error.javaClass.simpleName)
        }
        Log.e(TAG, detail, error)
        throw IllegalStateException(detail, error)
    }

    fun logNonFatal(
        error: Exception,
        operationType: OperationType,
        path: String?
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.w(
            TAG,
            "Firestore ${operationType.name} warning at path=$path uid=${currentUser?.uid}: ${error.message}",
            error
        )
    }
}
