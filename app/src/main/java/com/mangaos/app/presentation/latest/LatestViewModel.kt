package com.mangaos.app.presentation.latest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.repository.SourceUnavailableException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LatestUiState {
    data object Loading : LatestUiState
    data object NoSources : LatestUiState

    /** Extensions are installed but none of them produced a usable source. */
    data object NoUsableSources : LatestUiState
    data object Empty : LatestUiState
    data class Content(val mangas: List<Manga>) : LatestUiState
    data class Error(val failure: ExtensionFailure) : LatestUiState
}

class LatestViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val extensions = ServiceLocator.extensions
    private val settings = ServiceLocator.settings

    val sources: StateFlow<List<SourceInfo>> = extensions.sources
        .map { list -> list.sortedBy { source -> source.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selectedSourceId: StateFlow<String?> = settings.latestSourceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<LatestUiState>(LatestUiState.Loading)
    val state: StateFlow<LatestUiState> = _state.asStateFlow()

    var query by mutableStateOf("")
        private set

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(settings.latestSourceId, extensions.sources, extensions.installed) { id, sources, installed ->
                Triple(id, sources, installed)
            }.collectLatest { (id, sources, installed) -> load(id, sources, installed.isNotEmpty()) }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun selectSource(sourceId: String) {
        viewModelScope.launch { settings.setLatestSourceId(sourceId) }
    }

    fun retry() {
        viewModelScope.launch { extensions.refresh(force = true) }
        load(selectedSourceId.value, extensions.sources.value, extensions.installed.value.isNotEmpty())
    }

    private fun load(preferred: String?, sources: List<SourceInfo>, hasInstalledExtensions: Boolean) {
        if (sources.isEmpty()) {
            _state.value = if (hasInstalledExtensions) {
                LatestUiState.NoUsableSources
            } else {
                LatestUiState.NoSources
            }
            return
        }
        val chosen = sources.firstOrNull { it.id == preferred }
            ?: sources.firstOrNull { it.supportsLatest }
            ?: sources.first()
        if (preferred != chosen.id) {
            viewModelScope.launch { settings.setLatestSourceId(chosen.id) }
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = LatestUiState.Loading
            runCatching { repository.latest(chosen.id) }
                .onSuccess { mangas ->
                    _state.value = if (mangas.isEmpty()) {
                        LatestUiState.Empty
                    } else {
                        LatestUiState.Content(mangas)
                    }
                }
                .onFailure { error ->
                    _state.value = LatestUiState.Error(
                        (error as? SourceUnavailableException)?.failure ?: ExtensionFailure.NETWORK,
                    )
                }
        }
    }
}
