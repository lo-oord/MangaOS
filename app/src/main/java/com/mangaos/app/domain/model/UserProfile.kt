package com.mangaos.app.domain.model

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUri: String? = null,
    val sessionType: SessionType = SessionType.ACCOUNT,
)

enum class SessionType { GUEST, ACCOUNT }
