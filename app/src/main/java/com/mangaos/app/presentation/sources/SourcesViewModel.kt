package com.mangaos.app.presentation.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.ExtensionEntry
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.LanguageFilter
import com.mangaos.app.domain.model.ManagedExtension
import com.mangaos.app.domain.model.TransferState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel : ViewModel() {

    private val extensions = ServiceLocator.extensions

    val state: StateFlow<ExtensionsState> = extensions.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExtensionsState.Loading)

    val transfers: StateFlow<Map<String, TransferState>> = extensions.transfers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val runtimeAvailable: Boolean = extensions.runtimeAvailable

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
        viewModelScope.launch { extensions.refresh(force = true) }
    }

    fun reloadInstalled() = extensions.reloadInstalled()

    fun canInstallPackages(): Boolean = extensions.canInstallPackages()

    fun install(extension: ManagedExtension) = extensions.install(extension)

    fun uninstall(pkgName: String) = extensions.uninstall(pkgName)

    fun visibleEntries(entries: List<ExtensionEntry>): List<ExtensionEntry> {
        val trimmed = query.trim()
        return entries.filter { entry ->
            val matchesQuery = trimmed.isEmpty() ||
                entry.extension.name.contains(trimmed, ignoreCase = true) ||
                entry.extension.baseUrls.any { it.contains(trimmed, ignoreCase = true) }
            matchesQuery && languageFilter.matches(entry.extension.languages)
        }
    }
}
