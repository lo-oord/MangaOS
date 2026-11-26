package com.mangaos.app.domain.model

/** Chapter metadata — ready for the reader and download engine of later phases. */
data class Chapter(
    val id: String,
    val mangaId: String,
    val number: String,
    val title: String? = null,
    val publishedAt: Long = 0L,
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
