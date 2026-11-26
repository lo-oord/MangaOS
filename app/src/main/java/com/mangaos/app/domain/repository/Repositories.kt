package com.mangaos.app.domain.repository

import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.model.StoredWork
import com.mangaos.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MangaRepository {
    suspend fun latest(): List<Manga>
    suspend fun catalogue(): List<Manga>
    suspend fun byId(id: String): Manga?

    /** Reserved for the real source integration of the next phase. */
    suspend fun chapters(mangaId: String): List<Chapter>
}

interface LibraryRepository {
    val works: StateFlow<List<Manga>>
    val ids: StateFlow<Set<String>>

    fun toggle(manga: Manga)
    fun remove(mangaId: String)
}

interface DownloadRepository {
    val downloads: StateFlow<List<Download>>

    fun enqueue(manga: Manga, chapterNumber: String)
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
    fun createAccount(displayName: String, username: String, avatarUri: String?)
    fun signOut()
}

interface SettingsRepository {
    val language: Flow<String>
    val theme: Flow<AppTheme>
    /** null means "follow the system". */
    val darkMode: Flow<Boolean?>
    val onboardingCompleted: Flow<Boolean>

    suspend fun setLanguage(language: String)
    suspend fun setTheme(theme: AppTheme)
    suspend fun setDarkMode(darkMode: Boolean?)
    suspend fun setOnboardingCompleted(completed: Boolean)
}
