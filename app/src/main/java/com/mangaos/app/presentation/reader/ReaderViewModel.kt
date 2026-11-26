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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ReaderPage(val chapterId: String, val url: String)

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

    fun load(sourceId: String, mangaId: String, chapterId: String) {
        if (this.sourceId == sourceId && this.mangaId == mangaId && _pages.value.isNotEmpty()) return
        this.sourceId = sourceId
        this.mangaId = mangaId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val manga = mangaRepo.byId(sourceId, mangaId)
                _manga.value = manga
                val chs = mangaRepo.chapters(sourceId, mangaId)
                // reading order: oldest first
                val sorted = chs.sortedBy { it.number.toFloatOrNull() ?: 0f }
                _chapters.value = sorted
                val startIndex = sorted.indexOfFirst { it.id == chapterId }.coerceAtLeast(0)
                _currentChapterIndex.value = startIndex
                // load initial + maybe continuous
                loadChapterPages(sorted.getOrNull(startIndex))
                if (settings.value.continuousReading) {
                    prefetchNext()
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadChapterPages(chapter: Chapter?) {
        if (chapter == null) return
        if (chapter.id in loadedChapterIds) return
        loadedChapterIds.add(chapter.id)
        try {
            val urls = mangaRepo.pages(sourceId, mangaId, chapter.id)
            val newPages = urls.map { ReaderPage(chapter.id, it) }
            _pages.value = _pages.value + newPages
            // init image states
            val states = _imageStates.value.toMutableMap()
            newPages.forEach { if (it.url !in states) states[it.url] = ImageState.Loading }
            _imageStates.value = states
        } catch (e: Exception) {
            // mark failed? keep
        }
    }

    private fun prefetchNext() {
        viewModelScope.launch {
            val sorted = _chapters.value
            var idx = _currentChapterIndex.value + 1
            val toPreload = settings.value.preloadChapters.coerceAtLeast(1)
            repeat(toPreload) {
                if (idx < sorted.size) {
                    loadChapterPages(sorted[idx])
                    idx++
                }
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

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { readerSettingsRepo.update(transform) }
    }

    // Continuous: call when user near end
    fun ensureNextLoaded(currentIndex: Int) {
        val pages = _pages.value
        if (pages.isEmpty()) return
        if (currentIndex >= pages.size - 3) {
            viewModelScope.launch {
                val sorted = _chapters.value
                val loadedChapters = loadedChapterIds.size
                if (loadedChapters < sorted.size) {
                    val nextChapter = sorted.getOrNull(loadedChapters)
                    loadChapterPages(nextChapter)
                }
            }
        }
    }

    fun goToChapter(chapter: Chapter) {
        viewModelScope.launch {
            _pages.value = emptyList()
            _imageStates.value = emptyMap()
            loadedChapterIds.clear()
            _currentChapterIndex.value = _chapters.value.indexOf(chapter).coerceAtLeast(0)
            loadChapterPages(chapter)
            if (settings.value.continuousReading) prefetchNext()
        }
    }

    fun loadedCount(): Int = _imageStates.value.count { it.value is ImageState.Success }
    fun failedCount(): Int = _imageStates.value.count { it.value is ImageState.Error }
    fun totalCount(): Int = _pages.value.size
}