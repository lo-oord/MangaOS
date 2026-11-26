package com.mangaos.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mangaos.app.R
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadStatus
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.repository.DownloadRepository
import com.mangaos.app.domain.repository.LocalStorageRepository
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.NotificationRepository
import com.mangaos.app.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real, on-device chapter downloader.
 *
 * Pages come from the installed extension, are written one by one under
 * `filesDir/downloads`, and a manifest is kept beside them. Progress is the number of pages
 * actually written, never a timer or a guessed byte count. Queue state survives restarts.
 */
class LocalDownloadRepository(
    private val scope: CoroutineScope,
    private val mangaRepository: MangaRepository,
    private val localStorage: LocalStorageRepository,
    private val notifications: NotificationRepository,
    private val context: Context,
    private val settings: SettingsRepository,
) : DownloadRepository {

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    override val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val running = mutableMapOf<String, Job>()
    private var pumpJob: Job? = null

    init {
        _downloads.value = runCatching { decode(prefs.getString(KEY_QUEUE, null)) }
            .getOrDefault(emptyList())
            .map { download ->
                if (download.status == DownloadStatus.DOWNLOADING) {
                    download.copy(status = DownloadStatus.QUEUED, progress = 0f)
                } else {
                    download
                }
            }
        persist()
        pump()
    }

    override fun enqueue(manga: Manga, chapter: Chapter) {
        val existing = _downloads.value.firstOrNull {
            it.mangaId == manga.key && it.chapterId == chapter.id
        }
        if (existing != null) {
            if (existing.status == DownloadStatus.COMPLETED ||
                existing.status == DownloadStatus.QUEUED ||
                existing.status == DownloadStatus.DOWNLOADING
            ) {
                return
            }
            update(existing.id) {
                it.copy(
                    status = DownloadStatus.QUEUED,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    error = null,
                )
            }
        } else {
            val download = Download(
                id = UUID.randomUUID().toString(),
                mangaId = manga.key,
                sourceId = manga.sourceId,
                mangaTitle = manga.title,
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                coverUrl = manga.coverUrl,
                status = DownloadStatus.QUEUED,
            )
            _downloads.update { it + download }
            persist()
        }
        pump()
    }

    override fun localPages(mangaKey: String, chapterId: String): List<String> = runCatching {
        val directory = chapterDir(mangaKey, chapterId)
        val manifest = File(directory, MANIFEST)
        if (!manifest.exists()) return emptyList()
        val array = JSONArray(manifest.readText())
        (0 until array.length()).mapNotNull { index ->
            val name = array.optString(index)
            File(directory, name).takeIf { name.isNotBlank() && it.exists() }?.absolutePath
        }
    }.getOrDefault(emptyList())

    override fun pause(downloadId: String) {
        val job = running.remove(downloadId)
        job?.cancel()
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
        pump()
    }

    override fun retry(downloadId: String) {
        update(downloadId) {
            if (it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELLED ||
                it.status == DownloadStatus.PAUSED
            ) {
                it.copy(
                    status = DownloadStatus.QUEUED,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    error = null,
                )
            } else {
                it
            }
        }
        pump()
    }

    override fun cancel(downloadId: String) {
        running.remove(downloadId)?.cancel()
        update(downloadId) {
            if (it.status == DownloadStatus.COMPLETED) it else it.copy(status = DownloadStatus.CANCELLED)
        }
    }

    override fun remove(downloadId: String) {
        running.remove(downloadId)?.cancel()
        val item = _downloads.value.firstOrNull { it.id == downloadId } ?: return
        if (item.chapterId.isNotBlank()) {
            chapterDir(item.mangaId, item.chapterId).deleteRecursively()
            localStorage.deleteChapter(item.mangaId.substringAfter('/'), item.chapterId)
        }
        _downloads.update { list -> list.filterNot { it.id == downloadId } }
        persist()
    }

    /** One queued job at a time keeps memory use and source requests predictable. */
    private fun pump() {
        if (pumpJob?.isActive == true) return
        pumpJob = scope.launch(Dispatchers.IO) {
            while (true) {
                val next = _downloads.value.firstOrNull { it.status == DownloadStatus.QUEUED }
                    ?: break
                runDownload(next)
            }
        }
    }

    private suspend fun runDownload(download: Download) {
        val preferences = settings.appSettings.first()
        if (preferences.wifiOnlyDownloads && !isOnWifi()) {
            update(download.id) {
                it.copy(status = DownloadStatus.FAILED, error = "Wi-Fi is required")
            }
            notify(
                preferences,
                context.getString(R.string.notif_wifi_required_title),
                context.getString(
                    R.string.notif_wifi_required_message,
                    download.mangaTitle,
                    download.chapterNumber,
                ),
            )
            return
        }

        update(download.id) {
            it.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = 0L,
                error = null,
            )
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

        val chapter = Chapter(
            id = download.chapterId,
            mangaId = download.mangaId.substringAfter('/'),
            number = download.chapterNumber,
        )
        val job = scope.launch {
            try {
                val pages = mangaRepository.pages(download.sourceId, chapter.mangaId, download.chapterId)
                if (pages.isEmpty()) error("The source returned no pages")
                val headers = mangaRepository.imageHeaders(download.sourceId)
                val directory = chapterDir(download.mangaId, download.chapterId)
                if (directory.exists()) directory.deleteRecursively()
                directory.mkdirs()
                val names = mutableListOf<String>()
                var written = 0L
                pages.forEachIndexed { index, url ->
                    val extension = extensionFor(url)
                    val name = "%04d%s".format(index, extension)
                    val target = File(directory, name)
                    val request = Request.Builder()
                        .url(url)
                        .apply {
                            headers.forEach { (key, value) ->
                                if (key.isNotBlank() && value.isNotBlank()) header(key, value)
                            }
                        }
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code} for page ${index + 1}")
                        val body = response.body ?: error("Empty response for page ${index + 1}")
                        target.outputStream().use { output -> body.byteStream().copyTo(output) }
                        written += target.length()
                    }
                    names += name
                    update(download.id) {
                        it.copy(
                            progress = (index + 1f) / pages.size,
                            downloadedBytes = written,
                        )
                    }
                }
                File(directory, MANIFEST).writeText(JSONArray(names).toString())
                update(download.id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        downloadedBytes = written,
                        localDir = directory.absolutePath,
                        error = null,
                    )
                }
                localStorage.saveChapter(
                    Manga(
                        id = chapter.mangaId,
                        sourceId = download.sourceId,
                        title = download.mangaTitle,
                        coverUrl = download.coverUrl,
                    ),
                    chapter.copy(
                        downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                        sizeBytes = written,
                        localPath = directory.absolutePath,
                    ),
                )
                notify(
                    preferences,
                    context.getString(R.string.notif_completed_title),
                    context.getString(
                        R.string.notif_completed_message,
                        download.mangaTitle,
                        download.chapterNumber,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                update(download.id) {
                    if (it.status == DownloadStatus.CANCELLED) it
                    else it.copy(
                        status = DownloadStatus.FAILED,
                        error = error.message ?: error.javaClass.simpleName,
                    )
                }
                notify(
                    preferences,
                    context.getString(R.string.notif_failed_title),
                    context.getString(
                        R.string.notif_failed_message,
                        download.mangaTitle,
                        download.chapterNumber,
                    ),
                )
            } finally {
                running.remove(download.id)
            }
        }
        running[download.id] = job
        job.join()
    }

    private fun chapterDir(mangaKey: String, chapterId: String): File =
        File(File(context.filesDir, "downloads"), hash(mangaKey)).let { root ->
            File(root, hash(chapterId))
        }

    private fun extensionFor(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val extension = path.substringAfterLast('.', "").lowercase()
        return if (extension in setOf("jpg", "jpeg", "png", "webp", "gif", "avif")) ".$extension" else ".img"
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

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
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY_QUEUE, encode(_downloads.value)).apply()
    }

    private fun encode(downloads: List<Download>): String {
        val array = JSONArray()
        downloads.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("mangaId", item.mangaId)
                    put("sourceId", item.sourceId)
                    put("mangaTitle", item.mangaTitle)
                    put("chapterId", item.chapterId)
                    put("chapterNumber", item.chapterNumber)
                    item.coverUrl?.let { put("coverUrl", it) }
                    put("status", item.status.name)
                    put("progress", item.progress.toDouble())
                    put("totalBytes", item.totalBytes)
                    put("downloadedBytes", item.downloadedBytes)
                    item.localDir?.let { put("localDir", it) }
                    item.error?.let { put("error", it) }
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<Download> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Download(
                id = id,
                mangaId = json.optString("mangaId"),
                sourceId = json.optString("sourceId"),
                mangaTitle = json.optString("mangaTitle"),
                chapterId = json.optString("chapterId"),
                chapterNumber = json.optString("chapterNumber"),
                coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() },
                status = runCatching { DownloadStatus.valueOf(json.optString("status")) }
                    .getOrDefault(DownloadStatus.QUEUED),
                progress = json.optDouble("progress", 0.0).toFloat(),
                totalBytes = json.optLong("totalBytes"),
                downloadedBytes = json.optLong("downloadedBytes"),
                localDir = json.optString("localDir").takeIf { it.isNotBlank() },
                error = json.optString("error").takeIf { it.isNotBlank() },
            )
        }
    }

    private companion object {
        const val PREFS = "mangaos_downloads"
        const val KEY_QUEUE = "queue"
        const val MANIFEST = "manifest.json"
    }
}
