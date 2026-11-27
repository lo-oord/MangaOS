package com.mangaos.app.di

import android.content.Context
import com.mangaos.app.data.download.LocalDownloadRepository
import com.mangaos.app.data.local.CoverStore
import com.mangaos.app.data.local.ReaderSettingsRepositoryImpl
import com.mangaos.app.data.local.SettingsRepositoryImpl
import com.mangaos.app.data.local.mangaOsDataStore
import com.mangaos.app.data.notifications.DownloadNotifications
import com.mangaos.app.data.notifications.NewChapterNotifications
import com.mangaos.app.data.repository.ChapterWatchStore
import com.mangaos.app.data.repository.DataStoreLibraryRepository
import com.mangaos.app.data.repository.DataStoreUserRepository
import com.mangaos.app.data.repository.FileNotificationRepository
import com.mangaos.app.data.repository.NativeMangaRepository
import com.mangaos.app.data.repository.PersistentLocalStorageRepository
import com.mangaos.app.data.sources.NativeSourceManager
import com.mangaos.app.data.work.DownloadScheduler
import com.mangaos.app.data.work.NewChapterScheduler
import com.mangaos.app.domain.repository.DownloadRepository
import com.mangaos.app.domain.repository.LibraryRepository
import com.mangaos.app.domain.repository.LocalStorageRepository
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.NotificationRepository
import com.mangaos.app.domain.repository.ReaderSettingsRepository
import com.mangaos.app.domain.repository.SettingsRepository
import com.mangaos.app.domain.repository.SourceRepository
import com.mangaos.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Small manual dependency container. Keeps ViewModels free of Android wiring and makes it
 * trivial to swap an implementation without touching the presentation layer.
 */
object ServiceLocator {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var initialized = false

    private lateinit var appContext: Context

    lateinit var settings: SettingsRepository
        private set
    lateinit var readerSettings: ReaderSettingsRepository
        private set
    lateinit var user: UserRepository
        private set
    lateinit var sources: SourceRepository
        private set
    lateinit var manga: MangaRepository
        private set
    lateinit var library: LibraryRepository
        private set
    lateinit var downloads: DownloadRepository
        private set
    lateinit var localStorage: LocalStorageRepository
        private set
    lateinit var notifications: NotificationRepository
        private set
    lateinit var coverStore: CoverStore
        private set
    lateinit var downloadNotifications: DownloadNotifications
        private set
    lateinit var newChapterNotifications: NewChapterNotifications
        private set
    lateinit var chapterWatch: ChapterWatchStore
        private set

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val dataStore = appContext.mangaOsDataStore

        settings = SettingsRepositoryImpl(appContext, dataStore)
        readerSettings = ReaderSettingsRepositoryImpl(dataStore)
        user = DataStoreUserRepository(dataStore, appScope)
        library = DataStoreLibraryRepository(dataStore, appScope)
        // Every source is compiled into the app; this only reads and stores the enabled set.
        sources = NativeSourceManager(dataStore, appScope)
        manga = NativeMangaRepository(sources)
        localStorage = PersistentLocalStorageRepository(appContext)
        notifications = FileNotificationRepository(appContext, appScope)
        coverStore = CoverStore(appContext)
        downloadNotifications = DownloadNotifications(appContext)
        newChapterNotifications = NewChapterNotifications(appContext)
        chapterWatch = ChapterWatchStore(appContext)
        downloads = LocalDownloadRepository(
            scope = appScope,
            mangaRepository = manga,
            localStorage = localStorage,
            context = appContext,
            settings = settings,
            scheduler = DownloadScheduler(appContext),
            notifier = downloadNotifications,
            coverStore = coverStore,
        )

        // The periodic check is only registered while the user wants it. WorkManager keeps
        // the schedule across app restarts and reboots by itself.
        appScope.launch {
            settings.appSettings
                .map { it.notificationsEnabled && it.newChapterNotifications }
                .distinctUntilChanged()
                .collect { enabled ->
                    val scheduler = NewChapterScheduler(appContext)
                    if (enabled) scheduler.schedule() else scheduler.cancel()
                }
        }

        initialized = true
    }

    fun context(): Context = appContext
}
