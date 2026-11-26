package com.mangaos.app.presentation.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.UiState
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.MangaGrid
import com.mangaos.app.core.ui.components.MangaGridSkeleton
import com.mangaos.app.core.ui.components.MangaList
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.presentation.common.LibraryToggleSheet
import kotlinx.coroutines.launch

@Composable
fun MangaListScreen(
    viewMode: MangaViewMode,
    onMangaClick: (Manga) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MangaListViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryIds by ServiceLocator.library.ids.collectAsStateWithLifecycle()
    val installed by ServiceLocator.extensions.installed.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var sheetManga by remember { mutableStateOf<Manga?>(null) }
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        MangaSearchField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_manga_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        when (val current = state) {
            UiState.Loading -> MangaGridSkeleton()
            UiState.Empty -> EmptyState(
                icon = if (installed.isEmpty()) Icons.Outlined.Extension else Icons.Outlined.MenuBook,
                title = stringResource(
                    if (installed.isEmpty()) R.string.state_no_source_title else R.string.state_empty_title,
                ),
                actionLabel = if (installed.isEmpty()) stringResource(R.string.state_no_source_action) else null,
                onAction = if (installed.isEmpty()) onOpenSources else null,
            )
            is UiState.Error -> EmptyState(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.state_source_unavailable),
                actionLabel = stringResource(R.string.state_error_retry),
                onAction = viewModel::load,
            )
            is UiState.Content -> {
                val visible = filterByQuery(current.data, viewModel.query)
                if (visible.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_no_results),
                    )
                } else if (viewMode == MangaViewMode.GRID) {
                    MangaGrid(
                        mangas = visible,
                        onMangaClick = onMangaClick,
                        gridState = gridState,
                        onMangaLongClick = { sheetManga = it },
                    )
                } else {
                    MangaList(
                        mangas = visible,
                        onMangaClick = onMangaClick,
                        listState = listState,
                        onMangaLongClick = { sheetManga = it },
                    )
                }
            }
        }
    }

    sheetManga?.let { manga ->
        LibraryToggleSheet(
            manga = manga,
            isInLibrary = manga.key in libraryIds,
            onToggle = {
                val added = manga.key !in libraryIds
                ServiceLocator.library.toggle(manga)
                sheetManga = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(if (added) R.string.library_added else R.string.library_removed),
                    )
                }
            },
            onDismiss = { sheetManga = null },
        )
    }
}

internal fun filterByQuery(mangas: List<Manga>, query: String): List<Manga> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return mangas
    return mangas.filter { manga ->
        manga.title.contains(trimmed, ignoreCase = true) ||
            manga.altTitle?.contains(trimmed, ignoreCase = true) == true ||
            manga.author?.contains(trimmed, ignoreCase = true) == true
    }
}
