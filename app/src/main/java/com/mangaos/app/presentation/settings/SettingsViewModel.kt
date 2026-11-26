package com.mangaos.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val settings = ServiceLocator.settings

    val language: StateFlow<String> = settings.language.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        LocaleManager.readLanguage(ServiceLocator.context()),
    )

    val theme: StateFlow<AppTheme> = settings.theme.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppTheme.DEFAULT,
    )

    val darkMode: StateFlow<Boolean?> = settings.darkMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null,
    )

    fun setLanguage(language: String) {
        viewModelScope.launch { settings.setLanguage(language) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setDarkMode(darkMode: Boolean) {
        viewModelScope.launch { settings.setDarkMode(darkMode) }
    }
}
