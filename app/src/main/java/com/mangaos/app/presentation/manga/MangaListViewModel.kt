package com.mangaos.app.presentation.manga

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.ui.UiState
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MangaListViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val sources = ServiceLocator.sources

    private val _state = MutableStateFlow<UiState<List<Manga>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Manga>>> = _state.asStateFlow()

    var query by mutableStateOf("")
        private set

    private var loadJob: Job? = null

    init {
        if (sources.enabledSources.value.isEmpty()) {
            _state.value = UiState.Empty
        } else {
            load()
        }
        viewModelScope.launch {
            // Re-run only when the set of usable sources actually changes. The initial value is
            // dropped because it was handled above.
            sources.enabledSources
                .map { list -> list.map { it.id } }
                .distinctUntilChanged()
                .drop(1)
                .collect { ids ->
                    if (ids.isEmpty()) {
                        loadJob?.cancel()
                        _state.value = UiState.Empty
                    } else if (query.isBlank()) {
                        load()
                    } else {
                        onQueryChange(query)
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
            try {
                val list = sanitize(repository.searchAll(trimmed))
                if (query.trim() == trimmed) {
                    _state.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                }
            } catch (error: CancellationException) {
                // Only a real scope cancellation should terminate the job. A source timeout
                // arrives as TimeoutCancellationException while this coroutine is still active.
                if (!currentCoroutineContext().isActive) throw error
                if (query.trim() == trimmed) _state.value = UiState.Error()
            } catch (_: Throwable) {
                if (query.trim() == trimmed) _state.value = UiState.Error()
            }
        }
    }

    fun load() {
        if (query.isNotBlank()) {
            onQueryChange(query)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (sources.enabledSources.value.isEmpty()) {
                _state.value = UiState.Empty
                return@launch
            }
            _state.value = UiState.Loading
            try {
                val list = sanitize(repository.catalogue())
                if (query.isBlank()) {
                    _state.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                }
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive) throw error
                if (query.isBlank()) _state.value = UiState.Error()
            } catch (_: Throwable) {
                if (query.isBlank()) _state.value = UiState.Error()
            }
        }
    }

    /** One malformed source result must not poison the whole tab or create duplicate keys. */
    private fun sanitize(mangas: List<Manga>): List<Manga> = mangas
        .filter { it.id.isNotBlank() && it.title.isNotBlank() }
        .distinctBy { it.key }

    private companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
    }
}
