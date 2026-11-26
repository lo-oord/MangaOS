package com.mangaos.app.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MangaDetailViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val _manga = MutableStateFlow<Manga?>(null)
    val manga: StateFlow<Manga?> = _manga.asStateFlow()

    fun load(mangaId: String) {
        if (_manga.value?.id == mangaId) return
        viewModelScope.launch {
            _manga.value = repository.byId(mangaId)
        }
    }
}
