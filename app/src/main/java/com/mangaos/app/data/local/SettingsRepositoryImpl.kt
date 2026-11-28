package com.mangaos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.ThemeMode
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.LibraryFilter
import com.mangaos.app.domain.model.LibrarySort
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.OnboardingState
import com.mangaos.app.domain.model.StorageChoice
import com.mangaos.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private companion object {
        const val GENRE_SEPARATOR = "|"
    }

    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        // Same key as the old light/dark flag so an upgrade keeps the previous choice.
        val themeMode = stringPreferencesKey("dark_mode")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val onboardingStep = intPreferencesKey("onboarding_step")
        val onboardingStorage = stringPreferencesKey("onboarding_storage")
        val onboardingFolderUri = stringPreferencesKey("onboarding_folder_uri")
        val onboardingFolderName = stringPreferencesKey("onboarding_folder_name")
        val onboardingGenres = stringPreferencesKey("onboarding_genres")
        val onboardingTypes = stringPreferencesKey("onboarding_types")
        val onboardingNotifications = booleanPreferencesKey("onboarding_notifications")
        val viewMode = stringPreferencesKey("manga_view_mode")
        val latestSource = stringPreferencesKey("latest_source_id")
        val lastDestination = stringPreferencesKey("last_destination")

        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val newChapterNotifications = booleanPreferencesKey("notifications_new_chapters")
        val downloadNotifications = booleanPreferencesKey("notifications_downloads")
        val notificationSound = booleanPreferencesKey("notifications_sound")
        val notificationVibration = booleanPreferencesKey("notifications_vibration")

        val wifiOnlyDownloads = booleanPreferencesKey("downloads_wifi_only")
        val autoDownload = booleanPreferencesKey("downloads_auto")
        val concurrentDownloads = intPreferencesKey("downloads_concurrent")

        val librarySort = stringPreferencesKey("library_sort")
        val libraryFilter = stringPreferencesKey("library_filter")

        val reduceMotion = booleanPreferencesKey("interaction_reduce_motion")
        val hapticFeedback = booleanPreferencesKey("interaction_haptics")
        val touchEffects = booleanPreferencesKey("interaction_touch_effects")

        val restoreLastScreen = booleanPreferencesKey("behavior_restore_last_screen")
        val confirmExit = booleanPreferencesKey("behavior_confirm_exit")
        val autoRefreshOnOpen = booleanPreferencesKey("behavior_refresh_on_open")
        val openLinksExternally = booleanPreferencesKey("behavior_external_links")

        fun readerPosition(key: String) = intPreferencesKey("reader_position_$key")
    }

    override val language: Flow<String> = dataStore.data.map { preferences ->
        val stored = preferences[Keys.language]
        if (LocaleManager.isSupported(stored)) stored!! else LocaleManager.DEFAULT_LANGUAGE
    }

    override val theme: Flow<AppTheme> = dataStore.data.map { preferences ->
        AppTheme.fromStorage(preferences[Keys.theme])
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.fromStorage(preferences[Keys.themeMode])
    }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.onboarding] ?: false
    }

    override val onboardingState: Flow<OnboardingState> = dataStore.data.map { preferences ->
        OnboardingState(
            step = (preferences[Keys.onboardingStep] ?: 0).coerceAtLeast(0),
            language = if (LocaleManager.isSupported(preferences[Keys.language])) {
                preferences[Keys.language]!!
            } else {
                LocaleManager.DEFAULT_LANGUAGE
            },
            theme = AppTheme.fromStorage(preferences[Keys.theme]),
            storageChoice = StorageChoice.fromStorage(preferences[Keys.onboardingStorage]),
            folderUri = preferences[Keys.onboardingFolderUri]?.takeIf { it.isNotBlank() },
            folderName = preferences[Keys.onboardingFolderName]?.takeIf { it.isNotBlank() },
            genres = preferences[Keys.onboardingGenres]
                ?.split(GENRE_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.toSet()
                .orEmpty(),
            contentTypes = preferences[Keys.onboardingTypes]
                ?.split(GENRE_SEPARATOR)
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: OnboardingState.DEFAULT_CONTENT_TYPES,
            notificationsEnabled = preferences[Keys.onboardingNotifications] ?: true,
        )
    }

    override val viewMode: Flow<MangaViewMode> = dataStore.data.map { preferences ->
        MangaViewMode.fromStorage(preferences[Keys.viewMode])
    }

    override val latestSourceId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.latestSource]?.takeIf { it.isNotBlank() }
    }

    override val appSettings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    override val lastDestination: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.lastDestination]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setLanguage(language: String) {
        LocaleManager.cacheLanguage(context, language)
        dataStore.edit { it[Keys.language] = language }
    }

    override suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[Keys.theme] = theme.storageKey }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.storageKey }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.onboarding] = completed }
    }

    override suspend fun updateOnboarding(transform: (OnboardingState) -> OnboardingState) {
        dataStore.edit { preferences ->
            val current = OnboardingState(
                step = (preferences[Keys.onboardingStep] ?: 0).coerceAtLeast(0),
                language = preferences[Keys.language] ?: LocaleManager.DEFAULT_LANGUAGE,
                theme = AppTheme.fromStorage(preferences[Keys.theme]),
                storageChoice = StorageChoice.fromStorage(preferences[Keys.onboardingStorage]),
                folderUri = preferences[Keys.onboardingFolderUri],
                folderName = preferences[Keys.onboardingFolderName],
                genres = preferences[Keys.onboardingGenres]
                    ?.split(GENRE_SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    .orEmpty(),
                contentTypes = preferences[Keys.onboardingTypes]
                    ?.split(GENRE_SEPARATOR)
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: OnboardingState.DEFAULT_CONTENT_TYPES,
                notificationsEnabled = preferences[Keys.onboardingNotifications] ?: true,
            )
            val next = transform(current)
            preferences[Keys.onboardingStep] = next.step.coerceAtLeast(0)
            preferences[Keys.onboardingStorage] = next.storageChoice.storageKey
            next.folderUri?.takeIf { it.isNotBlank() }?.let {
                preferences[Keys.onboardingFolderUri] = it
            } ?: preferences.remove(Keys.onboardingFolderUri)
            next.folderName?.takeIf { it.isNotBlank() }?.let {
                preferences[Keys.onboardingFolderName] = it
            } ?: preferences.remove(Keys.onboardingFolderName)
            preferences[Keys.onboardingGenres] = next.genres.joinToString(GENRE_SEPARATOR)
            preferences[Keys.onboardingTypes] = next.contentTypes.joinToString(GENRE_SEPARATOR)
            preferences[Keys.onboardingNotifications] = next.notificationsEnabled
        }
    }

    override suspend fun setViewMode(viewMode: MangaViewMode) {
        dataStore.edit {
            it[Keys.viewMode] = if (viewMode == MangaViewMode.LIST) "list" else "grid"
        }
    }

    override suspend fun setLatestSourceId(sourceId: String?) {
        dataStore.edit { preferences ->
            if (sourceId.isNullOrBlank()) preferences.remove(Keys.latestSource)
            else preferences[Keys.latestSource] = sourceId
        }
    }

    override suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val next = transform(preferences.toAppSettings())
            preferences[Keys.notificationsEnabled] = next.notificationsEnabled
            preferences[Keys.newChapterNotifications] = next.newChapterNotifications
            preferences[Keys.downloadNotifications] = next.downloadNotifications
            preferences[Keys.notificationSound] = next.notificationSound
            preferences[Keys.notificationVibration] = next.notificationVibration
            preferences[Keys.wifiOnlyDownloads] = next.wifiOnlyDownloads
            preferences[Keys.autoDownload] = next.autoDownload
            preferences[Keys.concurrentDownloads] = next.concurrentDownloads.coerceIn(1, 4)
            preferences[Keys.librarySort] = next.librarySort.storageKey
            preferences[Keys.libraryFilter] = next.libraryFilter.storageKey
            preferences[Keys.reduceMotion] = next.reduceMotion
            preferences[Keys.hapticFeedback] = next.hapticFeedback
            preferences[Keys.touchEffects] = next.touchEffects
            preferences[Keys.restoreLastScreen] = next.restoreLastScreen
            preferences[Keys.confirmExit] = next.confirmExit
            preferences[Keys.autoRefreshOnOpen] = next.autoRefreshOnOpen
            preferences[Keys.openLinksExternally] = next.openLinksExternally
        }
    }

    override suspend fun setLastDestination(route: String?) {
        dataStore.edit { preferences ->
            if (route.isNullOrBlank()) preferences.remove(Keys.lastDestination)
            else preferences[Keys.lastDestination] = route
        }
    }

    override fun readerPosition(positionKey: String): Flow<Int?> = dataStore.data.map { preferences ->
        preferences[Keys.readerPosition(positionKey)]
    }

    override suspend fun setReaderPosition(positionKey: String, pageIndex: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.readerPosition(positionKey)] = pageIndex.coerceAtLeast(0)
        }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        notificationsEnabled = this[Keys.notificationsEnabled] ?: true,
        newChapterNotifications = this[Keys.newChapterNotifications] ?: true,
        downloadNotifications = this[Keys.downloadNotifications] ?: true,
        notificationSound = this[Keys.notificationSound] ?: true,
        notificationVibration = this[Keys.notificationVibration] ?: false,
        wifiOnlyDownloads = this[Keys.wifiOnlyDownloads] ?: true,
        autoDownload = this[Keys.autoDownload] ?: false,
        concurrentDownloads = (this[Keys.concurrentDownloads] ?: 2).coerceIn(1, 4),
        librarySort = LibrarySort.fromStorage(this[Keys.librarySort]),
        libraryFilter = LibraryFilter.fromStorage(this[Keys.libraryFilter]),
        reduceMotion = this[Keys.reduceMotion] ?: false,
        hapticFeedback = this[Keys.hapticFeedback] ?: true,
        touchEffects = this[Keys.touchEffects] ?: true,
        restoreLastScreen = this[Keys.restoreLastScreen] ?: true,
        confirmExit = this[Keys.confirmExit] ?: false,
        autoRefreshOnOpen = this[Keys.autoRefreshOnOpen] ?: true,
        openLinksExternally = this[Keys.openLinksExternally] ?: true,
    )
}
