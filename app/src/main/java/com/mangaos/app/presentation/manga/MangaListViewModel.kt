package com.mangaos.app.presentation.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.ui.UiState
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MangaListViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val extensions = ServiceLocator.extensions

    private val _state = MutableStateFlow<UiState<List<Manga>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Manga>>> = _state.asStateFlow()

    var query by mutableStateOf("")
        private set

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // Reload when extensions are installed/removed while the tab is open.
            extensions.sources.collect { sources ->
                if (sources.isNotEmpty() && _state.value is UiState.Empty) {
                    runCatching { repository.catalogue() }.onSuccess { list ->
                        _state.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                    }
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        loadJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            load()
            return
        }
        loadJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.value = UiState.Loading
            runCatching { repository.searchAll(trimmed) }
                .onSuccess { list ->
                    // Guard against a slower previous query overwriting newer results.
                    if (query.trim() == trimmed) {
                        _state.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                    }
                }
                .onFailure {
                    if (query.trim() == trimmed) _state.value = UiState.Error()
                }
        }
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { repository.catalogue() }
                .onSuccess { list ->
                    if (query.isBlank()) {
                        _state.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                    }
                }
                .onFailure {
                    if (query.isBlank()) _state.value = UiState.Error()
                }
        }
    }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
