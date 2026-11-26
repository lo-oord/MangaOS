package com.mangaos.app.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.ReaderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReaderSettingsViewModel : ViewModel() {
    val settings: StateFlow<ReaderSettings> = ServiceLocator.readerSettings.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, ReaderSettings(),
    )

    fun update(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { ServiceLocator.readerSettings.update(transform) }
    }

    fun reset() {
        viewModelScope.launch { ServiceLocator.readerSettings.reset() }
    }
}