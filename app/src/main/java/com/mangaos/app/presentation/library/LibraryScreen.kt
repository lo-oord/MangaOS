package com.mangaos.app.presentation.library

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
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
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaGrid
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.presentation.common.LibraryToggleSheet
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onMangaClick: (Manga) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val libraryIds by ServiceLocator.library.ids.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    var sheetManga by remember { mutableStateOf<Manga?>(null) }
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (works.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.CollectionsBookmark,
            title = stringResource(R.string.library_empty_title),
            actionLabel = stringResource(R.string.library_empty_action),
            onAction = onBrowse,
            modifier = modifier,
        )
    } else {
        MangaGrid(
            mangas = works,
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
                ServiceLocator.library.toggle(manga)
                sheetManga = null
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.library_removed))
                }
            },
            onDismiss = { sheetManga = null },
        )
    }
}
