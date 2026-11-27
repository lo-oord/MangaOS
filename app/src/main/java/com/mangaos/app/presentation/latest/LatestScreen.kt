package com.mangaos.app.presentation.latest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Layers
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.MangaCardContext
import com.mangaos.app.core.ui.components.MangaGrid
import com.mangaos.app.core.ui.components.MangaGridSkeleton
import com.mangaos.app.core.ui.components.MangaList
import com.mangaos.app.core.ui.components.MangaListSkeleton
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.domain.model.SourceInfo
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
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val prefs by ServiceLocator.settings.appSettings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    var pickerOpen by remember { mutableStateOf(false) }
    var radialOpen by remember { mutableStateOf(false) }
    var radialCenter by remember { mutableStateOf(Offset.Zero) }
    var radialOrigin by remember { mutableStateOf(Offset.Zero) }
    var radialSelection by remember { mutableStateOf<Int?>(null) }

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
            MangaSearchField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = stringResource(
                    R.string.search_latest_in,
                    selectedSource?.name.orEmpty(),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )

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
                        is LatestUiState.Content -> {
                            val footer: @Composable () -> Unit = {
                                Column {
                                    FeedFooter(
                                        content = current,
                                        onRetry = viewModel::retryNextPage,
                                    )
                                    // Keeps the floating source selector clear of the last row.
                                    Spacer(Modifier.height(64.dp))
                                }
                            }
                            if (viewMode == MangaViewMode.GRID) {
                                MangaGrid(
                                    mangas = current.mangas,
                                    onMangaClick = onMangaClick,
                                    gridState = gridState,
                                    context = MangaCardContext.LATEST,
                                    footer = footer,
                                )
                            } else {
                                MangaList(
                                    mangas = current.mangas,
                                    onMangaClick = onMangaClick,
                                    listState = listState,
                                    context = MangaCardContext.LATEST,
                                    footer = footer,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (sources.isNotEmpty()) {
            CompactSourceButton(
                source = selectedSource,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = Spacing.screen, bottom = Spacing.lg + Spacing.xs),
                onClick = {
                    vibrate()
                    pickerOpen = !pickerOpen
                },
                onLongPressStart = { rootPosition ->
                    vibrate()
                    radialOrigin = rootPosition
                    radialCenter = clampRadialCenter(
                        position = rootPosition,
                        screenWidth = screenWidth,
                        screenHeight = screenHeight,
                        minMargin = with(density) { 62.dp.toPx() },
                        verticalShift = with(density) { 20.dp.toPx() },
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

        // The picker is part of this screen: the sources sit stacked on top of each other,
        // right above the button that opened them. There is no separate sheet and no list page.
        AnimatedVisibility(
            visible = pickerOpen,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = Spacing.screen, bottom = 76.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 168.dp, max = 220.dp)
                        .heightIn(max = 304.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    sources.forEach { source ->
                        SourceStackRow(
                            source = source,
                            selected = source.id == selectedSource?.id,
                            onClick = {
                                vibrate()
                                viewModel.selectSource(source.id)
                                pickerOpen = false
                            },
                        )
                    }
                }
            }
        }

        if (radialOpen) {
            RadialSourceSelector(
                sources = sources.take(MAX_RADIAL),
                center = radialCenter,
                selectedIndex = radialSelection,
            )
        }
    }
}

/** One stacked source chip in the Latest picker: icon, name and the current choice. */
@Composable
private fun SourceStackRow(
    source: SourceInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val icon = source.iconRes
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) scheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = source.name.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = source.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
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
        }
    }
}

@Composable
private fun CompactSourceButton(
    source: SourceInfo?,
    onClick: () -> Unit,
    onLongPressStart: (Offset) -> Unit,
    onLongPressMove: (Offset) -> Unit,
    onLongPressEnd: () -> Unit,
    onLongPressCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Surface(
        modifier = modifier
            .height(40.dp)
            .widthIn(max = 148.dp)
            .clip(RoundedCornerShape(20.dp))
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
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SourceBadge(source = source)
            Text(
                text = source?.name.orEmpty(),
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
    center: Offset,
    selectedIndex: Int?,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val margin = with(density) { if (sources.size > 8) 146.dp.toPx() else 58.dp.toPx() }
    val safeCenter = Offset(
        center.x.coerceIn(margin, (screenWidth - margin).coerceAtLeast(margin)),
        center.y.coerceIn(margin, (screenHeight - margin).coerceAtLeast(margin)),
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
                    SourceBadge(source = source)
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

/**
 * The source's own bundled icon, or its monogram when a definition ships without one. "Every
 * source" has no icon of its own and shows the stacked-sources glyph instead.
 */
@Composable
private fun SourceBadge(source: SourceInfo?) {
    Surface(
        modifier = Modifier.size(30.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val icon = source?.iconRes
            when {
                icon != null -> Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(24.dp),
                )
                source == null -> Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                else -> Text(
                    text = source.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private fun radialPositions(count: Int, center: Offset, density: androidx.compose.ui.unit.Density): List<Offset> {
    if (count <= 0) return emptyList()
    return with(density) {
        fun ring(items: Int, radius: Float, startAngle: Double): List<Offset> =
            List(items) { index ->
                val angle = startAngle + index * 2.0 * Math.PI / items.coerceAtLeast(1)
                Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius,
                )
            }
        if (count <= 8) {
            ring(count, 94.dp.toPx(), -Math.PI / 2.0)
        } else {
            // Two rings keep the icons apart when many sources are installed.
            val inner = (count + 1) / 2
            val outer = count - inner
            ring(inner, 76.dp.toPx(), -Math.PI / 2.0) +
                ring(outer, 138.dp.toPx(), -Math.PI / 2.0 + Math.PI / outer.coerceAtLeast(1))
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

private const val MAX_RADIAL = 16
