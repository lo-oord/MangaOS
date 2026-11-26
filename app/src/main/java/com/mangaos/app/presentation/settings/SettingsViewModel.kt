package com.mangaos.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.ThemeMode
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.MangaViewMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val context = ServiceLocator.context()

    val language: StateFlow<String> = settings.language.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LocaleManager.readLanguage(context),
    )

    val theme: StateFlow<AppTheme> = settings.theme.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppTheme.DEFAULT,
    )

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ThemeMode.SYSTEM,
    )

    val appSettings: StateFlow<AppSettings> = settings.appSettings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    val viewMode: StateFlow<MangaViewMode> = settings.viewMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        MangaViewMode.GRID,
    )

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _clearingCache = MutableStateFlow(false)
    val clearingCache: StateFlow<Boolean> = _clearingCache.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setLanguage(language: String) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setViewMode(mode: MangaViewMode) {
        viewModelScope.launch { settings.setViewMode(mode) }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.updateAppSettings(transform) }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) { directorySize(context.cacheDir) }
        }
    }

    fun clearCache(onDone: (Long) -> Unit) {
        if (_clearingCache.value) return
        viewModelScope.launch {
            _clearingCache.value = true
            val freed = withContext(Dispatchers.IO) {
                val before = directorySize(context.cacheDir)
                // Close and clear Coil's cache first, then remove the remaining cache files.
                context.imageLoader.diskCache?.clear()
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                before
            }
            _cacheBytes.value = withContext(Dispatchers.IO) { directorySize(context.cacheDir) }
            _clearingCache.value = false
            onDone(freed)
        }
    }

    private fun directorySize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L
        return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
