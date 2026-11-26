package com.mangaos.app.presentation.latest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.SearchText
import com.mangaos.app.core.ui.UiState
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.MangaGrid
import com.mangaos.app.core.ui.components.MangaGridSkeleton
import com.mangaos.app.core.ui.components.MangaList
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.presentation.common.SourcePickerSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LatestScreen(
    viewMode: MangaViewMode,
    onMangaClick: (Manga) -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LatestViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selectedSourceId by viewModel.selectedSourceId.collectAsStateWithLifecycle()
    val extensionState by ServiceLocator.extensions.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    var showSourcePicker by remember { mutableStateOf(false) }
    var fabVisible by remember { mutableStateOf(true) }
    val prefs by ServiceLocator.settings.appSettings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(viewMode) {
        snapshotFlow {
            val scrolling = if (viewMode == MangaViewMode.GRID) {
                gridState.isScrollInProgress
            } else {
                listState.isScrollInProgress
            }
            val index = if (viewMode == MangaViewMode.GRID) {
                gridState.firstVisibleItemIndex
            } else {
                listState.firstVisibleItemIndex
            }
            index to scrolling
        }.collectLatest { (_, scrolling) ->
            if (scrolling) {
                fabVisible = false
            } else {
                delay(900)
                fabVisible = true
            }
        }
    }

    val icons = remember(extensionState) {
        val content = extensionState as? ExtensionsState.Content
        content?.entries.orEmpty().associate { it.extension.pkgName to it.extension.iconUrl }
    }
    val selectedSource = sources.firstOrNull { it.id == selectedSourceId } ?: sources.firstOrNull()

    Column(modifier = modifier.fillMaxSize()) {
        MangaSearchField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_latest_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        Box(modifier = Modifier.weight(1f)) {
            when (val current = state) {
                LatestUiState.Loading -> MangaGridSkeleton()
                LatestUiState.NoSources -> EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.state_no_source_title),
                    actionLabel = stringResource(R.string.state_no_source_action),
                    onAction = onOpenSources,
                )
                LatestUiState.NoUsableSources -> EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.state_no_usable_sources),
                    actionLabel = stringResource(R.string.state_no_source_action),
                    onAction = onOpenSources,
                )
                LatestUiState.Empty -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.state_empty_title),
                )
                is LatestUiState.Error -> when (current.failure) {
                    ExtensionFailure.OFFLINE, ExtensionFailure.NETWORK -> ErrorState(
                        onRetry = viewModel::retry,
                        offline = current.failure == ExtensionFailure.OFFLINE,
                    )
                    else -> EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = stringResource(R.string.state_source_unavailable),
                        actionLabel = stringResource(R.string.state_error_retry),
                        onAction = viewModel::retry,
                    )
                }
                is LatestUiState.Content -> {
                    val visible = filterByQuery(current.mangas, viewModel.query)
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
                            showLatestChapter = true,
                            showUpdatedAt = true,
                        )
                    } else {
                        MangaList(
                            mangas = visible,
                            onMangaClick = onMangaClick,
                            listState = listState,
                            showLatestChapter = true,
                            showUpdatedAt = true,
                        )
                    }
                }
            }

            if (sources.isNotEmpty()) {
                SourcePickerFab(
                    visible = fabVisible,
                    source = selectedSource,
                    iconUrl = selectedSource?.let { icons[it.pkgName] },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.lg),
                    onClick = {
                        if (prefs.hapticFeedback) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        showSourcePicker = true
                    },
                )
            }
        }
    }

    if (showSourcePicker) {
        SourcePickerSheet(
            sources = sources,
            icons = icons,
            selectedId = selectedSource?.id,
            onSelect = {
                viewModel.selectSource(it)
                showSourcePicker = false
            },
            onManage = {
                showSourcePicker = false
                onOpenSources()
            },
            onDismiss = { showSourcePicker = false },
        )
    }
}

@Composable
private fun SourcePickerFab(
    visible: Boolean,
    source: SourceInfo?,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = Modifier.widthIn(max = 280.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            SourceBadge(source = source, iconUrl = iconUrl)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = source?.name ?: stringResource(R.string.source_choose),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp),
            )
        }
    }
}

@Composable
private fun SourceBadge(source: SourceInfo?, iconUrl: String?) {
    if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun filterByQuery(mangas: List<Manga>, query: String): List<Manga> {
    if (SearchText.normalize(query).isEmpty()) return mangas
    return mangas.filter { SearchText.matches(it.title, query) }
}
