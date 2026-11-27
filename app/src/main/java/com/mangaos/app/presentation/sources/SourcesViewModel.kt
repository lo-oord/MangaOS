package com.mangaos.app.presentation.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.util.SearchText
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.SourceInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Sources screen: the Arabic sources compiled into the app and the user's enable/disable
 * choice. There is nothing to download, install, update or remove.
 */
class SourcesViewModel : ViewModel() {

    private val repository = ServiceLocator.sources

    val sources: StateFlow<List<SourceInfo>> = repository.sources
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.sources.value)

    val enabledCount: StateFlow<Int> = repository.sources
        .map { list -> list.count { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val failures: StateFlow<Map<String, String>> = repository.failures
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    var query by mutableStateOf("")
        private set

    fun onQueryChange(value: String) {
        query = value
    }

    fun setEnabled(sourceId: String, enabled: Boolean) {
        repository.setEnabled(sourceId, enabled)
    }

    /** Visible sources for the current search query. */
    fun visibleSources(all: List<SourceInfo>): List<SourceInfo> {
        if (SearchText.normalize(query).isEmpty()) return all
        return all.filter { source -> SearchText.matches(source.name, query) }
    }
}
