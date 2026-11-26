package com.mangaos.app.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings
import com.mangaos.app.domain.model.ReadingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderPage(
    val chapterId: String,
    val chapterNumber: String,
    val indexInChapter: Int,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    val key: String get() = "$chapterId#$indexInChapter#$url"
}

sealed interface ImageState {
    data object Loading : ImageState
    data object Success : ImageState
    data object Error : ImageState
}

class ReaderViewModel : ViewModel() {

    private val mangaRepo = ServiceLocator.manga
    private val downloads = ServiceLocator.downloads
    private val library = ServiceLocator.library
    private val settingsRepo = ServiceLocator.settings
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

    private val _nextChapterLoading = MutableStateFlow(false)
    val nextChapterLoading: StateFlow<Boolean> = _nextChapterLoading.asStateFlow()

    private val _nextChapterError = MutableStateFlow(false)
    val nextChapterError: StateFlow<Boolean> = _nextChapterError.asStateFlow()

    private val _autoWebtoon = MutableStateFlow(false)
    val autoWebtoon: StateFlow<Boolean> = _autoWebtoon.asStateFlow()

    /** Page the reader should open after restoring a saved position. */
    private val _initialPage = MutableStateFlow(0)
    val initialPage: StateFlow<Int> = _initialPage.asStateFlow()

    /** One-shot page index the screen must reveal after a chapter switch. */
    private val _scrollRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollRequests = _scrollRequests.asSharedFlow()

    /** Reading position inside the current chapter, 0f..1f. */
    private val _readingFraction = MutableStateFlow(0f)
    val readingFraction: StateFlow<Float> = _readingFraction.asStateFlow()

    private val _loadingFraction = MutableStateFlow(0f)
    val loadingFraction: StateFlow<Float> = _loadingFraction.asStateFlow()

    private val loadedChapterIds = mutableSetOf<String>()
    private val chapterPageCounts = mutableMapOf<String, Int>()
    private var sourceId: String = ""
    private var mangaId: String = ""
    private var currentChapterId: String = ""
    private var nextLoadJob: Job? = null
    private var consecutiveTallImages = 0
    private var webtoonDetectionDone = false

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
        chapterPageCounts.clear()
        _error.value = null
        _nextChapterError.value = false
        _autoWebtoon.value = false
        webtoonDetectionDone = false
        consecutiveTallImages = 0
        _readingFraction.value = 0f
        _loadingFraction.value = 0f

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
                    _initialPage.value = settingsRepo
                        .readerPosition(positionKey(startChapter.id))
                        .first() ?: 0
                    loadChapterPages(startChapter)
                    if (_pages.value.isEmpty() && _error.value == null) {
                        _error.value = "The source returned no pages for this chapter"
                    }
                    if (settings.value.continuousReading) prefetchNextChapters()
                }
            } catch (error: Exception) {
                _error.value = error.message ?: error.javaClass.simpleName
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadChapterPages(chapter: Chapter): Boolean {
        if (chapter.id in loadedChapterIds) return true
        return try {
            // A completed download is authoritative and works without a network connection.
            val localUrls = downloads.localPages("$sourceId/$mangaId", chapter.id)
            val urls = if (localUrls.isNotEmpty()) {
                localUrls
            } else {
                mangaRepo.pages(sourceId, mangaId, chapter.id)
            }
            val headers = if (localUrls.isNotEmpty()) emptyMap() else mangaRepo.imageHeaders(sourceId)
            if (urls.isEmpty()) {
                if (chapter.id == currentChapterId) {
                    _error.value = "The source returned no pages for chapter ${chapter.number}"
                }
                false
            } else {
                val newPages = urls.mapIndexed { index, url ->
                    ReaderPage(
                        chapterId = chapter.id,
                        chapterNumber = chapter.number,
                        indexInChapter = index,
                        url = url,
                        headers = headers,
                    )
                }
                loadedChapterIds += chapter.id
                chapterPageCounts[chapter.id] = newPages.size
                _pages.value = _pages.value + newPages
                val states = _imageStates.value.toMutableMap()
                newPages.forEach { page -> states.putIfAbsent(page.key, ImageState.Loading) }
                _imageStates.value = states
                true
            }
        } catch (cancelled: CancellationException) {
            loadedChapterIds.remove(chapter.id)
            throw cancelled
        } catch (error: Exception) {
            loadedChapterIds.remove(chapter.id)
            false
        }
    }

    private fun prefetchNextChapters() {
        nextLoadJob?.cancel()
        nextLoadJob = viewModelScope.launch {
            val chapters = _chapters.value
            val toPreload = settings.value.preloadChapters.coerceIn(0, 3)
            if (toPreload == 0) return@launch
            var index = _currentChapterIndex.value + 1
            repeat(toPreload) {
                val chapter = chapters.getOrNull(index) ?: return@launch
                if (chapter.id !in loadedChapterIds) {
                    _nextChapterLoading.value = true
                    val loaded = loadChapterPages(chapter)
                    _nextChapterLoading.value = false
                    if (!loaded) {
                        _nextChapterError.value = true
                        return@launch
                    }
                }
                index++
            }
        }
    }

    fun onImageSuccess(pageKey: String, width: Int, height: Int) {
        _imageStates.value = _imageStates.value.toMutableMap().apply {
            this[pageKey] = ImageState.Success
        }
        recalculateLoading()
        detectWebtoon(width, height)
    }

    fun onImageError(pageKey: String) {
        _imageStates.value = _imageStates.value.toMutableMap().apply {
            this[pageKey] = ImageState.Error
        }
        recalculateLoading()
    }

    fun retryImage(pageKey: String) {
        _imageStates.value = _imageStates.value.toMutableMap().apply {
            this[pageKey] = ImageState.Loading
        }
    }

    /**
     * Long images in a row are the reliable signal for a webtoon. Detection only selects the
     * webtoon rendering while the user keeps the vertical mode; any explicit choice wins.
     */
    private fun detectWebtoon(width: Int, height: Int) {
        if (webtoonDetectionDone || !settings.value.autoWebtoonDetection) return
        if (width <= 0 || height <= 0) return
        if (height.toFloat() / width.toFloat() >= 2.2f) {
            consecutiveTallImages++
            if (consecutiveTallImages >= 2) {
                webtoonDetectionDone = true
                _autoWebtoon.value = true
            }
        } else {
            consecutiveTallImages = 0
        }
    }

    private fun recalculateLoading() {
        val states = _imageStates.value.values
        if (states.isEmpty()) {
            _loadingFraction.value = 0f
            return
        }
        val done = states.count { it is ImageState.Success || it is ImageState.Error }
        _loadingFraction.value = done.toFloat() / states.size
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

    fun retryNextChapter() {
        _nextChapterError.value = false
        val chapter = _chapters.value.getOrNull(_currentChapterIndex.value + 1) ?: return
        viewModelScope.launch {
            _nextChapterLoading.value = true
            val loaded = loadChapterPages(chapter)
            _nextChapterLoading.value = false
            _nextChapterError.value = !loaded
        }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { readerSettingsRepo.update(transform) }
    }

    /** Called as the user scrolls; loads the next chapter before the end is reached. */
    fun ensureNextLoaded(pageIndex: Int) {
        if (!settings.value.continuousReading) return
        val pages = _pages.value
        if (pages.isEmpty()) return
        val page = pages.getOrNull(pageIndex) ?: pages.last()
        val index = pages.indexOfFirst { it.chapterId == page.chapterId }
        val chapterIndex = _chapters.value.indexOfFirst { it.id == page.chapterId }
        if (chapterIndex < 0) return
        val next = _chapters.value.getOrNull(chapterIndex + 1) ?: return
        if (pageIndex < pages.size - 3) return
        if (next.id in loadedChapterIds || _nextChapterLoading.value) return
        if (index < 0) return
        viewModelScope.launch {
            _nextChapterLoading.value = true
            val loaded = loadChapterPages(next)
            _nextChapterLoading.value = false
            _nextChapterError.value = !loaded
        }
    }

    /**
     * Records reading position separately from image loading. Only the chapter of the visible
     * page is persisted, so a prefetched chapter is never marked as read.
     */
    fun onPageChanged(pageIndex: Int) {
        val pages = _pages.value
        val page = pages.getOrNull(pageIndex) ?: return
        val chapterIndex = _chapters.value.indexOfFirst { it.id == page.chapterId }
        if (chapterIndex >= 0 && chapterIndex != _currentChapterIndex.value) {
            // Reaching another chapter means the previous one was actually read.
            _chapters.value.getOrNull(_currentChapterIndex.value)?.let { markRead(it) }
            _currentChapterIndex.value = chapterIndex
            currentChapterId = page.chapterId
            _chapters.value.getOrNull(chapterIndex)?.let { recordProgress(it) }
        }
        val count = chapterPageCounts[page.chapterId] ?: pages.count { it.chapterId == page.chapterId }
        _readingFraction.value = if (count > 1) {
            ((page.indexInChapter + 1).toFloat() / count).coerceIn(0f, 1f)
        } else {
            1f
        }
        if (count > 0 && page.indexInChapter >= count - 1 && chapterIndex >= 0) {
            _chapters.value.getOrNull(chapterIndex)?.let { markRead(it) }
        }
        currentChapterId = page.chapterId
        viewModelScope.launch {
            settingsRepo.setReaderPosition(positionKey(page.chapterId), page.indexInChapter)
        }
    }

    fun goToChapter(chapter: Chapter) {
        val index = _chapters.value.indexOfFirst { it.id == chapter.id }
        if (index < 0 || chapter.id == currentChapterId || _isLoading.value) return
        viewModelScope.launch {
            val previous = _chapters.value.getOrNull(_currentChapterIndex.value)
            if (previous != null && previous.id != chapter.id) markRead(previous)
            currentChapterId = chapter.id
            _currentChapterIndex.value = index
            _pages.value = emptyList()
            _imageStates.value = emptyMap()
            loadedChapterIds.clear()
            chapterPageCounts.clear()
            _error.value = null
            _nextChapterError.value = false
            _initialPage.value = settingsRepo.readerPosition(positionKey(chapter.id)).first() ?: 0
            recordProgress(chapter)
            _isLoading.value = true
            loadChapterPages(chapter)
            _isLoading.value = false
            val target = pageIndexOfChapter(chapter.id).coerceAtLeast(0)
            _scrollRequests.tryEmit(target)
            if (settings.value.continuousReading) prefetchNextChapters()
        }
    }

    fun pageIndexOfChapter(chapterId: String): Int =
        _pages.value.indexOfFirst { it.chapterId == chapterId }

    fun pagesOfCurrentChapter(): IntRange {
        val pages = _pages.value
        val first = pages.indexOfFirst { it.chapterId == currentChapterId }
        if (first < 0) return IntRange.EMPTY
        val last = pages.indexOfLast { it.chapterId == currentChapterId }
        return first..last
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

    private fun markRead(chapter: Chapter) {
        val manga = _manga.value ?: return
        library.markChapterRead(manga.key, chapter.id)
    }

    private fun positionKey(chapterId: String): String = "$sourceId/$mangaId/$chapterId"

    fun effectiveMode(settings: ReaderSettings): ReaderMode {
        return if (settings.autoWebtoonDetection &&
            _autoWebtoon.value &&
            settings.readingMode == ReaderMode.VERTICAL
        ) {
            ReaderMode.WEBTOON
        } else {
            settings.readingMode
        }
    }

    fun loadedCount(): Int = _imageStates.value.count { it.value is ImageState.Success }
    fun failedCount(): Int = _imageStates.value.count { it.value is ImageState.Error }
    fun totalCount(): Int = _pages.value.size
}
