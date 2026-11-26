package com.mangaos.app.domain.repository

import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.InstalledExtension
import com.mangaos.app.domain.model.ManagedExtension
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.model.ReadingProgress
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.model.StoredWork
import com.mangaos.app.domain.model.TransferState
import com.mangaos.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Browsing always happens through the extension that owns the work. */
interface MangaRepository {
    suspend fun latest(sourceId: String, page: Int = 1): List<Manga>
    suspend fun popular(sourceId: String, page: Int = 1): List<Manga>
    suspend fun search(sourceId: String, query: String, page: Int = 1): List<Manga>

    /** Aggregated across every installed source, used by the "Manga" tab. */
    suspend fun catalogue(): List<Manga>

    /** Aggregated search across every installed source. */
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
    val failure: ExtensionFailure,
    message: String? = null,
) : Exception(message ?: failure.name)

interface ExtensionRepository {
    val state: StateFlow<ExtensionsState>
    val installed: StateFlow<Map<String, InstalledExtension>>
    val transfers: StateFlow<Map<String, TransferState>>

    /** Sources instantiated from installed extensions by the Mihon runtime. */
    val sources: StateFlow<List<SourceInfo>>

    /** Installed extension package -> load failure, so broken extensions stay isolated. */
    val failures: StateFlow<Map<String, String>>

    /** True while the extension index is being refreshed (force refresh included). */
    val refreshing: StateFlow<Boolean>

    /** Last refresh failure; the previously loaded catalogue is kept when this is set. */
    val failureState: StateFlow<ExtensionFailure?>

    /** True when this build can execute extension code, not only install it. */
    val runtimeAvailable: Boolean

    suspend fun refresh(force: Boolean = false)

    /** Re-reads installed packages (after the system installer or an uninstall). */
    fun reloadInstalled()

    fun install(extension: ManagedExtension)
    fun uninstall(pkgName: String)
    fun canInstallPackages(): Boolean
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
}

/** Stable key for the read-chapter set. */
fun readChapterKey(mangaKey: String, chapterId: String): String = "$mangaKey\u0001$chapterId"

interface DownloadRepository {
    val downloads: StateFlow<List<Download>>

    fun enqueue(manga: Manga, chapter: Chapter)

    /** Completed page files for offline reading, in page order. */
    fun localPages(mangaKey: String, chapterId: String): List<String>
    fun pause(downloadId: String)
    fun resume(downloadId: String)
    fun retry(downloadId: String)
    fun cancel(downloadId: String)
    fun remove(downloadId: String)
}

interface LocalStorageRepository {
    val works: StateFlow<List<StoredWork>>

    fun saveChapter(manga: Manga, chapter: Chapter)
    fun deleteChapter(mangaId: String, chapterId: String)
    fun deleteChapters(mangaId: String, chapterIds: Set<String>)
    fun deleteWork(mangaId: String)
}

interface NotificationRepository {
    val notifications: StateFlow<List<AppNotification>>
    val unreadCount: StateFlow<Int>

    fun add(
        title: String,
        message: String,
        type: NotificationType,
    )

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
    /** null means "follow the system". */
    val darkMode: Flow<Boolean?>
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
    suspend fun setDarkMode(darkMode: Boolean?)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setViewMode(viewMode: MangaViewMode)
    suspend fun setLatestSourceId(sourceId: String?)
    suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings)
    suspend fun setLastDestination(route: String?)

    /** Saved page index for one chapter, keyed by source/manga/chapter. */
    fun readerPosition(positionKey: String): Flow<Int?>
    suspend fun setReaderPosition(positionKey: String, pageIndex: Int)
}
