package com.mangaos.app.domain.model

/**
 * One download task. A task always belongs to exactly one work and owns one or more chapters,
 * so selecting ten chapters creates one visible task while every chapter keeps its own state.
 */
data class Download(
    val id: String,
    /** Full metadata snapshot, so the task and the local library stay readable offline. */
    val manga: Manga,
    val chapters: List<DownloadChapter> = emptyList(),
    val createdAt: Long = 0L,
    /** App-private copy of the cover used by notifications and the offline library. */
    val localCoverPath: String? = null,
    /** Task-level message shown when the task needs attention (partial failure, pause...). */
    val error: String? = null,
) {
    val mangaId: String get() = manga.key
    val sourceId: String get() = manga.sourceId
    val mangaTitle: String get() = manga.title
    val coverUrl: String? get() = manga.coverUrl

    /** Local cover first: it is the only model guaranteed to load without a network. */
    val coverModel: String? get() = localCoverPath ?: coverUrl

    val state: DownloadState get() = downloadState(chapters)
    val progress: Float get() = downloadProgress(chapters)
    val completedChapters: Int get() = chapters.count { it.state == DownloadChapterState.COMPLETED }
    val totalChapters: Int get() = chapters.size
    val downloadedBytes: Long get() = chapters.sumOf { it.bytes }

    /** A task that the scheduler may still need to run. */
    val hasPendingChapters: Boolean get() = chapters.any { it.state == DownloadChapterState.QUEUED }

    fun chapter(chapterId: String): DownloadChapter? =
        chapters.firstOrNull { it.chapterId == chapterId }
}

/** State of one chapter inside a task. */
data class DownloadChapter(
    val chapterId: String,
    val number: String,
    val title: String? = null,
    val state: DownloadChapterState = DownloadChapterState.QUEUED,
    val pageCount: Int = 0,
    val downloadedPages: Int = 0,
    val bytes: Long = 0L,
    /** Chapter directory once every page was written and verified. */
    val localDir: String? = null,
    val error: String? = null,
) {
    val progress: Float
        get() = when (state) {
            DownloadChapterState.COMPLETED -> 1f
            DownloadChapterState.DOWNLOADING ->
                if (pageCount > 0) (downloadedPages.toFloat() / pageCount).coerceIn(0f, 1f) else 0f
            else -> 0f
        }
}

enum class DownloadChapterState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/** Task state derived from the chapters; there is no separate task state to desynchronise. */
enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    PARTIAL,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/** Chapter-weighted progress: a downloaded chapter counts fully, the active one by its pages. */
fun downloadProgress(chapters: List<DownloadChapter>): Float {
    if (chapters.isEmpty()) return 0f
    val done = chapters.sumOf { chapter ->
        when (chapter.state) {
            DownloadChapterState.COMPLETED -> 1.0
            DownloadChapterState.DOWNLOADING ->
                if (chapter.pageCount > 0) {
                    (chapter.downloadedPages.toDouble() / chapter.pageCount).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
            else -> 0.0
        }
    }
    return (done / chapters.size).coerceIn(0.0, 1.0).toFloat()
}

fun downloadState(chapters: List<DownloadChapter>): DownloadState {
    if (chapters.isEmpty()) return DownloadState.QUEUED
    if (chapters.all { it.state == DownloadChapterState.COMPLETED }) return DownloadState.COMPLETED
    if (chapters.any { it.state == DownloadChapterState.DOWNLOADING }) return DownloadState.DOWNLOADING
    if (chapters.any { it.state == DownloadChapterState.QUEUED }) return DownloadState.QUEUED
    if (chapters.any { it.state == DownloadChapterState.PAUSED }) return DownloadState.PAUSED
    if (chapters.any { it.state == DownloadChapterState.FAILED }) {
        return if (chapters.any { it.state == DownloadChapterState.COMPLETED }) {
            DownloadState.PARTIAL
        } else {
            DownloadState.FAILED
        }
    }
    if (chapters.all { it.state == DownloadChapterState.CANCELLED }) return DownloadState.CANCELLED
    if (chapters.any { it.state == DownloadChapterState.CANCELLED }) return DownloadState.PARTIAL
    return DownloadState.QUEUED
}

/** Finds the task that currently owns a chapter, so the chapter list shows one source of truth. */
fun List<Download>.findChapter(
    mangaKey: String,
    chapterId: String,
): Pair<Download, DownloadChapter>? {
    for (task in this) {
        if (task.manga.key != mangaKey) continue
        val chapter = task.chapter(chapterId) ?: continue
        return task to chapter
    }
    return null
}
