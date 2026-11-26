package com.mangaos.app.di

import android.content.Context
import com.mangaos.app.data.extension.ExtensionStore
import com.mangaos.app.data.local.ReaderSettingsRepositoryImpl
import com.mangaos.app.data.local.SettingsRepositoryImpl
import com.mangaos.app.data.local.mangaOsDataStore
import com.mangaos.app.data.repository.DataStoreLibraryRepository
import com.mangaos.app.data.repository.DataStoreUserRepository
import com.mangaos.app.data.repository.ExtensionMangaRepository
import com.mangaos.app.data.repository.InMemoryLocalStorageRepository
import com.mangaos.app.data.repository.InMemoryNotificationRepository
import com.mangaos.app.data.repository.LocalDownloadRepository
import com.mangaos.app.domain.repository.DownloadRepository
import com.mangaos.app.domain.repository.ExtensionRepository
import com.mangaos.app.domain.repository.ReaderSettingsRepository
import com.mangaos.app.domain.repository.LibraryRepository
import com.mangaos.app.domain.repository.LocalStorageRepository
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.NotificationRepository
import com.mangaos.app.domain.repository.SettingsRepository
import com.mangaos.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    lateinit var extensions: ExtensionRepository
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

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val dataStore = appContext.mangaOsDataStore

        settings = SettingsRepositoryImpl(appContext, dataStore)
        readerSettings = ReaderSettingsRepositoryImpl(dataStore)
        user = DataStoreUserRepository(dataStore, appScope)
        library = DataStoreLibraryRepository(dataStore, appScope)
        extensions = ExtensionStore(appContext, appScope, settings)
        manga = ExtensionMangaRepository(extensions, appContext)
        localStorage = InMemoryLocalStorageRepository()
        notifications = InMemoryNotificationRepository(appScope)
        downloads = LocalDownloadRepository(appScope, manga, localStorage, notifications, appContext, settings)

        initialized = true
    }

    fun context(): Context = appContext
}
