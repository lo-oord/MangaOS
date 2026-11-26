package com.mangaos.app.presentation.manga

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
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
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.UiState
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.MangaGrid
import com.mangaos.app.core.ui.components.MangaGridSkeleton
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.presentation.common.LibraryToggleSheet
import kotlinx.coroutines.launch

@Composable
fun MangaListScreen(
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MangaListViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val libraryIds by ServiceLocator.library.ids.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    var sheetManga by remember { mutableStateOf<Manga?>(null) }
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when (val current = state) {
        UiState.Loading -> MangaGridSkeleton(modifier = modifier)
        UiState.Empty -> EmptyState(
            icon = Icons.Outlined.MenuBook,
            title = stringResource(R.string.state_empty_title),
            modifier = modifier,
        )
        is UiState.Error -> ErrorState(
            onRetry = viewModel::load,
            offline = current.offline,
            modifier = modifier,
        )
        is UiState.Content -> MangaGrid(
            mangas = current.data,
            onMangaClick = onMangaClick,
            modifier = modifier,
            gridState = gridState,
            onMangaLongClick = { sheetManga = it },
        )
    }

    sheetManga?.let { manga ->
        LibraryToggleSheet(
            manga = manga,
            isInLibrary = manga.id in libraryIds,
            onToggle = {
                val added = manga.id !in libraryIds
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
