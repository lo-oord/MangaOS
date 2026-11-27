package com.mangaos.app.data.download

import android.content.Context
import android.os.SystemClock
import com.mangaos.app.data.local.AtomicFileWriter
import com.mangaos.app.data.local.CoverStore
import com.mangaos.app.data.local.toJson
import com.mangaos.app.data.local.toManga
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadChapter
import com.mangaos.app.domain.model.DownloadChapterState
import com.mangaos.app.domain.model.DownloadState
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.findChapter
import com.mangaos.app.domain.repository.DownloadRepository
import com.mangaos.app.domain.repository.LocalStorageRepository
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.SettingsRepository
import com.mangaos.app.data.notifications.DownloadNotifications
import com.mangaos.app.data.work.DownloadScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mangaos.app.sources.images.OverlayImageInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Durable download manager.
 *
 * The task list is persisted, execution runs in [DownloadWorker] (WorkManager) so a download
 * survives leaving the screen or the app, and the notification is updated in place. Task
 * records and chapter files are separate concerns: removing a record keeps the files, and
 * only an explicit "delete files" call removes downloaded chapters.
 */
class LocalDownloadRepository(
    private val scope: CoroutineScope,
    private val mangaRepository: MangaRepository,
    private val localStorage: LocalStorageRepository,
    private val context: Context,
    private val settings: SettingsRepository,
    private val scheduler: DownloadScheduler,
    private val notifier: DownloadNotifications,
    private val coverStore: CoverStore,
) : DownloadRepository {

    private val _downloads = MutableStateFlow<List<Download>>(emptyList())
    override val downloads: StateFlow<List<Download>> = _downloads.asStateFlow()

    private val file = File(context.filesDir, QUEUE_RECORDS)
    private val root = StorageInspector.root(context)
    private val lock = Any()
    private val lastPersistedAt = mutableMapOf<String, Long>()
    private val lastNotifiedAt = mutableMapOf<String, Long>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        // Downloaded pages go through the same overlay pipeline as the reader, so a page whose
        // translation is a separate text layer is stored complete.
        .addInterceptor(OverlayImageInterceptor)
        .build()

    @Volatile
    private var appSettings = AppSettings()

    init {
        _downloads.value = runCatching { decode(AtomicFileWriter.read(file)) }
            .getOrDefault(emptyList())
            .map { sanitize(it) }
        persist(force = true)
        notifier.ensureChannels(appSettings)
        scope.launch {
            settings.appSettings.collect { preferences ->
                appSettings = preferences
                notifier.ensureChannels(preferences)
            }
        }
        scope.launch { pumpIfPending() }
    }

    // ------------------------------------------------------------------ public API

    override fun enqueue(manga: Manga, chapters: List<Chapter>) {
        if (manga.key.isBlank() || manga.id.isBlank() || chapters.isEmpty()) return
        val wanted = chapters
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .filterNot { chapter ->
                val existing = _downloads.value.findChapter(manga.key, chapter.id)?.second
                existing != null && existing.state in ACTIVE_OR_DONE
            }
        if (wanted.isEmpty()) return

        val activeTask = _downloads.value.firstOrNull {
            it.manga.key == manga.key && it.state != DownloadState.COMPLETED &&
                it.state != DownloadState.CANCELLED
        }

        val task = if (activeTask != null) {
            val known = activeTask.chapters.mapTo(HashSet()) { it.chapterId }
            val additions = wanted
                .filterNot { it.id in known }
                .map { it.toDownloadChapter() }
            if (additions.isEmpty()) return
            updateTask(activeTask.id) { current ->
                current.copy(chapters = current.chapters + additions, error = null)
            } ?: return
        } else {
            val created = Download(
                id = UUID.randomUUID().toString(),
                manga = manga,
                chapters = wanted.map { it.toDownloadChapter() },
                createdAt = System.currentTimeMillis(),
            )
            synchronized(lock) {
                val next = listOf(created) + _downloads.value
                commit(next)
            }
            created
        }

        cacheCover(task.id)
        notifier.notify(findTask(task.id) ?: task, appSettings)
        scheduler.pump(unmeteredOnly = appSettings.wifiOnlyDownloads)
    }

    override fun localPages(mangaKey: String, chapterId: String): List<String> {
        val taskDirectory = _downloads.value
            .findChapter(mangaKey, chapterId)
            ?.second
            ?.localDir
            ?.let { File(it) }
        if (taskDirectory != null && ChapterFiles.isComplete(taskDirectory)) {
            return ChapterFiles.pages(taskDirectory)
        }
        val recordDirectory = localStorage
            .localChapter(mangaKey, chapterId)
            ?.localPath
            ?.let { File(it) }
        if (recordDirectory != null && ChapterFiles.isComplete(recordDirectory)) {
            return ChapterFiles.pages(recordDirectory)
        }
        return emptyList()
    }

    override fun pause(downloadId: String) {
        scheduler.cancel()
        val task = updateTask(downloadId) { current ->
            current.copy(
                chapters = current.chapters.map {
                    if (it.state == DownloadChapterState.QUEUED ||
                        it.state == DownloadChapterState.DOWNLOADING
                    ) {
                        it.copy(state = DownloadChapterState.PAUSED, error = null)
                    } else {
                        it
                    }
                },
                error = null,
            )
        } ?: return
        persist(force = true)
        notifier.notify(task, appSettings)
    }

    override fun resume(downloadId: String) {
        val task = updateTask(downloadId) { current ->
            current.copy(
                chapters = current.chapters.map {
                    if (it.state == DownloadChapterState.PAUSED) {
                        it.copy(state = DownloadChapterState.QUEUED, error = null)
                    } else {
                        it
                    }
                },
                error = null,
            )
        } ?: return
        persist(force = true)
        notifier.notify(task, appSettings)
        scheduler.pump(unmeteredOnly = appSettings.wifiOnlyDownloads)
    }

    override fun retry(downloadId: String) {
        val task = updateTask(downloadId) { current ->
            current.copy(
                chapters = current.chapters.map {
                    if (it.state == DownloadChapterState.FAILED ||
                        it.state == DownloadChapterState.CANCELLED
                    ) {
                        it.copy(
                            state = DownloadChapterState.QUEUED,
                            downloadedPages = 0,
                            pageCount = 0,
                            bytes = 0L,
                            localDir = null,
                            error = null,
                        )
                    } else {
                        it
                    }
                },
                error = null,
            )
        } ?: return
        if (!task.hasPendingChapters) return
        persist(force = true)
        notifier.notify(task, appSettings)
        scheduler.pump(unmeteredOnly = appSettings.wifiOnlyDownloads)
    }

    override fun cancel(downloadId: String) {
        scheduler.cancel()
        val task = updateTask(downloadId) { current ->
            current.copy(
                chapters = current.chapters.map {
                    if (it.state == DownloadChapterState.COMPLETED) {
                        it
                    } else {
                        it.copy(state = DownloadChapterState.CANCELLED)
                    }
                },
                error = null,
            )
        } ?: return
        persist(force = true)
        notifier.notify(task, appSettings)
    }

    override fun removeRecord(downloadId: String) {
        val task = findTask(downloadId) ?: return
        if (task.chapters.any {
                it.state == DownloadChapterState.QUEUED ||
                    it.state == DownloadChapterState.DOWNLOADING ||
                    it.state == DownloadChapterState.PAUSED
            }
        ) {
            // Never drop a record that WorkManager might still be executing.
            pause(downloadId)
            return
        }
        synchronized(lock) {
            commit(_downloads.value.filterNot { it.id == downloadId })
        }
        notifier.cancel(downloadId)
    }

    override fun deleteLocal(downloadId: String) {
        val task = findTask(downloadId) ?: return
        if (com.mangaos.app.core.session.ReaderSession.isReading(task.manga.key)) return
        task.chapters.forEach { chapter ->
            deleteChapterDirectory(task.manga.key, chapter.chapterId)
            localStorage.deleteChapter(task.manga.key, chapter.chapterId)
        }
        synchronized(lock) {
            commit(_downloads.value.filterNot { it.id == downloadId })
        }
        notifier.cancel(downloadId)
    }

    override fun deleteLocalChapter(mangaKey: String, chapterId: String) {
        if (com.mangaos.app.core.session.ReaderSession.isReading(mangaKey)) return
        deleteChapterDirectory(mangaKey, chapterId)
        localStorage.deleteChapter(mangaKey, chapterId)
        val pair = _downloads.value.findChapter(mangaKey, chapterId) ?: return
        val task = pair.first
        val remaining = task.chapters.filterNot { it.chapterId == chapterId }
        synchronized(lock) {
            val next = if (remaining.isEmpty()) {
                _downloads.value.filterNot { it.id == task.id }
            } else {
                _downloads.value.map { if (it.id == task.id) it.copy(chapters = remaining) else it }
            }
            commit(next)
        }
        if (remaining.isEmpty()) notifier.cancel(task.id)
    }

    // ------------------------------------------------------------------ queue execution

    /** Runs every queued chapter, one task at a time. Returns false when it must wait. */
    suspend fun runQueue(onForeground: suspend (Download) -> Unit): Boolean {
        while (true) {
            val task = _downloads.value.firstOrNull { it.hasPendingChapters } ?: return true
            val preferences = settings.appSettings.first()
            appSettings = preferences
            if (preferences.wifiOnlyDownloads && !isOnWifi()) {
                notifier.notify(task, preferences)
                return false
            }
            runCatching { onForeground(task) }
            processTask(task.id)
            if (_downloads.value.none { it.hasPendingChapters }) return true
        }
    }

    fun hasPendingWork(): Boolean = _downloads.value.any { it.hasPendingChapters }

    fun notificationFor(task: Download) = notifier.build(task)

    /** Storage state for the UI: never inferred from a failure message. */
    fun storageStatus(): StorageStatus = StorageInspector.check(context)

    /**
     * Maps a download failure to the message that matches the real cause, so a missing folder
     * or a refused network request is never reported as a full disk.
     */
    private fun failureMessage(error: Throwable): String {
        (error as? StorageException)?.failure?.let { failure ->
            return context.getString(
                when (failure) {
                    StorageFailure.NO_SPACE -> com.mangaos.app.R.string.download_error_storage
                    StorageFailure.UNAVAILABLE, StorageFailure.READ_ONLY,
                    StorageFailure.CANNOT_CREATE ->
                        com.mangaos.app.R.string.download_error_storage_access
                    StorageFailure.CANNOT_WRITE -> com.mangaos.app.R.string.download_error_write
                },
            )
        }
        val message = error.message.orEmpty()
        Regex("HTTP (\\d{3})").find(message)?.groupValues?.getOrNull(1)?.let { code ->
            return context.getString(com.mangaos.app.R.string.download_error_http, code)
        }
        return when {
            message.startsWith("Could not store page") || message.startsWith("Empty page") ->
                context.getString(com.mangaos.app.R.string.download_error_write)
            error is java.net.UnknownHostException ||
                error is java.net.SocketTimeoutException ||
                error is java.net.ConnectException ||
                error is javax.net.ssl.SSLException ||
                message.contains("timeout", ignoreCase = true) ||
                message.contains("Unable to resolve host", ignoreCase = true) ->
                context.getString(com.mangaos.app.R.string.download_error_network)
            else -> message.ifBlank {
                context.getString(com.mangaos.app.R.string.download_error_generic)
            }
        }
    }

    fun pumpIfPending() {
        if (!hasPendingWork()) return
        scheduler.pump(unmeteredOnly = appSettings.wifiOnlyDownloads)
    }

    private suspend fun processTask(taskId: String) {
        cacheCover(taskId)
        updateTask(taskId) { it.copy(error = null) }
        while (true) {
            val task = findTask(taskId) ?: return
            val chapter = task.chapters.firstOrNull { it.state == DownloadChapterState.QUEUED }
                ?: break
            setChapter(taskId, chapter.chapterId) {
                it.copy(
                    state = DownloadChapterState.DOWNLOADING,
                    pageCount = 0,
                    downloadedPages = 0,
                    bytes = 0L,
                    localDir = null,
                    error = null,
                )
            }
            persist(force = true)
            findTask(taskId)?.let { notifier.notify(it, appSettings) }

            try {
                downloadChapter(taskId, task.manga, chapter)
            } catch (cancelled: CancellationException) {
                persist(force = true)
                throw cancelled
            } catch (error: Throwable) {
                // Pages that were already written stay on disk, so a retry only re-fetches
                // what is missing instead of starting the chapter over.
                setChapter(taskId, chapter.chapterId) {
                    it.copy(
                        state = DownloadChapterState.FAILED,
                        error = failureMessage(error),
                    )
                }
                persist(force = true)
                findTask(taskId)?.let { notifier.notify(it, appSettings) }
            }
        }
        finalizeTask(taskId)
    }

    private suspend fun downloadChapter(taskId: String, manga: Manga, chapter: DownloadChapter) {
        val settingsSnapshot = appSettings
        if (settingsSnapshot.wifiOnlyDownloads && !isOnWifi()) {
            throw IOException(context.getString(com.mangaos.app.R.string.download_error_wifi))
        }
        val pages = mangaRepository.pages(manga.sourceId, manga.id, chapter.chapterId)
        if (pages.isEmpty()) {
            throw IOException(context.getString(com.mangaos.app.R.string.download_error_no_pages))
        }
        // Directory, permission and a real probe write first: a chapter never starts on a
        // storage layer that cannot accept it, and "no space" is only reported by the volume
        // that actually reported it. Free space is read from the nearest existing ancestor,
        // because File.usableSpace is 0 for a directory that does not exist yet.
        val storage = StorageInspector.check(context)
        if (!storage.writable) throw StorageException(storage.failure ?: StorageFailure.UNAVAILABLE)
        val headers = mangaRepository.imageHeaders(manga.sourceId)
        val directory = chapterDir(manga.key, chapter.chapterId)
        // Existing pages are kept: retrying a failed chapter continues where it stopped.
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw StorageException(StorageFailure.CANNOT_CREATE)
        }

        setChapter(taskId, chapter.chapterId) { it.copy(pageCount = pages.size) }
        persist(force = true)

        val names = ArrayList<String>(pages.size)
        var bytes = 0L
        pages.forEachIndexed { index, url ->
            currentCoroutineContext().ensureActive()
            val name = pageFileName(index, url)
            val target = File(directory, name)
            val part = File(directory, "$name.part")
            val request = Request.Builder()
                .url(url)
                .apply {
                    headers.forEach { (key, value) ->
                        if (key.isNotBlank() && value.isNotBlank()) header(key, value)
                    }
                }
                .build()
            if (target.isFile && target.length() > 0L) {
                // A previous attempt already stored this page; never download it twice.
                bytes += target.length()
                names += name
                setChapter(taskId, chapter.chapterId) {
                    it.copy(
                        pageCount = pages.size,
                        downloadedPages = index + 1,
                        bytes = bytes,
                    )
                }
                persist(force = false)
                notifyThrottled(taskId)
                return@forEachIndexed
            }
            fetchPage(request, part, index)
            val partial = part
            if (!partial.exists() || partial.length() <= 0L) {
                partial.delete()
                throw IOException("Empty page ${index + 1}")
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IOException("Could not store page ${index + 1}")
            }
            bytes += target.length()
            names += name
            setChapter(taskId, chapter.chapterId) {
                it.copy(
                    pageCount = pages.size,
                    downloadedPages = index + 1,
                    bytes = bytes,
                )
            }
            persist(force = false)
            notifyThrottled(taskId)
        }

        val verified = names.all { name ->
            val page = File(directory, name)
            page.exists() && page.isFile && page.length() > 0L
        }
        if (!verified || !ChapterFiles.writeManifest(directory, names) ||
            !ChapterFiles.isComplete(directory)
        ) {
            throw IOException(context.getString(com.mangaos.app.R.string.download_error_verify))
        }

        val snapshot = findTask(taskId)?.manga ?: manga
        try {
            localStorage.saveChapter(
                snapshot,
                Chapter(
                    id = chapter.chapterId,
                    mangaId = manga.id,
                    number = chapter.number,
                    title = chapter.title,
                    downloadStatus = ChapterDownloadStatus.DOWNLOADED,
                    sizeBytes = bytes,
                    localPath = directory.absolutePath,
                ),
                findTask(taskId)?.localCoverPath,
            )
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw IOException(
                context.getString(com.mangaos.app.R.string.download_error_record),
                error,
            )
        }

        setChapter(taskId, chapter.chapterId) {
            it.copy(
                state = DownloadChapterState.COMPLETED,
                pageCount = pages.size,
                downloadedPages = names.size,
                bytes = bytes,
                localDir = directory.absolutePath,
                error = null,
            )
        }
        persist(force = true)
        findTask(taskId)?.let { notifier.notify(it, appSettings) }
    }

    /**
     * One page request with bounded retries. A page that fails is retried here, so a single
     * flaky response does not fail the whole chapter; pages that already succeeded are kept.
     */
    private suspend fun fetchPage(request: Request, partial: File, index: Int) {
        var lastError: Throwable? = null
        repeat(PAGE_ATTEMPTS) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} for page ${index + 1}")
                    }
                    val body = response.body ?: throw IOException("Empty page ${index + 1}")
                    partial.outputStream().use { output -> body.byteStream().copyTo(output) }
                }
                return
            } catch (error: IOException) {
                lastError = error
                runCatching { partial.delete() }
                if (attempt < PAGE_ATTEMPTS - 1) delay(PAGE_RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Empty page ${index + 1}")
    }

    private fun finalizeTask(taskId: String) {
        val task = findTask(taskId) ?: return
        val message = when (task.state) {
            DownloadState.PARTIAL -> context.getString(com.mangaos.app.R.string.download_partial_message)
            DownloadState.FAILED -> task.chapters.firstNotNullOfOrNull { it.error }
            else -> null
        }
        updateTask(taskId) { it.copy(error = message) }
        persist(force = true)
        findTask(taskId)?.let { notifier.notify(it, appSettings) }
    }

    // ------------------------------------------------------------------ helpers

    private fun findTask(taskId: String): Download? =
        _downloads.value.firstOrNull { it.id == taskId }

    private fun updateTask(taskId: String, transform: (Download) -> Download): Download? {
        synchronized(lock) {
            val current = _downloads.value.firstOrNull { it.id == taskId } ?: return null
            val updated = transform(current)
            val next = _downloads.value.map { if (it.id == taskId) updated else it }
            _downloads.value = next
            persistLocked(next, force = true)
            return updated
        }
    }

    private fun setChapter(
        taskId: String,
        chapterId: String,
        transform: (DownloadChapter) -> DownloadChapter,
    ) {
        synchronized(lock) {
            val next = _downloads.value.map { task ->
                if (task.id != taskId) {
                    task
                } else {
                    task.copy(
                        chapters = task.chapters.map { chapter ->
                            if (chapter.chapterId == chapterId) transform(chapter) else chapter
                        },
                    )
                }
            }
            _downloads.value = next
            persistLocked(next, force = false)
        }
    }

    private fun cacheCover(taskId: String) {
        val task = findTask(taskId) ?: return
        if (task.localCoverPath != null) return
        scope.launch {
            val path = coverStore.cache(
                task.manga.key,
                task.manga.coverUrl,
                mangaRepository.imageHeaders(task.manga.sourceId),
            ) ?: return@launch
            updateTask(taskId) { it.copy(localCoverPath = path) }
        }
    }

    private fun notifyThrottled(taskId: String) {
        val now = SystemClock.elapsedRealtime()
        val last = lastNotifiedAt[taskId] ?: 0L
        if (now - last < NOTIFICATION_INTERVAL_MS) return
        lastNotifiedAt[taskId] = now
        findTask(taskId)?.let { notifier.notify(it, appSettings) }
    }

    private fun commit(next: List<Download>) {
        persistLocked(next, force = true)
        _downloads.value = next
    }

    private fun persist(force: Boolean) {
        persistLocked(_downloads.value, force)
    }

    private fun persistLocked(next: List<Download>, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - (lastPersistedAt[PERSIST_KEY] ?: 0L) < PERSIST_INTERVAL_MS) return
        lastPersistedAt[PERSIST_KEY] = now
        AtomicFileWriter.write(file, encode(next))
    }

    /** A chapter downloaded before process death is re-checked against its files. */
    private fun sanitize(task: Download): Download = task.copy(
        chapters = task.chapters.map { chapter ->
            when (chapter.state) {
                // Interrupted by process death: the worker must pick this chapter up again.
                DownloadChapterState.DOWNLOADING ->
                    chapter.copy(state = DownloadChapterState.QUEUED)
                DownloadChapterState.PAUSED -> chapter
                DownloadChapterState.COMPLETED -> {
                    val directory = chapter.localDir?.let { File(it) }
                    if (directory != null && ChapterFiles.isComplete(directory)) {
                        chapter
                    } else {
                        chapter.copy(
                            state = DownloadChapterState.QUEUED,
                            pageCount = 0,
                            downloadedPages = 0,
                            bytes = 0L,
                            localDir = null,
                        )
                    }
                }
                else -> chapter
            }
        },
    )

    private fun Chapter.toDownloadChapter(): DownloadChapter = DownloadChapter(
        chapterId = id,
        number = number,
        title = title,
        state = DownloadChapterState.QUEUED,
    )

    private fun chapterDir(mangaKey: String, chapterId: String): File =
        File(File(root, hash(mangaKey)), hash(chapterId))

    private fun deleteChapterDirectory(mangaKey: String, chapterId: String) {
        runCatching { chapterDir(mangaKey, chapterId).deleteRecursively() }
    }

    private fun pageFileName(index: Int, url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val extension = path.substringAfterLast('.', "").lowercase()
        val suffix = if (extension in IMAGE_EXTENSIONS) ".$extension" else ".img"
        return String.format(java.util.Locale.US, "%04d%s", index, suffix)
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private fun isOnWifi(): Boolean {
        val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // ------------------------------------------------------------------ persistence

    private fun encode(tasks: List<Download>): String {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(
                JSONObject().apply {
                    put("id", task.id)
                    put("manga", task.manga.toJson())
                    put("createdAt", task.createdAt)
                    task.localCoverPath?.let { put("localCoverPath", it) }
                    task.error?.let { put("error", it) }
                    put("chapters", JSONArray().apply {
                        task.chapters.forEach { chapter ->
                            put(
                                JSONObject().apply {
                                    put("chapterId", chapter.chapterId)
                                    put("number", chapter.number)
                                    chapter.title?.let { put("title", it) }
                                    put("state", chapter.state.name)
                                    put("pageCount", chapter.pageCount)
                                    put("downloadedPages", chapter.downloadedPages)
                                    put("bytes", chapter.bytes)
                                    chapter.localDir?.let { put("localDir", it) }
                                    chapter.error?.let { put("error", it) }
                                },
                            )
                        }
                    })
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
            val manga = json.optJSONObject("manga")?.toManga() ?: return@mapNotNull null
            val chapters = json.optJSONArray("chapters")?.let { list ->
                (0 until list.length()).mapNotNull { position ->
                    val chapter = list.optJSONObject(position) ?: return@mapNotNull null
                    val chapterId = chapter.optString("chapterId").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    DownloadChapter(
                        chapterId = chapterId,
                        number = chapter.optString("number"),
                        title = chapter.optString("title").takeIf { it.isNotBlank() },
                        state = runCatching {
                            DownloadChapterState.valueOf(chapter.optString("state"))
                        }.getOrDefault(DownloadChapterState.QUEUED),
                        pageCount = chapter.optInt("pageCount"),
                        downloadedPages = chapter.optInt("downloadedPages"),
                        bytes = chapter.optLong("bytes"),
                        localDir = chapter.optString("localDir").takeIf { it.isNotBlank() },
                        error = chapter.optString("error").takeIf { it.isNotBlank() },
                    )
                }
            }.orEmpty()
            if (chapters.isEmpty()) return@mapNotNull null
            Download(
                id = id,
                manga = manga,
                chapters = chapters,
                createdAt = json.optLong("createdAt"),
                localCoverPath = json.optString("localCoverPath").takeIf { it.isNotBlank() },
                error = json.optString("error").takeIf { it.isNotBlank() },
            )
        }
    }

    private companion object {
        const val QUEUE_RECORDS = "download_queue.json"
        const val PERSIST_KEY = "queue"
        const val PERSIST_INTERVAL_MS = 800L
        const val NOTIFICATION_INTERVAL_MS = 700L
        const val PAGE_ATTEMPTS = 3
        const val PAGE_RETRY_DELAY_MS = 900L
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        val ACTIVE_OR_DONE = setOf(
            DownloadChapterState.QUEUED,
            DownloadChapterState.DOWNLOADING,
            DownloadChapterState.PAUSED,
            DownloadChapterState.COMPLETED,
        )
    }
}
