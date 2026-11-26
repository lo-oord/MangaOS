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

/**
 * A work that has at least one chapter saved on the device. There is exactly one record per
 * [Manga.key]; every finished chapter is merged into it instead of creating a second card.
 */
data class StoredWork(
    val manga: Manga,
    val chapters: List<Chapter>,
    /** App-private cover copy, so the offline card never depends on the network. */
    val localCoverPath: String? = null,
    val savedAt: Long = 0L,
) {
    val coverModel: String? get() = localCoverPath ?: manga.coverUrl
    val sizeBytes: Long get() = chapters.sumOf { it.sizeBytes ?: 0L }
    val latestChapterNumber: String? get() = chapters.firstOrNull()?.number
}

enum class ChapterSort { NEWEST_FIRST, OLDEST_FIRST }
