package com.mangaos.app.presentation.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.ui.UiState
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MangaListViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val _state = MutableStateFlow<UiState<List<Manga>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Manga>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_state.value is UiState.Content) return
        viewModelScope.launch {
            _state.value = UiState.Loading
            runCatching { repository.catalogue() }
                .onSuccess { list ->
                    _state.value = if (list.isEmpty()) UiState.Empty else UiState.Content(list)
                }
                .onFailure { _state.value = UiState.Error() }
        }
    }
}
