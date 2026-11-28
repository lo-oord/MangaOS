package com.mangaos.app.data.firebase

/**
 * One account document (`users/{uid}`) as the app reads it. Nothing private is ever shared:
 * the document is only readable by its owner.
 */
data class AccountDoc(
    val uid: String,
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val language: String? = null,
    val country: String? = null,
    val age: Int? = null,
    /** Small compressed JPEG, base64 encoded, because the free plan has no file storage. */
    val photo: String? = null,
    val setupCompleted: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    val hasUsername: Boolean get() = !username.isNullOrBlank()

    companion object {
        fun from(uid: String, data: Map<String, Any?>): AccountDoc = AccountDoc(
            uid = uid,
            email = data["email"] as? String,
            username = (data["username"] as? String)?.takeIf { it.isNotBlank() },
            displayName = (data["displayName"] as? String)?.takeIf { it.isNotBlank() },
            language = data["language"] as? String,
            country = data["country"] as? String,
            age = (data["age"] as? Number)?.toInt(),
            photo = data["photo"] as? String,
            setupCompleted = data["setupCompleted"] as? Boolean ?: false,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
        )
    }
}

/**
 * The state every screen reads. It combines the Firebase session, the account document and the
 * verification badge, so a screen never has to look at three sources to know who is signed in.
 */
data class AccountState(
    val loading: Boolean = true,
    val uid: String? = null,
    val email: String? = null,
    val emailVerified: Boolean = false,
    val hasPassword: Boolean = false,
    val hasGoogle: Boolean = false,
    val account: AccountDoc? = null,
    val verified: Boolean = false,
) {
    val signedIn: Boolean get() = uid != null
    val isGuest: Boolean get() = uid == null
    val displayName: String
        get() = account?.displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
            ?: ""
    val username: String get() = account?.username.orEmpty()
    val needsSetup: Boolean get() = signedIn && (account == null || !account.setupCompleted || !account.hasUsername)
}
