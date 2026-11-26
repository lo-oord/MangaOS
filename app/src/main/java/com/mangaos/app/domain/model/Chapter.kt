package com.mangaos.app.domain.model

/** Chapter metadata returned by the extension that owns the work. */
data class Chapter(
    val id: String,
    val mangaId: String,
    val number: String,
    val title: String? = null,
    val publishedAt: Long? = null,
    val isRead: Boolean = false,
    val downloadStatus: ChapterDownloadStatus = ChapterDownloadStatus.NOT_DOWNLOADED,
    val sizeBytes: Long? = null,
    val localPath: String? = null,
)

enum class ChapterDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

/** A work that has at least one chapter saved on the device. */
data class StoredWork(
    val manga: Manga,
    val chapters: List<Chapter>,
)

enum class ChapterSort { NEWEST_FIRST, OLDEST_FIRST }
