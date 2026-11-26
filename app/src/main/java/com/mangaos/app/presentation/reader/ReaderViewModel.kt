package com.mangaos.app.presentation.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.ReaderSettings
import com.mangaos.app.domain.model.ReadingProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderPage(
    val chapterId: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

sealed interface ImageState { data object Loading : ImageState; data object Success : ImageState; data object Error : ImageState }

class ReaderViewModel : ViewModel() {

    private val mangaRepo = ServiceLocator.manga
    private val library = ServiceLocator.library
    private val readerSettingsRepo = ServiceLocator.readerSettings

    val settings: StateFlow<ReaderSettings> = readerSettingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    private val _manga = MutableStateFlow<Manga?>(null)
    val manga: StateFlow<Manga?> = _manga.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _pages = MutableStateFlow<List<ReaderPage>>(emptyList())
    val pages: StateFlow<List<ReaderPage>> = _pages.asStateFlow()

    private val _imageStates = MutableStateFlow<Map<String, ImageState>>(emptyMap())
    val imageStates: StateFlow<Map<String, ImageState>> = _imageStates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val loadedChapterIds = mutableSetOf<String>()
    private var sourceId: String = ""
    private var mangaId: String = ""
    private var currentChapterId: String = ""

    fun load(sourceId: String, mangaId: String, chapterId: String) {
        if (this.sourceId == sourceId && this.mangaId == mangaId &&
            currentChapterId == chapterId && _pages.value.isNotEmpty()
        ) {
            return
        }
        this.sourceId = sourceId
        this.mangaId = mangaId
        this.currentChapterId = chapterId
        _pages.value = emptyList()
        _imageStates.value = emptyMap()
        loadedChapterIds.clear()
        _error.value = null

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val manga = mangaRepo.byId(sourceId, mangaId)
                _manga.value = manga
                val chapters = mangaRepo.chapters(sourceId, mangaId)
                    .sortedBy { it.number.toFloatOrNull() ?: 0f }
                _chapters.value = chapters
                val startIndex = chapters.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
                _currentChapterIndex.value = startIndex
                val startChapter = chapters.getOrNull(startIndex)
                if (startChapter == null) {
                    _error.value = "The chapter is not part of this source's chapter list"
                } else {
                    recordProgress(startChapter)
                    loadChapterPages(startChapter)
                    if (settings.value.continuousReading) prefetchNext()
                    if (_pages.value.isEmpty() && _error.value == null) {
                        _error.value = "The source returned no pages for this chapter"
                    }
                }
            } catch (error: Exception) {
                _error.value = error.message ?: error.javaClass.simpleName
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadChapterPages(chapter: Chapter) {
        if (chapter.id in loadedChapterIds) return
        try {
            val urls = mangaRepo.pages(sourceId, mangaId, chapter.id)
            val headers = mangaRepo.imageHeaders(sourceId)
            val newPages = urls.map { ReaderPage(chapter.id, it, headers) }
            if (newPages.isEmpty()) {
                _error.value = "The source returned no pages for chapter ${chapter.number}"
                return
            }
            loadedChapterIds += chapter.id
            _pages.value = _pages.value + newPages
            val states = _imageStates.value.toMutableMap()
            newPages.forEach { page -> states.putIfAbsent(page.url, ImageState.Loading) }
            _imageStates.value = states
        } catch (error: Exception) {
            // Keep the chapter unloaded so the retry path is real.
            loadedChapterIds.remove(chapter.id)
            _error.value = error.message ?: "Could not load chapter pages"
        }
    }

    private fun prefetchNext() {
        viewModelScope.launch {
            val chapters = _chapters.value
            val nextIndex = _currentChapterIndex.value + 1
            val toPreload = settings.value.preloadChapters.coerceAtLeast(1)
            repeat(toPreload) { offset ->
                chapters.getOrNull(nextIndex + offset)?.let { loadChapterPages(it) }
            }
        }
    }

    fun onImageSuccess(url: String) {
        _imageStates.value = _imageStates.value.toMutableMap().apply { this[url] = ImageState.Success }
    }

    fun onImageError(url: String) {
        _imageStates.value = _imageStates.value.toMutableMap().apply { this[url] = ImageState.Error }
    }

    fun retryImage(url: String) {
        _imageStates.value = _imageStates.value.toMutableMap().apply { this[url] = ImageState.Loading }
    }

    fun retry() {
        val chapter = _chapters.value.getOrNull(_currentChapterIndex.value) ?: return
        loadedChapterIds.remove(chapter.id)
        _pages.value = _pages.value.filterNot { it.chapterId == chapter.id }
        _error.value = null
        viewModelScope.launch {
            _isLoading.value = true
            loadChapterPages(chapter)
            _isLoading.value = false
        }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { readerSettingsRepo.update(transform) }
    }

    /** Loads the next chapter when the reader approaches the end of the current one. */
    fun ensureNextLoaded(currentIndex: Int) {
        val pages = _pages.value
        if (pages.isEmpty()) return
        if (currentIndex < pages.size - 3) return
        val chapterId = pages.getOrNull(currentIndex)?.chapterId ?: return
        val chapter = _chapters.value.firstOrNull { it.id == chapterId } ?: return
        val next = _chapters.value.getOrNull(_chapters.value.indexOf(chapter) + 1) ?: return
        if (next.id !in loadedChapterIds) {
            viewModelScope.launch { loadChapterPages(next) }
        }
    }

    fun goToChapter(chapter: Chapter) {
        val index = _chapters.value.indexOf(chapter)
        if (index < 0) return
        viewModelScope.launch {
            currentChapterId = chapter.id
            _currentChapterIndex.value = index
            _pages.value = emptyList()
            _imageStates.value = emptyMap()
            loadedChapterIds.clear()
            _error.value = null
            recordProgress(chapter)
            _isLoading.value = true
            loadChapterPages(chapter)
            _isLoading.value = false
            if (settings.value.continuousReading) prefetchNext()
        }
    }

    private fun recordProgress(chapter: Chapter) {
        val manga = _manga.value ?: return
        library.setProgress(
            manga.key,
            ReadingProgress(
                chapterId = chapter.id,
                chapterNumber = chapter.number,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun loadedCount(): Int = _imageStates.value.count { it.value is ImageState.Success }
    fun failedCount(): Int = _imageStates.value.count { it.value is ImageState.Error }
    fun totalCount(): Int = _pages.value.size
}
