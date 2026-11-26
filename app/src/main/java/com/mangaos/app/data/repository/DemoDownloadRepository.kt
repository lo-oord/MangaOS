package com.mangaos.app.data.repository

import android.content.Context
import com.mangaos.app.R
import com.mangaos.app.data.local.DemoCatalogue
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadStatus
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.repository.DownloadRepository
import com.mangaos.app.domain.repository.LocalStorageRepository
import com.mangaos.app.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.UUID

/**
 * Download engine skeleton.
 *
 * Phase 1 has no manga source, so progress is simulated per job. Every feature the real
 * implementation needs is already here and exercised by the UI: independent ids, queueing,
 * pause/resume, retry, cancel, per-job progress, completion hand-off to local storage and
 * notifications. Swapping [runJob] for a network downloader is the only remaining step.
 */
class DemoDownloadRepository(
    private val scope: CoroutineScope,
    private val localStorage: LocalStorageRepository,
    private val notifications: NotificationRepository,
    private val context: Context,
) : DownloadRepository {

    private val _downloads = MutableStateFlow(DemoCatalogue.seedDownloads())
    override val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    init {
        _downloads.value
            .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
            .forEach { startJob(it.id) }
    }

    override fun enqueue(manga: Manga, chapterNumber: String) {
        val download = Download(
            id = UUID.randomUUID().toString(),
            mangaId = manga.id,
            mangaTitle = manga.title,
            chapterNumber = chapterNumber,
            localCoverRes = manga.localCoverRes,
            status = DownloadStatus.QUEUED,
            totalBytes = 18_000_000L + Random.nextLong(0, 12_000_000L),
        )
        _downloads.update { it + download }
        notifications.add(
            context.getString(R.string.notif_started_title),
            context.getString(R.string.notif_started_message, download.mangaTitle, download.chapterNumber),
            NotificationType.DOWNLOAD_STATUS,
        )
        startJob(download.id)
    }

    override fun pause(downloadId: String) {
        jobs.remove(downloadId)?.cancel()
        update(downloadId) { it.copy(status = DownloadStatus.PAUSED) }
    }

    override fun resume(downloadId: String) {
        val current = _downloads.value.firstOrNull { it.id == downloadId } ?: return
        if (current.status == DownloadStatus.COMPLETED) return
        startJob(downloadId)
    }

    override fun retry(downloadId: String) {
        jobs.remove(downloadId)?.cancel()
        update(downloadId) {
            it.copy(status = DownloadStatus.QUEUED, progress = 0f, downloadedBytes = 0L)
        }
        startJob(downloadId)
    }

    override fun cancel(downloadId: String) {
        jobs.remove(downloadId)?.cancel()
        update(downloadId) { it.copy(status = DownloadStatus.CANCELLING) }
        scope.launch {
            delay(320)
            update(downloadId) {
                if (it.status == DownloadStatus.CANCELLING) {
                    it.copy(status = DownloadStatus.CANCELLED, progress = 0f, downloadedBytes = 0L)
                } else {
                    it
                }
            }
        }
    }

    override fun remove(downloadId: String) {
        jobs.remove(downloadId)?.cancel()
        _downloads.update { list -> list.filterNot { it.id == downloadId } }
    }

    private fun startJob(downloadId: String) {
        jobs[downloadId]?.cancel()
        jobs[downloadId] = scope.launch {
            update(downloadId) { it.copy(status = DownloadStatus.DOWNLOADING) }
            while (isActive) {
                delay(220)
                val current = _downloads.value.firstOrNull { it.id == downloadId } ?: return@launch
                if (current.status != DownloadStatus.DOWNLOADING) return@launch

                val next = (current.progress + 0.035f + Random.nextFloat() * 0.02f).coerceAtMost(1f)
                if (next >= 1f) {
                    finish(current)
                    return@launch
                }
                update(downloadId) {
                    it.copy(progress = next, downloadedBytes = (it.totalBytes * next).toLong())
                }
            }
        }
    }

    private suspend fun finish(download: Download) {
        update(download.id) {
            it.copy(status = DownloadStatus.COMPLETED, progress = 1f, downloadedBytes = it.totalBytes)
        }
        val manga = DemoCatalogue.mangaById(download.mangaId)
        if (manga != null) {
            localStorage.saveChapter(
                manga,
                Chapter(
                    id = "${download.mangaId}-${download.chapterNumber}",
                    mangaId = download.mangaId,
                    number = download.chapterNumber,
                    publishedAt = System.currentTimeMillis(),
                    downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                    sizeBytes = download.totalBytes,
                    localPath = "mangaos/${download.mangaId}/${download.chapterNumber}.cbz",
                ),
            )
        }
        notifications.add(
            context.getString(R.string.notif_completed_title),
            context.getString(R.string.notif_completed_message, download.mangaTitle, download.chapterNumber),
            NotificationType.DOWNLOAD_COMPLETED,
        )
        // Keep the finished row visible for a moment, then hand it over to local storage.
        delay(1400)
        _downloads.update { list -> list.filterNot { it.id == download.id } }
        jobs.remove(download.id)
    }

    private fun update(downloadId: String, transform: (Download) -> Download) {
        _downloads.update { list ->
            list.map { if (it.id == downloadId) transform(it) else it }
        }
    }
}
