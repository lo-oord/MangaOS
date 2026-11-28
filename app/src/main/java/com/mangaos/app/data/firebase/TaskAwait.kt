package com.mangaos.app.data.firebase

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridges a Play Services [Task] to a coroutine without pulling in another dependency.
 *
 * The listener is always registered, even for an already-complete task, so a cached result is
 * never dropped.
 */
suspend fun <T> Task<T>.awaitResult(): T? = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed without an exception"),
            )
        }
    }
}

/** The same bridge for tasks that only report success (Firestore writes, sign-in, etc.). */
suspend fun Task<*>.awaitUnit() {
    awaitResult<Any?>()
}
