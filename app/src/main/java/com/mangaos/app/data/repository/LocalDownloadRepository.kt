package com.mangaos.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mangaos.app.R
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadStatus
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.repository.DownloadRepository
import com.mangaos.app.domain.repository.LocalStorageRepository
import com.mangaos.app.domain.repository.NotificationRepository
import com.mangaos.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Download queue. Chapter pages come from the extension that owns the work, so a job can
 * only run when that source can serve pages. Today the queue records the attempt and the
 * honest failure instead of pretending to download bytes; pause/resume/retry/cancel are
 * fully implemented against the queue state.
 */
class LocalDownloadRepository(
    private val scope: CoroutineScope,
    @Suppress("unused") private val localStorage: LocalStorageRepository,
    private val notifications: NotificationRepository,
    private val context: Context,
    private val settings: SettingsRepository,
) : DownloadRepository {

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    override val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    override fun enqueue(manga: Manga, chapterNumber: String) {
        val download = Download(
            id = UUID.randomUUID().toString(),
            mangaId = manga.key,
            mangaTitle = manga.title,
            chapterNumber = chapterNumber,
            status = DownloadStatus.QUEUED,
        )
        _downloads.update { it + download }
        scope.launch {
            val preferences = settings.appSettings.first()
            if (preferences.wifiOnlyDownloads && !isOnWifi()) {
                update(download.id) { it.copy(status = DownloadStatus.FAILED) }
                notify(
                    preferences,
                    context.getString(R.string.notif_wifi_required_title),
                    context.getString(
                        R.string.notif_wifi_required_message,
                        download.mangaTitle,
                        download.chapterNumber,
                    ),
                )
                return@launch
            }
            notify(
                preferences,
                context.getString(R.string.notif_started_title),
                context.getString(
                    R.string.notif_started_message,
                    download.mangaTitle,
                    download.chapterNumber,
                ),
            )
            update(download.id) { it.copy(status = DownloadStatus.FAILED) }
            notify(
                preferences,
                context.getString(R.string.notif_failed_title),
                context.getString(
                    R.string.notif_failed_message,
                    download.mangaTitle,
                    download.chapterNumber,
                ),
            )
        }
    }

    override fun pause(downloadId: String) {
        update(downloadId) {
            if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) {
                it.copy(status = DownloadStatus.PAUSED)
            } else {
                it
            }
        }
    }

    override fun resume(downloadId: String) {
        update(downloadId) {
            if (it.status == DownloadStatus.PAUSED) it.copy(status = DownloadStatus.QUEUED) else it
        }
    }

    override fun retry(downloadId: String) {
        update(downloadId) {
            if (it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED) {
                it.copy(status = DownloadStatus.FAILED, progress = 0f, downloadedBytes = 0L)
            } else {
                it
            }
        }
    }

    override fun cancel(downloadId: String) {
        update(downloadId) {
            if (it.status == DownloadStatus.COMPLETED) it else it.copy(status = DownloadStatus.CANCELLED)
        }
    }

    override fun remove(downloadId: String) {
        _downloads.update { list -> list.filterNot { it.id == downloadId } }
    }

    private fun notify(
        preferences: com.mangaos.app.domain.model.AppSettings,
        title: String,
        message: String,
    ) {
        if (!preferences.notificationsEnabled || !preferences.downloadNotifications) return
        notifications.add(title, message, NotificationType.DOWNLOAD_STATUS)
    }

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun update(downloadId: String, transform: (Download) -> Download) {
        _downloads.update { list ->
            list.map { if (it.id == downloadId) transform(it) else it }
        }
    }
}
