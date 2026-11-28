package com.mangaos.app.presentation.latest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.util.SearchText
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
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
import kotlinx.coroutines.withTimeoutOrNull

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
            val incoming = pages.flatten().filterNot { it.key in merged }
            val ready = withTimeoutOrNull(RESOLVE_TOTAL_MS) { resolveCards(incoming) } ?: incoming
            // A page is ordered before it is appended, so a source that answers out of order
            // cannot place an older work above a newer one. The cards already on screen keep
            // their positions: the feed only re-sorts when the page is newer than its tail.
            val ordered = ready.sortedNewestFirst()
            val added = ordered.count { merged.put(it.key, it) == null }
            if (added == 0) {
                _state.value = content.copy(loadingMore = false, endReached = true)
                return@launch
            }
            nextPage = page + 1
            val feed = merged.values.toList().let { items ->
                if (isNewerThanFeed(ordered, content.mangas)) items.sortedNewestFirst() else items
            }
            _state.value = content.copy(
                mangas = feed,
                loadingMore = false,
            )
            enrichNewest(feed)
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
                }
                channel.close()
            }
            when {
                merged.isNotEmpty() -> {
                    // Every card is published complete: its chapter number and date are
                    // resolved first, so the metadata never pops in after the card is visible.
                    // A source that cannot answer must never hold the first paint hostage:
                    // the page is bounded, and anything still unresolved is filled in after
                    // the cards are on screen.
                    val listed = merged.values.toList()
                    val ready = withTimeoutOrNull(RESOLVE_TOTAL_MS) { resolveCards(listed) }
                        ?: listed
                    // The end of a feed is never inferred from a page's size: a source may
                    // return fewer cards than a page hint (novels filtered out, a smaller page
                    // size), which used to stop the feed after the first page. Paging ends only
                    // when a page actually brings nothing new.
                    publish(LinkedHashMap<String, Manga>().apply {
                        ready.forEach { put(it.key, it) }
                    }, failures)
                    enrichNewest(ready)
                }
                failures.size == scope.size -> _state.value = LatestUiState.Error(
                    lastFailure ?: SourceFailure.NETWORK,
                )
                else -> _state.value = LatestUiState.Empty
            }
        }
    }

    /** True when an arriving page holds a work newer than anything already on screen. */
    private fun isNewerThanFeed(incoming: List<Manga>, shown: List<Manga>): Boolean {
        val newestShown = shown.firstNotNullOfOrNull { it.updatedAt } ?: return false
        return incoming.any { (it.updatedAt ?: Long.MIN_VALUE) > newestShown }
    }

    private fun publish(
        merged: Map<String, Manga>,
        failures: List<String>,
        endReached: Boolean = false,
    ) {
        _state.value = LatestUiState.Content(
            // The feed is newest first on its very first paint, not only after an enrichment
            // pass: undated works are never treated as the newest (they keep their source order).
            mangas = merged.values.sortedNewestFirst(),
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
    /**
     * Fills the newest chapter and its publication date for a page of cards before they are
     * shown. Order is preserved exactly, and a source that cannot answer leaves its work as it
     * is instead of blocking the page.
     */
    private suspend fun resolveCards(mangas: List<Manga>): List<Manga> = coroutineScope {
        val semaphore = Semaphore(RESOLVE_CONCURRENCY)
        mangas.map { manga ->
            async(Dispatchers.IO) {
                if (manga.latestChapter != null && manga.updatedAt != null &&
                    !manga.needsMetadata()
                ) {
                    manga
                } else {
                    semaphore.withPermit {
                        var resolved = manga
                        if (resolved.latestChapter == null || resolved.updatedAt == null) {
                            runCatching {
                                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                                    repository.latestChapter(resolved.sourceId, resolved.id)
                                }
                            }.getOrNull()?.let { chapter ->
                                resolved = resolved.copy(
                                    latestChapter = chapter.number.takeIf { it.isNotBlank() }
                                        ?: resolved.latestChapter,
                                    latestChapterId = chapter.id,
                                    updatedAt = chapter.publishedAt ?: resolved.updatedAt,
                                )
                            }
                        }
                        // Some sources list a work with neither its state nor a genre (their
                        // card markup carries neither). The detail page does, so it is read
                        // for those cards only, and the card is never shown half empty.
                        if (resolved.needsMetadata()) {
                            runCatching {
                                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                                    repository.byId(resolved.sourceId, resolved.id)
                                }
                            }.getOrNull()?.let { details ->
                                resolved = resolved.copy(
                                    status = if (details.status != MangaStatus.UNKNOWN) {
                                        details.status
                                    } else {
                                        resolved.status
                                    },
                                    genres = details.genres.takeIf { it.isNotEmpty() }
                                        ?: resolved.genres,
                                    author = resolved.author ?: details.author,
                                    coverUrl = resolved.coverUrl ?: details.coverUrl,
                                )
                            }
                        }
                        resolved
                    }
                }
            }
        }.awaitAll()
    }

    /** A card with no state and no genre still needs its metadata resolved. */
    private fun Manga.needsMetadata(): Boolean =
        status == MangaStatus.UNKNOWN && genres.isEmpty()

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

        /** Cards resolved side by side; enough to be quick without hammering one source. */
        const val RESOLVE_CONCURRENCY = 6
        const val RESOLVE_TIMEOUT_MS = 8_000L
        const val RESOLVE_TOTAL_MS = 14_000L
    }
}

/**
 * Newest first, using the real publication time of the newest chapter. A work without a date is
 * never treated as the newest: it sorts after every dated work and keeps the source's own order,
 * because [sortedWith] is a stable sort — so equal (or missing) dates never reshuffle a feed.
 */
internal fun Collection<Manga>.sortedNewestFirst(): List<Manga> =
    sortedWith(
        compareByDescending<Manga> { it.updatedAt ?: Long.MIN_VALUE }
            .thenByDescending { it.latestChapter?.toDoubleOrNull() ?: -1.0 },
    )
