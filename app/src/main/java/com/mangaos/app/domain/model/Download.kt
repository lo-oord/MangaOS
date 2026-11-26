package com.mangaos.app.domain.model

/** One download job. Every job owns its own id, status and progress. */
data class Download(
    val id: String,
    val mangaId: String,
    val mangaTitle: String,
    val chapterNumber: String,
    @androidx.annotation.DrawableRes val localCoverRes: Int? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
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
