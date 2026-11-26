package com.mangaos.app.presentation.latest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.MangaGrid
import com.mangaos.app.core.ui.components.MangaGridSkeleton
import com.mangaos.app.core.ui.components.MangaList
import com.mangaos.app.core.ui.components.MangaListSkeleton
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.presentation.common.SourcePickerSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val selectedSourceId by viewModel.selectedSourceId.collectAsStateWithLifecycle()
    val extensionState by ServiceLocator.extensions.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val prefs by ServiceLocator.settings.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    var showSourcePicker by remember { mutableStateOf(false) }
    var radialOpen by remember { mutableStateOf(false) }
    var radialCenter by remember { mutableStateOf(Offset.Zero) }
    var radialOrigin by remember { mutableStateOf(Offset.Zero) }
    var radialSelection by remember { mutableStateOf<Int?>(null) }

    val icons = remember(extensionState) {
        val content = extensionState as? ExtensionsState.Content
        content?.entries.orEmpty().associate { it.extension.pkgName to it.extension.iconUrl }
    }
    val selectedSource = sources.firstOrNull { it.id == selectedSourceId } ?: sources.firstOrNull()

    fun vibrate() {
        if (prefs.hapticFeedback) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (sources.isNotEmpty()) {
                    CompactSourceButton(
                        source = selectedSource,
                        iconUrl = selectedSource?.let { icons[it.pkgName] },
                        onClick = {
                            vibrate()
                            showSourcePicker = true
                        },
                        onLongPressStart = { rootPosition ->
                            vibrate()
                            radialOrigin = rootPosition
                            radialCenter = clampRadialCenter(
                                position = rootPosition,
                                screenWidth = screenWidth,
                                screenHeight = screenHeight,
                                minMargin = with(density) { 62.dp.toPx() },
                                verticalShift = with(density) { 30.dp.toPx() },
                            )
                            radialSelection = null
                            radialOpen = true
                        },
                        onLongPressMove = { pointer ->
                            val gate = with(density) { 22.dp.toPx() }
                            radialSelection = if ((pointer - radialOrigin).getDistance() > gate) {
                                radialSelectionAt(
                                    pointer = pointer,
                                    center = radialCenter,
                                    count = minOf(sources.size, MAX_RADIAL),
                                    density = density,
                                )
                            } else {
                                null
                            }
                        },
                        onLongPressEnd = {
                            val index = radialSelection
                            if (index != null) {
                                val source = sources.getOrNull(index)
                                if (source != null) viewModel.selectSource(source.id)
                            }
                            radialOpen = false
                            radialSelection = null
                        },
                        onLongPressCancel = {
                            radialOpen = false
                            radialSelection = null
                        },
                    )
                }
                MangaSearchField(
                    value = viewModel.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.search_latest_placeholder),
                    modifier = Modifier.weight(1f),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (viewModel.query.isNotBlank()) {
                    SearchBody(
                        searchState = searchState,
                        query = viewModel.query,
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
                            val footer: @Composable () -> Unit = {
                                FeedFooter(
                                    content = current,
                                    onRetry = viewModel::retryNextPage,
                                )
                            }
                            if (viewMode == MangaViewMode.GRID) {
                                MangaGrid(
                                    mangas = current.mangas,
                                    onMangaClick = onMangaClick,
                                    gridState = gridState,
                                    showLatestChapter = true,
                                    showUpdatedAt = true,
                                    footer = footer,
                                )
                            } else {
                                MangaList(
                                    mangas = current.mangas,
                                    onMangaClick = onMangaClick,
                                    listState = listState,
                                    showLatestChapter = true,
                                    showUpdatedAt = true,
                                    footer = footer,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (radialOpen) {
            RadialSourceSelector(
                sources = sources.take(MAX_RADIAL),
                icons = icons,
                center = radialCenter,
                selectedIndex = radialSelection,
            )
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
private fun SearchBody(
    searchState: LatestSearchState,
    query: String,
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
                if (viewMode == MangaViewMode.GRID) {
                    MangaGrid(
                        mangas = searchState.mangas,
                        onMangaClick = onMangaClick,
                        gridState = gridState,
                        footer = footer,
                    )
                } else {
                    MangaList(
                        mangas = searchState.mangas,
                        onMangaClick = onMangaClick,
                        listState = listState,
                        footer = footer,
                    )
                }
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
            content.endReached -> Text(
                text = stringResource(R.string.feed_end),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactSourceButton(
    source: SourceInfo?,
    iconUrl: String?,
    onClick: () -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Surface(
        modifier = Modifier
            .height(48.dp)
            .widthIn(max = 132.dp)
            .clip(RoundedCornerShape(14.dp))
            .onGloballyPositioned { coordinates = it }
            .radialPress(
                coordinates = { coordinates },
                onClick = onClick,
                onLongPressStart = onLongPressStart,
                onLongPressMove = onLongPressMove,
                onLongPressEnd = onLongPressEnd,
                onLongPressCancel = onLongPressCancel,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SourceBadge(source = source, iconUrl = iconUrl)
            Text(
                text = source?.name ?: stringResource(R.string.source_choose),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.radialPress(
    coordinates: () -> LayoutCoordinates?,
    onClick: () -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
): Modifier = this.then(
    Modifier.radialPointerInput(
        coordinates = coordinates,
        onClick = onClick,
        onLongPressStart = onLongPressStart,
        onLongPressMove = onLongPressMove,
        onLongPressEnd = onLongPressEnd,
        onLongPressCancel = onLongPressCancel,
    ),
)

private fun Modifier.radialPointerInput(
    coordinates: () -> LayoutCoordinates?,
    onClick: () -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
): Modifier = Modifier.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val timeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull null
                if (!change.pressed) return@withTimeoutOrNull change
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        if (timeout != null) {
            onClick()
            return@awaitEachGesture
        }
        val start = coordinates()?.localToRoot(down.position) ?: down.position
        onLongPressStart(start)
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.position != down.position) moved = true
            val pointer = coordinates()?.localToRoot(change.position) ?: change.position
            onLongPressMove(pointer)
            if (!change.pressed) {
                if (moved) onLongPressEnd() else onLongPressCancel()
                break
            }
            change.consume()
        }
    }
}

@Composable
private fun RadialSourceSelector(
    sources: List<SourceInfo>,
    icons: Map<String, String?>,
    center: Offset,
    selectedIndex: Int?,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val safeCenter = Offset(
        center.x.coerceIn(with(density) { 58.dp.toPx() }, screenWidth - with(density) { 58.dp.toPx() }),
        center.y.coerceIn(with(density) { 58.dp.toPx() }, screenHeight - with(density) { 58.dp.toPx() }),
    )
    val itemSize = with(density) { 54.dp.toPx() }
    val positions = radialPositions(sources.size, safeCenter, density)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
    ) {
        positions.forEachIndexed { index, position ->
            val selected = selectedIndex == index
            val scale by animateFloatAsState(if (selected) 1.16f else 1f, label = "radialItem")
            val source = sources[index]
            Surface(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (position.x - itemSize / 2f).roundToInt(),
                            (position.y - itemSize / 2f).roundToInt(),
                        )
                    }
                    .size(54.dp)
                    .scale(scale)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .semantics { contentDescription = source.name },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shadowElevation = if (selected) 10.dp else 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SourceBadge(source = source, iconUrl = icons[source.pkgName])
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp, start = Spacing.xl, end = Spacing.xl),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = selectedIndex?.let { sources.getOrNull(it)?.name }
                    ?: stringResource(R.string.source_radial_hint),
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourceBadge(source: SourceInfo?, iconUrl: String?) {
    if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = source?.name,
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.Extension,
            contentDescription = source?.name,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun radialPositions(count: Int, center: Offset, density: androidx.compose.ui.unit.Density): List<Offset> {
    if (count <= 0) return emptyList()
    return with(density) {
        val radius = 96.dp.toPx()
        List(count) { index ->
            val angle = -Math.PI / 2.0 + index * 2.0 * Math.PI / count
            Offset(
                center.x + cos(angle).toFloat() * radius,
                center.y + sin(angle).toFloat() * radius,
            )
        }
    }
}

private fun radialSelectionAt(
    pointer: Offset,
    center: Offset,
    count: Int,
    density: androidx.compose.ui.unit.Density,
): Int? {
    if (count <= 0) return null
    val positions = radialPositions(count, center, density)
    val nearest = positions.minByOrNull { (it - pointer).getDistance() } ?: return null
    val threshold = with(density) { 76.dp.toPx() }
    return if ((nearest - pointer).getDistance() <= threshold) positions.indexOf(nearest) else null
}

private fun clampRadialCenter(
    position: Offset,
    screenWidth: Float,
    screenHeight: Float,
    minMargin: Float,
    verticalShift: Float,
): Offset = Offset(
    x = position.x.coerceIn(minMargin, (screenWidth - minMargin).coerceAtLeast(minMargin)),
    y = (position.y + verticalShift).coerceIn(
        minimumValue = minMargin,
        maximumValue = (screenHeight - minMargin).coerceAtLeast(minMargin),
    ),
)

private const val MAX_RADIAL = 10
