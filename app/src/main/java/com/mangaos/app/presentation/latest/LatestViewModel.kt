package com.mangaos.app.presentation.latest

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.util.SearchText
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.Manga
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

    /** Extensions are installed but none of them produced a usable source. */
    data object NoUsableSources : LatestUiState
    data object Empty : LatestUiState

    data class Content(
        val mangas: List<Manga>,
        val loadingMore: Boolean = false,
        val endReached: Boolean = false,
        val appendError: ExtensionFailure? = null,
    ) : LatestUiState

    data class Error(val failure: ExtensionFailure) : LatestUiState
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

    data class Error(val failure: ExtensionFailure) : LatestSearchState
}

class LatestViewModel : ViewModel() {

    private val repository = ServiceLocator.manga
    private val extensions = ServiceLocator.extensions
    private val settings = ServiceLocator.settings

    val sources: StateFlow<List<SourceInfo>> = combine(extensions.sources, extensions.installed) { list, installed ->
        list.filter { it.pkgName in installed }.sortedBy { source -> source.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
    private var currentSource: SourceInfo? = null

    init {
        viewModelScope.launch {
            combine(settings.latestSourceId, extensions.sources, extensions.installed) { id, sources, installed ->
                Triple(id, sources, installed)
            }.collectLatest { (id, sources, installed) ->
                if (query.isBlank()) load(id, sources, installed.isNotEmpty())
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

    fun selectSource(sourceId: String) {
        viewModelScope.launch { settings.setLatestSourceId(sourceId) }
    }

    fun retry() {
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { search(query.trim()) }
            return
        }
        viewModelScope.launch { extensions.refresh(force = true) }
        load(selectedSourceId.value, extensions.sources.value, extensions.installed.value.isNotEmpty())
    }

    fun clearSearch() {
        query = ""
        searchJob?.cancel()
        _searchState.value = LatestSearchState.Idle
        if (_state.value !is LatestUiState.Content) {
            load(selectedSourceId.value, extensions.sources.value, extensions.installed.value.isNotEmpty())
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
        val source = currentSource ?: return
        _state.value = content.copy(loadingMore = true, appendError = null)
        loadJob = viewModelScope.launch {
            val page = nextPage
            val result = runCatching {
                if (source.supportsLatest) repository.latest(source.id, page)
                else repository.popular(source.id, page)
            }
            result.onSuccess { incoming ->
                val existing = (content.mangas + incoming)
                    .distinctBy { it.key }
                val added = existing.size - content.mangas.size
                if (incoming.isEmpty() || added == 0) {
                    _state.value = content.copy(loadingMore = false, endReached = true)
                } else {
                    nextPage = page + 1
                    _state.value = content.copy(mangas = existing, loadingMore = false)
                    enrichLatest(incoming)
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _state.value = content.copy(
                    loadingMore = false,
                    appendError = (error as? SourceUnavailableException)?.failure ?: ExtensionFailure.NETWORK,
                )
            }
        }
    }

    private fun load(preferred: String?, sources: List<SourceInfo>, hasInstalledExtensions: Boolean) {
        loadJob?.cancel()
        enrichJob?.cancel()
        if (sources.isEmpty()) {
            currentSource = null
            _state.value = if (hasInstalledExtensions) {
                LatestUiState.NoUsableSources
            } else {
                LatestUiState.NoSources
            }
            return
        }
        val chosen = sources.firstOrNull { it.id == preferred } ?: sources.first()
        currentSource = chosen
        if (preferred != chosen.id) {
            viewModelScope.launch { settings.setLatestSourceId(chosen.id) }
        }
        nextPage = 2
        loadJob = viewModelScope.launch {
            _state.value = LatestUiState.Loading
            runCatching {
                if (chosen.supportsLatest) repository.latest(chosen.id, 1)
                else repository.popular(chosen.id, 1)
            }.onSuccess { mangas ->
                _state.value = if (mangas.isEmpty()) {
                    LatestUiState.Empty
                } else {
                    LatestUiState.Content(mangas)
                }
                if (mangas.isNotEmpty()) enrichLatest(mangas)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                _state.value = LatestUiState.Error(
                    (error as? SourceUnavailableException)?.failure ?: ExtensionFailure.NETWORK,
                )
            }
        }
    }

    /**
     * Latest feeds do not carry a chapter in the shared source API. Older Mihon clients get
     * that number from the library; MangaOS resolves it from the source after showing the
     * covers, so the feed stays fast and the badge appears as soon as it is real.
     */
    private fun enrichLatest(mangas: List<Manga>) {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val semaphore = Semaphore(4)
            val pending = mangas.filter { it.latestChapter == null }.take(MAX_ENRICH)
            coroutineScope {
                pending.map { manga ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            runCatching {
                                repository.latestChapter(manga.sourceId, manga.id)
                            }.getOrNull()?.let { chapter ->
                                _state.value = when (val current = _state.value) {
                                    is LatestUiState.Content -> current.copy(
                                        mangas = current.mangas.map { item ->
                                            if (item.key == manga.key) {
                                                item.copy(
                                                    latestChapter = chapter.number,
                                                    latestChapterId = chapter.id,
                                                    updatedAt = chapter.publishedAt ?: item.updatedAt,
                                                )
                                            } else {
                                                item
                                            }
                                        },
                                    )
                                    else -> current
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun search(text: String) {
        val allSources = sources.value
        if (allSources.isEmpty()) {
            _searchState.value = LatestSearchState.Error(ExtensionFailure.NO_SOURCES)
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
                _searchState.value = LatestSearchState.Error(ExtensionFailure.NETWORK)
            }
        }
    }

    private fun dedupeKey(manga: Manga): String {
        val title = SearchText.normalize(manga.title)
        val author = manga.author?.let(SearchText::normalize).orEmpty()
        return if (title.isNotBlank() && author.isNotBlank()) "$title|$author" else manga.key
    }

    private data class SearchSourceResult(
        val sourceName: String,
        val mangas: List<Manga>,
        val failure: Throwable?,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val MAX_ENRICH = 4
    }
}
