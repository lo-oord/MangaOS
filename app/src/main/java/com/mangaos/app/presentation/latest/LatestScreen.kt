package com.mangaos.app.presentation.latest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.MangaCardContext
import com.mangaos.app.core.ui.components.MangaCollection
import com.mangaos.app.core.ui.components.MangaGridSkeleton
import com.mangaos.app.core.ui.components.MangaListSkeleton
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.SourceFailure
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Latest feed.
 *
 * It reads one source — the reader's choice, or the primary server when nothing was chosen — and
 * exposes no source switcher of its own: choosing a server belongs to the Sources screen, so this
 * page has nothing above the feed that could shift the cards away from where they were.
 */
@Composable
fun LatestScreen(
    viewMode: MangaViewMode,
    onMangaClick: (Manga) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LatestViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // Only the empty/non-empty answer is read here. Reading the query itself would recompose the
    // whole feed — and every card in it — on each keystroke.
    val searchActive by remember { derivedStateOf { viewModel.query.isNotBlank() } }

    LaunchedEffect(viewMode) {
        snapshotFlow {
            if (viewMode == MangaViewMode.GRID) {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            } else {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            }
        }.distinctUntilChanged().collect { lastIndex ->
            val current = viewModel.state.value as? LatestUiState.Content ?: return@collect
            if (lastIndex >= current.mangas.size - 3) viewModel.loadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LatestSearchBar(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (searchActive) {
                SearchBody(
                    searchState = searchState,
                    viewMode = viewMode,
                    gridState = gridState,
                    listState = listState,
                    onMangaClick = onMangaClick,
                    onRetry = viewModel::retry,
                )
            } else {
                when (val current = state) {
                    LatestUiState.Loading -> if (viewMode == MangaViewMode.GRID) {
                        MangaGridSkeleton()
                    } else {
                        MangaListSkeleton()
                    }
                    LatestUiState.NoSources -> EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = stringResource(R.string.state_no_source_title),
                        actionLabel = stringResource(R.string.state_no_source_action),
                        onAction = onOpenSources,
                    )
                    LatestUiState.Empty -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.state_empty_title),
                    )
                    is LatestUiState.Error -> when (current.failure) {
                        SourceFailure.OFFLINE, SourceFailure.NETWORK -> ErrorState(
                            onRetry = viewModel::retry,
                            offline = current.failure == SourceFailure.OFFLINE,
                        )
                        else -> EmptyState(
                            icon = Icons.Outlined.CloudOff,
                            title = stringResource(R.string.state_source_unavailable),
                            actionLabel = stringResource(R.string.state_error_retry),
                            onAction = viewModel::retry,
                        )
                    }
                    is LatestUiState.Content -> MangaCollection(
                        mangas = current.mangas,
                        viewMode = viewMode,
                        onMangaClick = onMangaClick,
                        context = MangaCardContext.LATEST,
                        gridState = gridState,
                        listState = listState,
                        footer = {
                            FeedFooter(content = current, onRetry = viewModel::retryNextPage)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The search field owns its own state. Because the query is read here and not by the feed, a
 * keystroke recomposes this row only; the grid, its cards and their images stay untouched.
 */
@Composable
private fun LatestSearchBar(viewModel: LatestViewModel, modifier: Modifier = Modifier) {
    MangaSearchField(
        value = viewModel.query,
        onValueChange = viewModel::onQueryChange,
        placeholder = stringResource(R.string.search_latest),
        modifier = modifier,
    )
}

@Composable
private fun SearchBody(
    searchState: LatestSearchState,
    viewMode: MangaViewMode,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onMangaClick: (Manga) -> Unit,
    onRetry: () -> Unit,
) {
    when (searchState) {
        LatestSearchState.Idle, LatestSearchState.Loading -> if (viewMode == MangaViewMode.GRID) {
            MangaGridSkeleton()
        } else {
            MangaListSkeleton()
        }
        is LatestSearchState.Error -> ErrorState(onRetry = onRetry)
        is LatestSearchState.Content -> {
            if (searchState.mangas.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.search_no_results),
                )
            } else {
                val footer: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (searchState.finishedSources < searchState.totalSources) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (searchState.failedSources.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.search_sources_failed,
                                    searchState.failedSources.size,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                MangaCollection(
                    mangas = searchState.mangas,
                    viewMode = viewMode,
                    onMangaClick = onMangaClick,
                    gridState = gridState,
                    listState = listState,
                    footer = footer,
                )
            }
        }
    }
}

@Composable
private fun FeedFooter(
    content: LatestUiState.Content,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            content.loadingMore -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            content.appendError != null -> TextButton(onClick = onRetry) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.state_error_retry), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
