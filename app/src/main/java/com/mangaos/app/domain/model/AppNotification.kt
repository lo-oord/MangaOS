package com.mangaos.app.domain.model

data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val createdAt: Long,
    val isRead: Boolean = false,
)

enum class NotificationType {
    DOWNLOAD_COMPLETED,
    DOWNLOAD_FAILED,
    DOWNLOAD_STATUS,
    SYSTEM,
}
