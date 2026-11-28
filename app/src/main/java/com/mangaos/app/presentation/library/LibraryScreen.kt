package com.mangaos.app.presentation.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.SearchOff
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
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaCollection
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.presentation.auth.AuthMode
import com.mangaos.app.presentation.common.GuestAccountCard
import com.mangaos.app.presentation.common.LibraryToggleSheet
import com.mangaos.app.presentation.common.LocalGuestGate
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewMode: MangaViewMode,
    onMangaClick: (Manga) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val libraryIds by ServiceLocator.library.ids.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var sheetManga by remember { mutableStateOf<Manga?>(null) }
    val snackbarHostState = LocalSnackbarHostState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val guestGate = LocalGuestGate.current

    // A guest has nothing to list here, and the screen says why instead of showing an empty
    // library that looks like a bug.
    if (guestGate.isGuest) {
        GuestLibraryPanel(
            onCreate = { guestGate.start(AuthMode.SIGN_UP) },
            onSignIn = { guestGate.start(AuthMode.SIGN_IN) },
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        MangaSearchField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_library_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        if (works.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.CollectionsBookmark,
                title = stringResource(R.string.library_empty_title),
                actionLabel = stringResource(R.string.library_empty_action),
                onAction = onBrowse,
            )
        } else {
            val visible = viewModel.visible(works, viewModel.query, progress, settings)
            if (visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.search_no_results),
                )
            } else {
                MangaCollection(
                    mangas = visible,
                    viewMode = viewMode,
                    onMangaClick = onMangaClick,
                    onMangaLongClick = { sheetManga = it },
                    gridState = gridState,
                    listState = listState,
                )
            }
        }
    }

    sheetManga?.let { manga ->
        LibraryToggleSheet(
            manga = manga,
            isInLibrary = manga.key in libraryIds,
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

/**
 * The guest library: one glass card that explains the account requirement and offers the two
 * ways to satisfy it, in the same order as every other account prompt in the app.
 */
@Composable
private fun GuestLibraryPanel(
    onCreate: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.Center,
    ) {
        GuestAccountCard(
            titleRes = R.string.guest_library_title,
            messageRes = R.string.guest_library_message,
            onCreate = onCreate,
            onSignIn = onSignIn,
        )
    }
}
