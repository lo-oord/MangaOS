package com.mangaos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepositoryImpl(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val darkMode = stringPreferencesKey("dark_mode")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val viewMode = stringPreferencesKey("manga_view_mode")
        val latestSource = stringPreferencesKey("latest_source_id")
    }

    override val language: Flow<String> = dataStore.data.map { preferences ->
        val stored = preferences[Keys.language]
        if (LocaleManager.isSupported(stored)) stored!! else LocaleManager.DEFAULT_LANGUAGE
    }

    override val theme: Flow<AppTheme> = dataStore.data.map { preferences ->
        AppTheme.fromStorage(preferences[Keys.theme])
    }

    override val darkMode: Flow<Boolean?> = dataStore.data.map { preferences ->
        when (preferences[Keys.darkMode]) {
            "dark" -> true
            "light" -> false
            else -> null
        }
    }

    override val onboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.onboarding] ?: false
    }

    override val viewMode: Flow<MangaViewMode> = dataStore.data.map { preferences ->
        MangaViewMode.fromStorage(preferences[Keys.viewMode])
    }

    override val latestSourceId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.latestSource]?.takeIf { it.isNotBlank() }
    }

    override suspend fun setLanguage(language: String) {
        LocaleManager.cacheLanguage(context, language)
        dataStore.edit { it[Keys.language] = language }
    }

    override suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[Keys.theme] = theme.storageKey }
    }

    override suspend fun setDarkMode(darkMode: Boolean?) {
        dataStore.edit { preferences ->
            if (darkMode == null) preferences.remove(Keys.darkMode)
            else preferences[Keys.darkMode] = if (darkMode) "dark" else "light"
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.onboarding] = completed }
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
}
