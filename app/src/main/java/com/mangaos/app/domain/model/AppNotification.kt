package com.mangaos.app.domain.model

/**
 * An in-app notification. Only reader-facing events live here: a new chapter of a work in the
 * library, or a system message. Download progress is an Android notification owned by the
 * download manager and is deliberately absent from this model.
 */
data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val createdAt: Long,
    val isRead: Boolean = false,
    /** Source + manga identify the destination exactly; titles are never used as keys. */
    val sourceId: String? = null,
    val mangaId: String? = null,
    val chapterNumber: String? = null,
    val coverUrl: String? = null,
    /** App-private cover copy for an offline-readable thumbnail. */
    val thumbnailPath: String? = null,
) {
    val coverModel: String? get() = thumbnailPath ?: coverUrl
    val mangaKey: String? get() = if (sourceId != null && mangaId != null) "$sourceId/$mangaId" else null
}

enum class NotificationType {
    NEW_CHAPTER,
    SYSTEM,
}
