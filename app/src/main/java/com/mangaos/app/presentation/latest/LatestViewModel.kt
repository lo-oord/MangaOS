package com.mangaos.app.presentation.latest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.util.SearchText
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.repository.SourceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface LatestUiState {
    data object Loading : LatestUiState
    data object NoSources : LatestUiState
    data object Empty : LatestUiState

    data class Content(
        val mangas: List<Manga>,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val appendError: SourceFailure? = null,
        /** Sources that could not answer the first page, named for the footer note. */
        val failedSources: List<String> = emptyList(),
    ) : LatestUiState

    data class Error(val failure: SourceFailure) : LatestUiState
}

sealed interface LatestSearchState {
    data object Idle : LatestSearchState
    data object Loading : LatestSearchState
    data class Content(
        val mangas: List<Manga>,
        val failedSources: List<String>,
        val finishedSources: Int,
        val totalSources: Int,
    ) : LatestSearchState

    data class Error(val failure: SourceFailure) : LatestSearchState
}

/**
 * The Latest feed.
 *
 * With no source chosen it is one merged feed: every enabled source's newest page is fetched
 * side by side, deduplicated and sorted by the real publication time of the newest chapter, so
 * the work that was updated most recently is always on top — no matter which source it came
 * from and in what order that source answered. Choosing one source narrows both the feed and
 * the search to it.
 *
 * Sources do not all carry the newest chapter number and its date in their list payload. Those
 * items are resolved from the source afterwards, and the feed is re-sorted as soon as a real
 * timestamp arrives, so the ordering is never guessed.
 */
class LatestViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val sourcesRepository = ServiceLocator.sources
    private val settings = ServiceLocator.settings

    val sources: StateFlow<List<SourceInfo>> = sourcesRepository.enabledSources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Null means every enabled source, which is the default feed. */
    val selectedSourceId: StateFlow<String?> = settings.latestSourceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow<LatestUiState>(LatestUiState.Loading)
    val state: StateFlow<LatestUiState> = _state.asStateFlow()

    private val _searchState = MutableStateFlow<LatestSearchState>(LatestSearchState.Idle)
    val searchState: StateFlow<LatestSearchState> = _searchState.asStateFlow()

    var query by mutableStateOf("")
        private set

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var enrichJob: Job? = null
    private var nextPage = 2
    private var feedSources: List<SourceInfo> = emptyList()

    init {
        viewModelScope.launch {
            combine(settings.latestSourceId, sourcesRepository.enabledSources) { id, enabled ->
                id to enabled
            }.collectLatest { (id, enabled) ->
                if (query.isBlank()) load(id, enabled)
            }
        }
    }

    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _searchState.value = LatestSearchState.Idle
            return
        }
        _searchState.value = LatestSearchState.Loading
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            search(trimmed)
        }
    }

    /** Null returns to the merged feed of every enabled source. */
    fun selectSource(sourceId: String?) {
        viewModelScope.launch { settings.setLatestSourceId(sourceId) }
    }

    fun retry() {
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { search(query.trim()) }
            return
        }
        load(selectedSourceId.value, sourcesRepository.enabledSources.value)
    }

    fun clearSearch() {
        query = ""
        searchJob?.cancel()
        _searchState.value = LatestSearchState.Idle
        if (_state.value !is LatestUiState.Content) {
            load(selectedSourceId.value, sourcesRepository.enabledSources.value)
        }
    }

    /** Manual retry for the next-page footer. */
    fun retryNextPage() {
        val content = _state.value as? LatestUiState.Content ?: return
        if (content.loadingMore) return
        loadMore()
    }

    fun loadMore() {
        if (query.isNotBlank()) return
        val content = _state.value as? LatestUiState.Content ?: return
        if (content.loadingMore || content.endReached) return
        val scope = feedSources
        if (scope.isEmpty()) return
        val page = nextPage
        _state.value = content.copy(loadingMore = true, appendError = null)
        loadJob = viewModelScope.launch {
            val merged = LinkedHashMap<String, Manga>().apply {
                content.mangas.forEach { put(it.key, it) }
            }
            val pages = coroutineScope {
                scope.map { source ->
                    async(Dispatchers.IO) {
                        runCatching { feedOf(source, page) }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
            var added = 0
            pages.flatten().forEach { manga ->
                if (merged.put(manga.key, manga) == null) added++
            }
            if (added == 0) {
                _state.value = content.copy(loadingMore = false, endReached = true)
                return@launch
            }
            nextPage = page + 1
            // The source's own order is kept: nothing re-sorts under the reader's finger.
            _state.value = content.copy(
                mangas = merged.values.toList(),
                loadingMore = false,
            )
            enrichNewest(merged.values.toList())
        }
    }

    private fun load(preferred: String?, sources: List<SourceInfo>) {
        loadJob?.cancel()
        enrichJob?.cancel()
        if (sources.isEmpty()) {
            feedSources = emptyList()
            _state.value = LatestUiState.NoSources
            return
        }
        // One source is always chosen: the first enabled one until the reader picks another.
        val scope = preferred?.let { id -> sources.filter { it.id == id } }
            ?.takeIf { it.isNotEmpty() }
            ?: sources.take(1)
        feedSources = scope
        nextPage = 2
        loadJob = viewModelScope.launch {
            _state.value = LatestUiState.Loading
            val merged = LinkedHashMap<String, Manga>()
            val failures = mutableListOf<String>()
            var lastFailure: SourceFailure? = null
            val channel = Channel<SourceFeed>(Channel.UNLIMITED)
            coroutineScope {
                launch {
                    scope.forEach { source ->
                        launch(Dispatchers.IO) {
                            val result = runCatching { feedOf(source, 1) }
                            channel.send(
                                SourceFeed(
                                    sourceName = source.name,
                                    mangas = result.getOrDefault(emptyList()),
                                    failure = result.exceptionOrNull(),
                                ),
                            )
                        }
                    }
                }
                repeat(scope.size) {
                    val feed = channel.receive()
                    if (feed.failure != null) {
                        failures += feed.sourceName
                        lastFailure = (feed.failure as? SourceUnavailableException)?.failure
                            ?: SourceFailure.NETWORK
                    }
                    feed.mangas.forEach { merged[it.key] = it }
                    if (merged.isNotEmpty()) publish(merged, failures)
                }
                channel.close()
            }
            when {
                merged.isNotEmpty() -> {
                    publish(merged, failures, endReached = merged.size < PAGE_HINT)
                    enrichNewest(merged.values.toList())
                }
                failures.size == scope.size -> _state.value = LatestUiState.Error(
                    lastFailure ?: SourceFailure.NETWORK,
                )
                else -> _state.value = LatestUiState.Empty
            }
        }
    }

    private fun publish(
        merged: Map<String, Manga>,
        failures: List<String>,
        endReached: Boolean = false,
    ) {
        _state.value = LatestUiState.Content(
            mangas = merged.values.toList(),
            loadingMore = false,
            endReached = endReached,
            failedSources = failures.toList(),
        )
    }

    private suspend fun feedOf(source: SourceInfo, page: Int): List<Manga> =
        if (source.supportsLatest) repository.latest(source.id, page)
        else repository.popular(source.id, page)

    /**
     * A source may list a work without the newest chapter or its timestamp. Both are resolved
     * from the source itself, then the feed is re-sorted, so the badge and the relative time
     * appear for every work instead of only for the sources that happen to send them.
     */
    private fun enrichNewest(mangas: List<Manga>) {
        enrichJob?.cancel()
        val pending = mangas
            .filter { it.latestChapter == null || it.updatedAt == null }
            .take(MAX_ENRICH)
        if (pending.isEmpty()) return
        enrichJob = viewModelScope.launch {
            val semaphore = Semaphore(4)
            coroutineScope {
                pending.map { manga ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            runCatching {
                                repository.latestChapter(manga.sourceId, manga.id)
                            }.getOrNull()?.let { chapter ->
                                applyChapter(manga.key, chapter.number, chapter.id, chapter.publishedAt)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun applyChapter(key: String, number: String?, chapterId: String?, publishedAt: Long?) {
        val current = _state.value as? LatestUiState.Content ?: return
        val updated = current.mangas.map { item ->
            if (item.key != key) {
                item
            } else {
                item.copy(
                    latestChapter = number ?: item.latestChapter,
                    latestChapterId = chapterId ?: item.latestChapterId,
                    updatedAt = publishedAt ?: item.updatedAt,
                )
            }
        }
        // Updated in place: resolving a missing date must not move a card to another row.
        _state.value = current.copy(mangas = updated)
    }

    private suspend fun search(text: String) {
        // A chosen source scopes the search; otherwise every enabled source is asked.
        val allowed = sourcesRepository.enabledSources.value
        val chosen = selectedSourceId.value ?: allowed.firstOrNull()?.id
        val allSources = chosen?.let { id -> allowed.filter { it.id == id } }.orEmpty()
        if (allSources.isEmpty()) {
            _searchState.value = LatestSearchState.Error(SourceFailure.NO_SOURCES)
            return
        }
        _searchState.value = LatestSearchState.Loading
        val channel = Channel<SearchSourceResult>(Channel.UNLIMITED)
        coroutineScope {
            allSources.forEach { source ->
                launch(Dispatchers.IO) {
                    val result = runCatching { repository.search(source.id, text, 1) }
                    channel.send(
                        SearchSourceResult(
                            sourceName = source.name,
                            mangas = result.getOrDefault(emptyList()),
                            failure = result.exceptionOrNull(),
                        ),
                    )
                }
            }
            val merged = LinkedHashMap<String, Manga>()
            val failures = mutableListOf<String>()
            var finished = 0
            repeat(allSources.size) {
                val result = channel.receive()
                finished++
                result.failure?.let {
                    failures += result.sourceName
                }
                result.mangas.forEach { manga ->
                    val key = dedupeKey(manga)
                    if (key !in merged) merged[key] = manga
                }
                _searchState.value = LatestSearchState.Content(
                    mangas = merged.values.toList(),
                    failedSources = failures.toList(),
                    finishedSources = finished,
                    totalSources = allSources.size,
                )
            }
            channel.close()
            if (merged.isEmpty() && failures.size == allSources.size) {
                _searchState.value = LatestSearchState.Error(SourceFailure.NETWORK)
            }
        }
    }

    private fun dedupeKey(manga: Manga): String {
        val title = SearchText.normalize(manga.title)
        val author = manga.author?.let(SearchText::normalize).orEmpty()
        return if (title.isNotBlank() && author.isNotBlank()) "$title|$author" else manga.key
    }

    private data class SourceFeed(
        val sourceName: String,
        val mangas: List<Manga>,
        val failure: Throwable?,
    )

    private data class SearchSourceResult(
        val sourceName: String,
        val mangas: List<Manga>,
        val failure: Throwable?,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L

        /** How many undated works one refresh may resolve, to stay light on the sources. */
        const val MAX_ENRICH = 60

        /** A page smaller than this hints that the source has nothing left to page through. */
        const val PAGE_HINT = 24
    }
}

/** Newest first, using the real publication time; works without a timestamp go last. */
internal fun Collection<Manga>.sortedNewestFirst(): List<Manga> =
    sortedWith(
        compareByDescending<Manga> { it.updatedAt ?: Long.MIN_VALUE }
            .thenByDescending { it.latestChapter?.toDoubleOrNull() ?: -1.0 }
            .thenBy { it.title },
    )
