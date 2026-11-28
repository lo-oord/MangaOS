package com.mangaos.app.data.firebase

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Owns the account document and the username reservation.
 *
 * The username is unique because a transaction creates `usernames/{normalized}` only when no
 * other UID owns it. Two devices asking at the same time therefore cannot both succeed, which a
 * client-side check alone could never guarantee.
 */
class AccountRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    private fun userDoc(uid: String) = db.collection(COLLECTION_USERS).document(uid)
    private fun usernameDoc(name: String) = db.collection(COLLECTION_USERNAMES).document(normalize(name))
    private fun badgeDoc(uid: String) = db.collection(COLLECTION_BADGES).document(uid)

    fun account(uid: String): Flow<AccountDoc?> = callbackFlow {
        val registration = userDoc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.let { AccountDoc.from(uid, it.data.orEmpty()) })
        }
        awaitClose { registration.remove() }
    }

    /**
     * The verification badge is a separate, admin-only document. The app can read it, never write
     * it, so a user cannot grant themselves verification.
     */
    fun badge(uid: String): Flow<Boolean> = callbackFlow {
        val registration = badgeDoc(uid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.getBoolean(FIELD_VERIFIED) == true)
        }
        awaitClose { registration.remove() }
    }

    suspend fun ensureAccount(uid: String, email: String?, displayName: String?) {
        val reference = userDoc(uid)
        val snapshot = reference.get().awaitResult()
        if (snapshot != null && snapshot.exists()) return
        val now = System.currentTimeMillis()
        reference.set(
            mapOf(
                "uid" to uid,
                "email" to (email ?: ""),
                "displayName" to (displayName ?: ""),
                "username" to "",
                "setupCompleted" to false,
                "createdAt" to now,
                "updatedAt" to now,
            ),
        ).awaitUnit()
    }

    suspend fun isUsernameAvailable(username: String, uid: String): Boolean {
        val snapshot = usernameDoc(username).get().awaitResult() ?: return true
        if (!snapshot.exists()) return true
        return snapshot.getString("uid") == uid
    }

    /** Reserves [username] for [uid]; false means somebody else already owns it. */
    suspend fun claimUsername(uid: String, username: String, previous: String?): Boolean {
        val clean = normalize(username)
        if (clean.isBlank()) return false
        val claimed = db.runTransaction<Boolean> { transaction ->
            val reference = usernameDoc(clean)
            val existing = transaction.get(reference)
            val owner = existing.getString("uid")
            if (existing.exists() && owner != null && owner != uid) {
                false
            } else {
                transaction.set(reference, mapOf("uid" to uid, "updatedAt" to System.currentTimeMillis()))
                true
            }
        }.awaitResult() ?: false
        if (claimed) {
            val old = previous?.let(::normalize)
            if (!old.isNullOrBlank() && old != clean) {
                runCatching { releaseUsername(old, uid) }
            }
        }
        return claimed
    }

    suspend fun releaseUsername(username: String, uid: String) {
        val reference = usernameDoc(username)
        val snapshot = reference.get().awaitResult() ?: return
        if (snapshot.getString("uid") == uid) reference.delete().awaitUnit()
    }

    /** Field-level update: a profile edit never replaces the whole document. */
    suspend fun updateAccount(uid: String, fields: Map<String, Any?>) {
        val payload = HashMap<String, Any>()
        fields.forEach { (key, value) -> if (value != null) payload[key] = value }
        payload["updatedAt"] = System.currentTimeMillis()
        userDoc(uid).update(payload).awaitUnit()
    }

    suspend fun saveAvatar(uid: String, base64: String) {
        updateAccount(uid, mapOf("photo" to base64))
    }

    suspend fun saveDeviceToken(uid: String, token: String) {
        userDoc(uid).collection(COLLECTION_DEVICES).document(token).set(
            mapOf("token" to token, "platform" to "android", "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge(),
        ).awaitUnit()
    }

    suspend fun removeDeviceToken(uid: String, token: String) {
        runCatching {
            userDoc(uid).collection(COLLECTION_DEVICES).document(token).delete().awaitUnit()
        }
    }

    /**
     * Removes everything the account owns. Firestore does not cascade a document delete, so the
     * subcollections are emptied first; a failure is surfaced instead of reported as success.
     */
    suspend fun deleteAccountData(uid: String, username: String?) {
        val root = userDoc(uid)
        listOf(
            COLLECTION_LIBRARY,
            COLLECTION_PROGRESS,
            COLLECTION_READ,
            COLLECTION_DEVICES,
            COLLECTION_STATE,
        ).forEach { name -> deleteCollection(root.collection(name)) }
        root.delete().awaitUnit()
        if (!username.isNullOrBlank()) releaseUsername(username, uid)
    }

    private suspend fun deleteCollection(collection: CollectionReference) {
        while (true) {
            val snapshot = collection.limit(BATCH_SIZE).get().awaitResult() ?: return
            if (snapshot.isEmpty) return
            val batch = db.batch()
            snapshot.documents.forEach { document -> batch.delete(document.reference) }
            batch.commit().awaitUnit()
        }
    }

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_USERNAMES = "usernames"
        const val COLLECTION_BADGES = "badges"
        const val COLLECTION_LIBRARY = "library"
        const val COLLECTION_PROGRESS = "readingProgress"
        const val COLLECTION_READ = "readChapters"
        const val COLLECTION_DEVICES = "devices"
        const val COLLECTION_STATE = "state"
        const val FIELD_VERIFIED = "verified"
        const val BATCH_SIZE = 200L

        fun normalize(value: String): String = value.trim().lowercase()
    }
}
