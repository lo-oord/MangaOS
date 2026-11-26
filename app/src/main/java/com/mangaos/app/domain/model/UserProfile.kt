package com.mangaos.app.domain.model

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUri: String? = null,
    val sessionType: SessionType = SessionType.ACCOUNT,
    /** ISO 3166-1 alpha-2 code, or null when the reader skipped it. */
    val countryCode: String? = null,
    val age: Int? = null,
    /** Preferred content language (ar/en/ja), separate from the interface language. */
    val preferredLanguage: String? = null,
)

enum class SessionType { GUEST, ACCOUNT }
