package com.mangaos.app.domain.repository

import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.ThemeMode
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.model.ReadingProgress
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.model.StoredWork
import com.mangaos.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Browsing always happens through the native source that owns the work. */
interface MangaRepository {
    suspend fun latest(sourceId: String, page: Int = 1): List<Manga>
    suspend fun popular(sourceId: String, page: Int = 1): List<Manga>
    suspend fun search(sourceId: String, query: String, page: Int = 1): List<Manga>

    /** Aggregated across every enabled source, used by the "Manga" tab. */
    suspend fun catalogue(): List<Manga>

    /** Aggregated search across every enabled source. */
    suspend fun searchAll(query: String, page: Int = 1): List<Manga>

    suspend fun byId(sourceId: String, mangaId: String): Manga?
    suspend fun chapters(sourceId: String, mangaId: String): List<Chapter>

    /** Newest chapter known by the source, used by the Latest feed badges. */
    suspend fun latestChapter(sourceId: String, mangaId: String): Chapter?
    suspend fun pages(sourceId: String, mangaId: String, chapterId: String): List<String>

    /** Headers the source requires when fetching its images (Referer/User-Agent/cookies). */
    fun imageHeaders(sourceId: String): Map<String, String>
}

/** A failure that belongs to the source itself, never to the screen. */
class SourceUnavailableException(
    val failure: SourceFailure,
    message: String? = null,
) : Exception(message ?: failure.name)

/**
 * The bundled sources and the user's enable/disable choice. There is no installation step:
 * every source is part of the APK and only its enabled flag changes.
 */
interface SourceRepository {
    /** Every bundled source, with its current enabled flag. */
    val sources: StateFlow<List<SourceInfo>>

    /** Sources usable for new requests, ordered for the picker. */
    val enabledSources: StateFlow<List<SourceInfo>>

    /** Source id -> last runtime failure, shown next to the source and cleared on success. */
    val failures: StateFlow<Map<String, String>>

    fun setEnabled(sourceId: String, enabled: Boolean)
    fun isEnabled(sourceId: String): Boolean

    /** Lazily created implementation, or null for an unknown id. */
    fun source(sourceId: String): com.mangaos.app.sources.core.MangaSource?

    fun reportFailure(sourceId: String, message: String?)
}

interface LibraryRepository {
    val works: StateFlow<List<Manga>>
    val ids: StateFlow<Set<String>>
    val progress: StateFlow<Map<String, ReadingProgress>>

    /** Keys of chapters the reader actually left; never set by opening details. */
    val readChapters: StateFlow<Set<String>>

    fun toggle(manga: Manga)
    fun add(manga: Manga)
    fun remove(mangaId: String)
    fun isSaved(mangaKey: String): Boolean
    fun setProgress(mangaKey: String, progress: ReadingProgress)
    fun markChapterRead(mangaKey: String, chapterId: String)

    /** Explicit user action from the chapter list; never called by the reader. */
    fun markChapterUnread(mangaKey: String, chapterId: String)

    /** Fresh library snapshot for background work, independent of flow warm-up timing. */
    suspend fun worksSnapshot(): List<Manga>
}

/** Stable key for the read-chapter set. */
fun readChapterKey(mangaKey: String, chapterId: String): String = "$mangaKey\u0001$chapterId"

/**
 * Download queue + execution. Task records and local chapter files are separate: removing a
 * task from the downloads tab never deletes a completed chapter, which needs an explicit call.
 */
interface DownloadRepository {
    val downloads: StateFlow<List<Download>>

    /** One task for every chapter in [chapters]; already completed/active ones are skipped. */
    fun enqueue(manga: Manga, chapters: List<Chapter>)

    fun enqueue(manga: Manga, chapter: Chapter) = enqueue(manga, listOf(chapter))

    /** Completed page files for offline reading, in page order. */
    fun localPages(mangaKey: String, chapterId: String): List<String>

    fun pause(downloadId: String)
    fun resume(downloadId: String)
    fun retry(downloadId: String)
    fun cancel(downloadId: String)

    /** Removes the task record only; downloaded chapters stay in the local library. */
    fun removeRecord(downloadId: String)

    /** Explicitly deletes the downloaded files and their local records, then the task record. */
    fun deleteLocal(downloadId: String)

    /** Deletes one completed chapter's files and local record; its task stays without it. */
    fun deleteLocalChapter(mangaKey: String, chapterId: String)
}

interface LocalStorageRepository {
    val works: StateFlow<List<StoredWork>>

    fun work(mangaId: String): StoredWork?
    fun localChapter(mangaId: String, chapterId: String): Chapter?

    /** Saves/merges one completed chapter; throws when the record could not be written. */
    fun saveChapter(manga: Manga, chapter: Chapter, localCoverPath: String? = null)

    /** @return false when the chapter is currently open in the reader. */
    fun deleteChapter(mangaId: String, chapterId: String): Boolean
    fun deleteChapters(mangaId: String, chapterIds: Set<String>): Boolean
    fun deleteWork(mangaId: String): Boolean
}

interface NotificationRepository {
    val notifications: StateFlow<List<AppNotification>>
    val unreadCount: StateFlow<Int>

    fun add(
        title: String,
        message: String,
        type: NotificationType,
    )

    fun add(notification: AppNotification)

    fun markRead(notificationId: String)
    fun markAllRead()
    fun delete(notificationId: String)
    fun deleteAll()
}

interface UserRepository {
    val profile: StateFlow<UserProfile?>
    val isGuest: Flow<Boolean>

    fun startGuestSession()
    fun createAccount(
        displayName: String,
        username: String,
        avatarUri: String?,
        email: String? = null,
        countryCode: String? = null,
        age: Int? = null,
        preferredLanguage: String? = null,
    )

    /** Updates the active local profile in one write so every screen sees the same values. */
    suspend fun updateProfile(
        displayName: String,
        username: String,
        avatarUri: String?,
        email: String?,
        countryCode: String?,
        age: Int?,
        preferredLanguage: String?,
    )

    fun signOut()
}

interface SettingsRepository {
    val language: Flow<String>
    val theme: Flow<AppTheme>
    /** System / light / dark / AMOLED, independent of the colour theme. */
    val themeMode: Flow<ThemeMode>
    val onboardingCompleted: Flow<Boolean>
    val viewMode: Flow<MangaViewMode>
    /** Source id shown in the Latest tab, or null for the first usable source. */
    val latestSourceId: Flow<String?>

    /** Behaviour, notifications, downloads, library and interaction preferences. */
    val appSettings: Flow<AppSettings>

    /** Last drawer destination, used only when "restore last screen" is enabled. */
    val lastDestination: Flow<String?>

    suspend fun setLanguage(language: String)
    suspend fun setTheme(theme: AppTheme)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setViewMode(viewMode: MangaViewMode)
    suspend fun setLatestSourceId(sourceId: String?)
    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings)
    suspend fun setLastDestination(route: String?)

    /** Saved page index for one chapter, keyed by source/manga/chapter. */
    fun readerPosition(positionKey: String): Flow<Int?>
    suspend fun setReaderPosition(positionKey: String, pageIndex: Int)
}
