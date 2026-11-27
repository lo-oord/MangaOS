package com.mangaos.app.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterOrder
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.PageSequence
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings
import com.mangaos.app.domain.model.ReadingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** Outcome of loading one chapter's pages, so callers never mistake "busy" for "failed". */
private enum class PageLoadResult { LOADED, EMPTY, FAILED, BUSY, STALE }

class ReaderViewModel : ViewModel() {

    private val mangaRepo = ServiceLocator.manga
    private val downloads = ServiceLocator.downloads
    private val library = ServiceLocator.library
    private val settingsRepo = ServiceLocator.settings
    private val readerSettingsRepo = ServiceLocator.readerSettings
    private val localStorage = ServiceLocator.localStorage
    private val context = ServiceLocator.context()

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

    /** height/width per page once the bitmap is decoded, used to reserve layout space. */
    private val _imageRatios = MutableStateFlow<Map<String, Float>>(emptyMap())
    val imageRatios: StateFlow<Map<String, Float>> = _imageRatios.asStateFlow()

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

    private val _previousChapterLoading = MutableStateFlow(false)
    val previousChapterLoading: StateFlow<Boolean> = _previousChapterLoading.asStateFlow()

    private val _previousChapterError = MutableStateFlow(false)
    val previousChapterError: StateFlow<Boolean> = _previousChapterError.asStateFlow()

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

    /** Chapters with a request in flight; the same chapter is never fetched twice at once. */
    private val inFlightChapterIds = mutableSetOf<String>()
    private val chapterPageCounts = mutableMapOf<String, Int>()
    private var sourceId: String = ""
    private var mangaId: String = ""
    private var currentChapterId: String = ""

    /** Chapters whose last page was actually reached in this session. */
    private val finishedChapterIds = mutableSetOf<String>()
    private var nextLoadJob: Job? = null
    private var previousLoadJob: Job? = null
    private var consecutiveTallImages = 0
    private var webtoonDetectionDone = false
    /** True when the source could not be reached and only downloaded chapters are usable. */
    private var offlineOnly = false

    /**
     * Bumped whenever the reader is torn down or jumps to another chapter. A load that started
     * before the jump sees a different value and discards its result instead of appending a
     * chapter that no longer belongs to the visible sequence.
     */
    private var generation = 0

    fun load(sourceId: String, mangaId: String, chapterId: String) {
        if (this.sourceId == sourceId && this.mangaId == mangaId &&
            currentChapterId == chapterId && _pages.value.isNotEmpty()
        ) {
            return
        }
        this.sourceId = sourceId
        this.mangaId = mangaId
        this.currentChapterId = chapterId
        resetSequence()

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val key = "$sourceId/$mangaId"
                val localWork = localStorage.works.value.firstOrNull { it.manga.key == key }
                val remoteManga = runCatching { mangaRepo.byId(sourceId, mangaId) }.getOrNull()
                val remoteChapters = runCatching {
                    mangaRepo.chapters(sourceId, mangaId)
                }.getOrNull()
                offlineOnly = remoteChapters.isNullOrEmpty()
                _manga.value = remoteManga ?: localWork?.manga

                // Offline falls back to exactly the chapters that are stored on the device;
                // chapters that were never downloaded are never offered as available.
                val chapters = ChapterOrder.ascending(
                    when {
                        !remoteChapters.isNullOrEmpty() -> remoteChapters
                        localWork != null -> localWork.chapters
                        else -> emptyList()
                    },
                )
                _chapters.value = chapters

                val startIndex = chapters.indexOfFirst { it.id == chapterId }
                if (startIndex < 0) {
                    _error.value = if (offlineOnly) {
                        context.getString(com.mangaos.app.R.string.reader_chapter_not_downloaded)
                    } else {
                        "The chapter is not part of this source's chapter list"
                    }
                } else {
                    _currentChapterIndex.value = startIndex
                    val startChapter = chapters[startIndex]
                    _initialPage.value = settingsRepo
                        .readerPosition(positionKey(startChapter.id))
                        .first() ?: 0
                    // Opening a chapter makes it the work's reading position, which is what the
                    // details screen shows as "in progress". It stays unread until its end.
                    recordProgress(startChapter)
                    loadChapterPages(startChapter)
                    if (_pages.value.isEmpty() && _error.value == null) {
                        _error.value = if (offlineOnly) {
                            context.getString(com.mangaos.app.R.string.reader_chapter_not_downloaded)
                        } else {
                            "The source returned no pages for this chapter"
                        }
                    }
                    if (settings.value.continuousReading && !offlineOnly) prefetchNextChapters()
                }
            } catch (error: Exception) {
                _error.value = error.message ?: error.javaClass.simpleName
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun resetSequence() {
        generation++
        nextLoadJob?.cancel()
        previousLoadJob?.cancel()
        _pages.value = emptyList()
        _imageStates.value = emptyMap()
        _imageRatios.value = emptyMap()
        loadedChapterIds.clear()
        inFlightChapterIds.clear()
        chapterPageCounts.clear()
        finishedChapterIds.clear()
        _error.value = null
        _nextChapterError.value = false
        _previousChapterError.value = false
        _nextChapterLoading.value = false
        _previousChapterLoading.value = false
        _autoWebtoon.value = false
        webtoonDetectionDone = false
        consecutiveTallImages = 0
        _readingFraction.value = 0f
        _loadingFraction.value = 0f
    }

    /**
     * Loads one chapter and inserts its pages at the position its chapter index dictates. The
     * same path serves the forward prefetch, the backward prefetch and the retry button, so the
     * sequence can never be reordered or duplicated.
     */
    private suspend fun loadChapterPages(chapter: Chapter): PageLoadResult {
        if (chapter.id in loadedChapterIds) return PageLoadResult.LOADED
        if (!inFlightChapterIds.add(chapter.id)) return PageLoadResult.BUSY
        val startedIn = generation
        try {
            // A completed download is authoritative and works without a network connection.
            val localUrls = downloads.localPages("$sourceId/$mangaId", chapter.id)
            val urls = if (localUrls.isNotEmpty()) {
                localUrls
            } else if (offlineOnly) {
                emptyList()
            } else {
                mangaRepo.pages(sourceId, mangaId, chapter.id)
            }
            if (startedIn != generation) return PageLoadResult.STALE
            if (urls.isEmpty()) {
                if (chapter.id == currentChapterId) {
                    _error.value = if (offlineOnly) {
                        context.getString(com.mangaos.app.R.string.reader_chapter_not_downloaded)
                    } else {
                        "The source returned no pages for chapter ${chapter.number}"
                    }
                }
                return PageLoadResult.EMPTY
            }
            val headers = if (localUrls.isNotEmpty()) emptyMap() else mangaRepo.imageHeaders(sourceId)
            val newPages = urls.mapIndexed { index, url ->
                ReaderPage(
                    chapterId = chapter.id,
                    chapterNumber = chapter.number,
                    indexInChapter = index,
                    url = url,
                    headers = headers,
                )
            }
            // Another path may have loaded this chapter while the request was in flight.
            if (chapter.id in loadedChapterIds || _pages.value.any { it.chapterId == chapter.id }) {
                return PageLoadResult.STALE
            }
            insertPagesInOrder(newPages)
            loadedChapterIds += chapter.id
            chapterPageCounts[chapter.id] = newPages.size
            val states = _imageStates.value.toMutableMap()
            newPages.forEach { page -> states.putIfAbsent(page.key, ImageState.Loading) }
            _imageStates.value = states
            return PageLoadResult.LOADED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return PageLoadResult.FAILED
        } finally {
            inFlightChapterIds.remove(chapter.id)
        }
    }

    /** Inserts by chapter index, so prepending and appending share one ordering rule. */
    private fun insertPagesInOrder(newPages: List<ReaderPage>) {
        _pages.value = PageSequence.insertByChapter(
            existing = _pages.value,
            incoming = newPages,
            chapterOrder = _chapters.value.map { it.id },
            chapterIdOf = ReaderPage::chapterId,
        )
    }

    private fun prefetchNextChapters() {
        nextLoadJob?.cancel()
        nextLoadJob = viewModelScope.launch {
            val toPreload = settings.value.preloadChapters.coerceIn(0, 3)
            if (toPreload == 0) return@launch
            var index = _currentChapterIndex.value + 1
            repeat(toPreload) {
                val chapter = _chapters.value.getOrNull(index) ?: return@launch
                if (chapter.id !in loadedChapterIds && chapter.id !in inFlightChapterIds) {
                    _nextChapterLoading.value = true
                    val result = loadChapterPages(chapter)
                    _nextChapterLoading.value = false
                    if (result == PageLoadResult.FAILED || result == PageLoadResult.EMPTY) {
                        _nextChapterError.value = true
                        return@launch
                    }
                }
                index++
            }
        }
    }

    fun onImageSuccess(pageKey: String, width: Int, height: Int) {
        // A response that arrives after the page left the sequence (chapter switch, jump,
        // trimming) must not resurrect a state entry or change the current layout.
        if (!isCurrentPage(pageKey)) return
        _imageStates.value = _imageStates.value.toMutableMap().apply {
            this[pageKey] = ImageState.Success
        }
        if (width > 0 && height > 0) {
            val ratio = height.toFloat() / width.toFloat()
            if (_imageRatios.value[pageKey] != ratio) {
                _imageRatios.value = _imageRatios.value + (pageKey to ratio)
            }
        }
        recalculateLoading()
        detectWebtoon(width, height)
    }

    fun onImageError(pageKey: String) {
        if (!isCurrentPage(pageKey)) return
        _imageStates.value = _imageStates.value.toMutableMap().apply {
            this[pageKey] = ImageState.Error
        }
        recalculateLoading()
    }

    fun retryImage(pageKey: String) {
        if (!isCurrentPage(pageKey)) return
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

    private fun isCurrentPage(pageKey: String): Boolean =
        _pages.value.any { it.key == pageKey }

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
        nextLoadJob?.cancel()
        val chapter = _chapters.value.getOrNull(lastLoadedChapterIndex() + 1) ?: return
        nextLoadJob = viewModelScope.launch {
            _nextChapterLoading.value = true
            val result = loadChapterPages(chapter)
            _nextChapterLoading.value = false
            _nextChapterError.value = result == PageLoadResult.FAILED || result == PageLoadResult.EMPTY
        }
    }

    fun retryPreviousChapter() {
        _previousChapterError.value = false
        previousLoadJob?.cancel()
        val chapter = _chapters.value.getOrNull(firstLoadedChapterIndex() - 1) ?: return
        previousLoadJob = viewModelScope.launch {
            _previousChapterLoading.value = true
            val result = loadChapterPages(chapter)
            _previousChapterLoading.value = false
            _previousChapterError.value = result == PageLoadResult.FAILED || result == PageLoadResult.EMPTY
        }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { readerSettingsRepo.update(transform) }
    }

    /**
     * Loads the next chapter as the reader approaches the end of the chapter they are in, so
     * the transition is a continuation of the same list rather than a new screen.
     */
    fun ensureNextLoaded(pageIndex: Int) {
        if (!settings.value.continuousReading) return
        val pages = _pages.value
        val page = pages.getOrNull(pageIndex) ?: return
        val count = chapterPageCounts[page.chapterId] ?: pages.count { it.chapterId == page.chapterId }
        if (count <= 0 || page.indexInChapter < count - 2) return
        // A failed neighbour is only retried by the user: no endless background requests.
        if (_nextChapterError.value) return
        if (nextLoadJob?.isActive == true || _nextChapterLoading.value) return
        val chapter = _chapters.value.getOrNull(lastLoadedChapterIndex() + 1) ?: return
        if (chapter.id in loadedChapterIds || chapter.id in inFlightChapterIds) return
        nextLoadJob = viewModelScope.launch {
            _nextChapterLoading.value = true
            val result = loadChapterPages(chapter)
            _nextChapterLoading.value = false
            _nextChapterError.value = result == PageLoadResult.FAILED || result == PageLoadResult.EMPTY
        }
    }

    /**
     * Mirror of [ensureNextLoaded]: as the reader returns to the first pages of the chapter at
     * the top of the sequence, the previous chapter is fetched and inserted before it.
     */
    fun ensurePreviousLoaded(pageIndex: Int) {
        if (!settings.value.continuousReading) return
        val pages = _pages.value
        val page = pages.getOrNull(pageIndex) ?: return
        val firstLoaded = firstLoadedChapterIndex()
        if (firstLoaded <= 0) return
        // Only the chapter that sits at the top of the sequence can grow upwards.
        if (page.chapterId != pages.first().chapterId) return
        if (page.indexInChapter > PREVIOUS_TRIGGER_PAGES) return
        if (_previousChapterError.value) return
        if (previousLoadJob?.isActive == true || _previousChapterLoading.value) return
        val chapter = _chapters.value.getOrNull(firstLoaded - 1) ?: return
        if (chapter.id in loadedChapterIds || chapter.id in inFlightChapterIds) return
        previousLoadJob = viewModelScope.launch {
            _previousChapterLoading.value = true
            val result = loadChapterPages(chapter)
            _previousChapterLoading.value = false
            _previousChapterError.value = result == PageLoadResult.FAILED || result == PageLoadResult.EMPTY
        }
    }

    private fun firstLoadedChapterIndex(): Int {
        val firstChapterId = _pages.value.firstOrNull()?.chapterId ?: return -1
        return _chapters.value.indexOfFirst { it.id == firstChapterId }
    }

    private fun lastLoadedChapterIndex(): Int {
        val lastChapterId = _pages.value.lastOrNull()?.chapterId ?: return -1
        return _chapters.value.indexOfFirst { it.id == lastChapterId }
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
            // Only a chapter the reader scrolled to its end counts as read. Opening a chapter
            // and leaving it again must not silently change its reading state.
            _chapters.value.getOrNull(_currentChapterIndex.value)
                ?.takeIf { it.id in finishedChapterIds }
                ?.let { markRead(it) }
            _currentChapterIndex.value = chapterIndex
            currentChapterId = page.chapterId
            _chapters.value.getOrNull(chapterIndex)?.let { recordProgress(it) }
            trimDistantChapters(chapterIndex, pageIndex)
        }
        val count = chapterPageCounts[page.chapterId] ?: pages.count { it.chapterId == page.chapterId }
        _readingFraction.value = if (count > 1) {
            ((page.indexInChapter + 1).toFloat() / count).coerceIn(0f, 1f)
        } else {
            1f
        }
        if (count > 0 && page.indexInChapter >= count - 1 && chapterIndex >= 0) {
            finishedChapterIds += page.chapterId
            _chapters.value.getOrNull(chapterIndex)?.let { markRead(it) }
        }
        currentChapterId = page.chapterId
        viewModelScope.launch {
            settingsRepo.setReaderPosition(positionKey(page.chapterId), page.indexInChapter)
        }
    }

    /**
     * Keeps only the chapters around the reading position in memory. Runs on chapter changes
     * (never mid-scroll), never drops the visible chapter, and keeps per-chapter page counts so
     * progress stays correct for chapters that were unloaded. Reading positions and downloads
     * are untouched.
     */
    private fun trimDistantChapters(chapterIndex: Int, visiblePageIndex: Int) {
        if (loadedChapterIds.size <= MAX_LOADED_CHAPTERS) return
        val chapters = _chapters.value
        if (chapters.isEmpty()) return
        val from = (chapterIndex - KEEP_CHAPTERS_BEHIND).coerceAtLeast(0)
        val to = (chapterIndex + KEEP_CHAPTERS_AHEAD).coerceAtMost(chapters.lastIndex)
        if (from > to) return
        val keep = chapters.subList(from, to + 1).mapTo(mutableSetOf()) { it.id }
        val visibleChapterId = _pages.value.getOrNull(visiblePageIndex)?.chapterId
        if (visibleChapterId != null && visibleChapterId !in keep) return
        val removed = loadedChapterIds - keep
        if (removed.isEmpty()) return
        val pages = _pages.value
        val remaining = pages.filter { it.chapterId in keep }
        if (remaining.size == pages.size) return
        val remainingKeys = remaining.mapTo(mutableSetOf()) { it.key }
        _pages.value = remaining
        loadedChapterIds.removeAll(removed)
        _imageStates.value = _imageStates.value.filterKeys { it in remainingKeys }
        _imageRatios.value = _imageRatios.value.filterKeys { it in remainingKeys }
    }

    fun goToChapter(chapter: Chapter) {
        val index = _chapters.value.indexOfFirst { it.id == chapter.id }
        if (index < 0 || chapter.id == currentChapterId || _isLoading.value) return
        nextLoadJob?.cancel()
        previousLoadJob?.cancel()
        viewModelScope.launch {
            val previous = _chapters.value.getOrNull(_currentChapterIndex.value)
            if (previous != null && previous.id != chapter.id && previous.id in finishedChapterIds) {
                markRead(previous)
            }
            resetSequence()
            _currentChapterIndex.value = index
            currentChapterId = chapter.id
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

    private companion object {
        /** Start fetching the previous chapter while still on one of its first pages. */
        const val PREVIOUS_TRIGGER_PAGES = 1

        /** Loaded chapters kept on each side of the reading position before trimming. */
        const val KEEP_CHAPTERS_BEHIND = 3
        const val KEEP_CHAPTERS_AHEAD = 3
        const val MAX_LOADED_CHAPTERS = 8
    }
}
