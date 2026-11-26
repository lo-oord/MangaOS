package com.mangaos.app.presentation.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterOrder
import com.mangaos.app.domain.model.ChapterSort
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.ReadingProgress
import com.mangaos.app.domain.repository.SourceUnavailableException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val failure: ExtensionFailure, val cached: Manga?) : DetailUiState
    data class Content(
        val manga: Manga,
        val chapters: List<Chapter>,
        val chaptersLoading: Boolean,
        val chaptersFailure: ExtensionFailure?,
    ) : DetailUiState
}

class MangaDetailViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val library = ServiceLocator.library

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    val savedIds: StateFlow<Set<String>> = library.ids
    val progress: StateFlow<Map<String, ReadingProgress>> = library.progress
    val readChapters: StateFlow<Set<String>> = library.readChapters

    var sort by mutableStateOf(ChapterSort.NEWEST_FIRST)
        private set

    private var sourceId: String = ""
    private var mangaId: String = ""

    fun load(sourceId: String, mangaId: String, force: Boolean = false) {
        val sameWork = this.sourceId == sourceId && this.mangaId == mangaId
        if (!force && sameWork && _state.value is DetailUiState.Content) return

        this.sourceId = sourceId
        this.mangaId = mangaId
        val key = "$sourceId/$mangaId"
        val cached = library.works.value.firstOrNull { it.key == key }
        _state.value = if (cached != null) {
            DetailUiState.Content(cached, emptyList(), chaptersLoading = true, chaptersFailure = null)
        } else {
            DetailUiState.Loading
        }

        viewModelScope.launch {
            val detailResult = runCatching { repository.byId(sourceId, mangaId) }
            val manga = detailResult.getOrNull() ?: cached
            if (manga == null) {
                _state.value = DetailUiState.Error(
                    failure = detailResult.failureOrDefault(),
                    cached = null,
                )
                return@launch
            }

            _state.value = DetailUiState.Content(manga, emptyList(), chaptersLoading = true, chaptersFailure = null)

            val chaptersResult = runCatching { repository.chapters(sourceId, mangaId) }
            _state.value = DetailUiState.Content(
                manga = manga,
                chapters = sortChapters(chaptersResult.getOrDefault(emptyList())),
                chaptersLoading = false,
                chaptersFailure = chaptersResult.exceptionOrNull()?.let { error ->
                    (error as? SourceUnavailableException)?.failure ?: ExtensionFailure.NETWORK
                },
            )
        }
    }

    fun retry() {
        if (sourceId.isNotEmpty()) load(sourceId, mangaId, force = true)
    }

    fun toggleSort() {
        sort = if (sort == ChapterSort.NEWEST_FIRST) ChapterSort.OLDEST_FIRST else ChapterSort.NEWEST_FIRST
        val current = _state.value as? DetailUiState.Content ?: return
        _state.value = current.copy(chapters = sortChapters(current.chapters))
    }

    fun toggleLibrary() {
        val current = _state.value as? DetailUiState.Content ?: return
        library.toggle(current.manga)
    }

    /** One canonical order, shared with the reader so "next" here matches "next" there. */
    private fun sortChapters(chapters: List<Chapter>): List<Chapter> {
        val ascending = ChapterOrder.ascending(chapters)
        return when (sort) {
            ChapterSort.NEWEST_FIRST -> ascending.asReversed()
            ChapterSort.OLDEST_FIRST -> ascending
        }
    }

    private fun Result<*>.failureOrDefault(): ExtensionFailure {
        val error = exceptionOrNull() ?: return ExtensionFailure.NETWORK
        return (error as? SourceUnavailableException)?.failure ?: ExtensionFailure.NETWORK
    }
}
