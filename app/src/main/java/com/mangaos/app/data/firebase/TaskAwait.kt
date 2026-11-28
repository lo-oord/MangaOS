package com.mangaos.app.data.firebase

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridges a Play Services [Task] to a coroutine without pulling in another dependency.
 *
 * The listener is registered even for an already-complete task, so a cached result resolves
 * immediately instead of hanging.
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

/** The same bridge for writes and sign-in, which only report success. */
suspend fun Task<*>.awaitUnit(): Unit = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) {
            continuation.resume(Unit)
        } else {
            continuation.resumeWithException(
                task.exception ?: IllegalStateException("Firebase task failed without an exception"),
            )
        }
    }
}
