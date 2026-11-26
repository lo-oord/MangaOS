package com.mangaos.app.presentation.latest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.InstalledExtension
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.repository.SourceUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface LatestUiState {
    data object Loading : LatestUiState
    data object NoSources : LatestUiState
    data object Empty : LatestUiState
    data class Content(val mangas: List<Manga>) : LatestUiState
    data class Error(val failure: ExtensionFailure) : LatestUiState
}

class LatestViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val extensions = ServiceLocator.extensions
    private val settings = ServiceLocator.settings

    val installedSources: StateFlow<List<InstalledExtension>> = extensions.installed
        .map { it.values.sortedBy { source -> source.label.lowercase() } }
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
            combine(settings.latestSourceId, extensions.installed) { id, installed -> id to installed }
                .collectLatest { (id, installed) -> load(id, installed) }
        }
    }

    fun onQueryChange(value: String) {
        query = value
    }

    fun selectSource(pkgName: String) {
        viewModelScope.launch { settings.setLatestSourceId(pkgName) }
    }

    fun retry() {
        viewModelScope.launch { extensions.refresh(force = true) }
        load(selectedSourceId.value, extensions.installed.value)
    }

    private fun load(preferred: String?, installed: Map<String, InstalledExtension>) {
        if (installed.isEmpty()) {
            _state.value = LatestUiState.NoSources
            return
        }
        val sourceId = preferred?.takeIf { it in installed } ?: installed.keys.first()
        if (preferred != sourceId) {
            viewModelScope.launch { settings.setLatestSourceId(sourceId) }
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = LatestUiState.Loading
            runCatching { repository.latest(sourceId) }
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
