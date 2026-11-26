package com.mangaos.app.presentation.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.util.SearchText
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.ExtensionEntry
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.LanguageFilter
import com.mangaos.app.domain.model.ManagedExtension
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.model.TransferState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SourcesEvent {
    data object Refreshed : SourcesEvent
    data class Failed(val failure: ExtensionFailure) : SourcesEvent
}

class SourcesViewModel : ViewModel() {

    private val extensions = ServiceLocator.extensions

    val state: StateFlow<ExtensionsState> = extensions.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExtensionsState.Loading)

    val sources: StateFlow<List<SourceInfo>> = extensions.sources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transfers: StateFlow<Map<String, TransferState>> = extensions.transfers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val failures: StateFlow<Map<String, String>> = extensions.failures
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val refreshing: StateFlow<Boolean> = extensions.refreshing
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val failureState: StateFlow<ExtensionFailure?> = extensions.failureState
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val runtimeAvailable: Boolean = extensions.runtimeAvailable

    private val _events = MutableSharedFlow<SourcesEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SourcesEvent> = _events.asSharedFlow()

    var query by mutableStateOf("")
        private set

    var languageFilter by mutableStateOf(LanguageFilter.ALL)
        private set

    fun onQueryChange(value: String) {
        query = value
    }

    fun changeLanguageFilter(filter: LanguageFilter) {
        languageFilter = filter
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            runCatching { extensions.refresh(force = true) }
            val failure = extensions.failureState.value
                ?: (extensions.state.value as? ExtensionsState.Error)?.failure
            if (failure != null) {
                _events.emit(SourcesEvent.Failed(failure))
            } else {
                _events.emit(SourcesEvent.Refreshed)
            }
        }
    }

    fun reloadInstalled() = extensions.reloadInstalled()

    fun canInstallPackages(): Boolean = extensions.canInstallPackages()

    fun install(extension: ManagedExtension) = extensions.install(extension)

    fun uninstall(pkgName: String) = extensions.uninstall(pkgName)

    fun visibleSources(all: List<SourceInfo>): List<SourceInfo> {
        val empty = SearchText.normalize(query).isEmpty()
        return all.filter { source ->
            languageFilter.matches(listOf(source.lang)) &&
                (empty ||
                    SearchText.matches(source.name, query) ||
                    SearchText.matches(source.extensionName, query))
        }
    }

    fun visibleExtensions(entries: List<ExtensionEntry>): List<ExtensionEntry> {
        val empty = SearchText.normalize(query).isEmpty()
        return entries.filter { entry ->
            languageFilter.matches(entry.extension.languages) &&
                (empty ||
                    SearchText.matches(entry.extension.name, query) ||
                    SearchText.matches(entry.extension.pkgName, query) ||
                    entry.extension.baseUrls.any { SearchText.matches(it, query) })
        }
    }
}
