package com.mangaos.app.domain.model

/** One download job. Every job owns its own id, status and progress. */
data class Download(
    val id: String,
    /** [Manga.key], so source + manga id. */
    val mangaId: String,
    val sourceId: String = "",
    val mangaTitle: String,
    val chapterId: String = "",
    val chapterNumber: String,
    val coverUrl: String? = null,
    @androidx.annotation.DrawableRes val localCoverRes: Int? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    /** App-private chapter directory once every page was written. */
    val localDir: String? = null,
    val error: String? = null,
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    FAILED,
    COMPLETED,
    CANCELLING,
    CANCELLED,
}
