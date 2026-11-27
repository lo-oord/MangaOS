package com.mangaos.app.presentation.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.mangaos.app.R
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.util.findActivity
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.session.ReaderSession
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Headers

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    sourceId: String,
    mangaId: String,
    chapterId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ReaderViewModel = viewModel()
    LaunchedEffect(sourceId, mangaId, chapterId) { viewModel.load(sourceId, mangaId, chapterId) }
    DisposableEffect(sourceId, mangaId) {
        val key = "$sourceId/$mangaId"
        ReaderSession.enter(key)
        onDispose { ReaderSession.exit(key) }
    }

    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val imageStates by viewModel.imageStates.collectAsStateWithLifecycle()
    val imageRatios by viewModel.imageRatios.collectAsStateWithLifecycle()
    val manga by viewModel.manga.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val nextChapterLoading by viewModel.nextChapterLoading.collectAsStateWithLifecycle()
    val nextChapterError by viewModel.nextChapterError.collectAsStateWithLifecycle()
    val previousChapterLoading by viewModel.previousChapterLoading.collectAsStateWithLifecycle()
    val previousChapterError by viewModel.previousChapterError.collectAsStateWithLifecycle()
    val autoWebtoon by viewModel.autoWebtoon.collectAsStateWithLifecycle()
    val initialPage by viewModel.initialPage.collectAsStateWithLifecycle()
    val readingFraction by viewModel.readingFraction.collectAsStateWithLifecycle()

    val mode = if (settings.autoWebtoonDetection && autoWebtoon &&
        settings.readingMode == ReaderMode.VERTICAL
    ) {
        ReaderMode.WEBTOON
    } else {
        settings.readingMode
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var restored by remember(sourceId, mangaId, chapterId) { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Anchor used to keep the visible page still when a chapter is inserted above it.
    var anchorKey by remember { mutableStateOf<String?>(null) }
    var anchorOffset by remember { mutableStateOf(0) }
    var firstPageKey by remember { mutableStateOf<String?>(null) }
    val latestPages = rememberUpdatedState(pages)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findActivity()
    val window = activity?.window
    val reduceMotion = LocalReduceMotion.current

    DisposableEffect(settings.keepScreenOn) {
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(settings.screenOrientation) {
        activity?.requestedOrientation = when (settings.screenOrientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(settings.fullscreen, settings.hideStatusBar) {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (settings.fullscreen || settings.hideStatusBar) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(controlsVisible, settings.autoHideControls, settings.autoHideDelayMs, dragging) {
        if (controlsVisible && settings.autoHideControls && !dragging) {
            delay(settings.autoHideDelayMs)
            controlsVisible = false
        }
    }

    LaunchedEffect(pages.isNotEmpty(), initialPage) {
        if (!restored && pages.isNotEmpty()) {
            val target = if (mode == ReaderMode.DOUBLE_PAGE) {
                initialPage / 2
            } else {
                initialPage
            }
            listState.scrollToItem(target.coerceIn(0, pages.lastIndex.coerceAtLeast(0)))
            restored = true
        }
    }

    LaunchedEffect(mode) {
        viewModel.scrollRequests.collect { pageIndex ->
            val itemIndex = if (mode == ReaderMode.DOUBLE_PAGE) pageIndex / 2 else pageIndex
            listState.scrollToItem(itemIndex.coerceAtLeast(0))
            restored = true
        }
    }

    // Remembers the exact page and pixel offset the user is looking at, independent of the
    // list index, so it survives content being inserted or removed above it.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            latestPages.value.getOrNull(index)?.let { page ->
                anchorKey = page.key
                anchorOffset = offset
            }
        }
    }

    // A chapter inserted above shifts every index. Re-anchor on the same page before the next
    // remeasure: the reader continues from the same image instead of jumping to a new place.
    LaunchedEffect(pages) {
        val newFirstKey = pages.firstOrNull()?.key
        val anchor = anchorKey
        if (firstPageKey != null && newFirstKey != firstPageKey && anchor != null) {
            val target = pages.indexOfFirst { it.key == anchor }
            if (target >= 0) {
                listState.requestScrollToItem(target, anchorOffset)
            }
        }
        firstPageKey = newFirstKey
    }

    LaunchedEffect(mode, pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { visible ->
                delay(220)
                val pageIndex = if (mode == ReaderMode.DOUBLE_PAGE) visible * 2 else visible
                viewModel.onPageChanged(pageIndex)
                viewModel.ensureNextLoaded(pageIndex)
                viewModel.ensurePreviousLoaded(pageIndex)
            }
    }

    // Auto scroll is real scrolling of the same list the user controls, at the chosen speed.
    LaunchedEffect(settings.autoScroll, settings.autoScrollSpeed, mode) {
        if (!settings.autoScroll) return@LaunchedEffect
        if (mode != ReaderMode.VERTICAL && mode != ReaderMode.WEBTOON) return@LaunchedEffect
        while (true) {
            delay(40)
            listState.scrollBy(settings.autoScrollSpeed * 2.2f)
        }
    }

    // Warm only the pages just ahead of the reader, in reading order and through the exact same
    // request (and cache keys) the visible pages use: a prefetched page becomes a cache hit
    // instead of a second download, and the page on screen is never requested again here.
    LaunchedEffect(listState.firstVisibleItemIndex, pages.size, settings.preloadImages) {
        if (pages.isEmpty() || settings.preloadImages <= 0) return@LaunchedEffect
        val loader = context.imageLoader
        val start = listState.firstVisibleItemIndex + 1
        pages.drop(start)
            .take(settings.preloadImages)
            .forEach { page -> loader.enqueue(readerImageRequest(context, page)) }
    }

    val enterSpec = tween<Float>(if (reduceMotion) 0 else 170)
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(if (reduceMotion) 0 else 170)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor(settings))
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val pageWidth = maxWidth
            val pageHeight = maxHeight
            when (mode) {
                ReaderMode.VERTICAL, ReaderMode.WEBTOON -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(pages, key = { _, page -> page.key }) { _, page ->
                            ReaderImage(
                                page = page,
                                state = imageStates[page.key] ?: ImageState.Loading,
                                ratio = imageRatios[page.key],
                                settings = settings,
                                vertical = true,
                                onRetry = { viewModel.retryImage(page.key) },
                                onSuccess = { width, height -> viewModel.onImageSuccess(page.key, width, height) },
                                onError = { viewModel.onImageError(page.key) },
                                onTap = { controlsVisible = !controlsVisible },
                            )
                            if (settings.imageSpacingDp > 0) {
                                Spacer(Modifier.height(settings.imageSpacingDp.dp))
                            }
                        }
                    }
                }

                ReaderMode.SINGLE_PAGE -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                        ) {
                            itemsIndexed(pages, key = { _, page -> page.key }) { _, page ->
                                ReaderImage(
                                    page = page,
                                    state = imageStates[page.key] ?: ImageState.Loading,
                                    ratio = imageRatios[page.key],
                                    settings = settings,
                                    vertical = false,
                                    onRetry = { viewModel.retryImage(page.key) },
                                    onSuccess = { width, height -> viewModel.onImageSuccess(page.key, width, height) },
                                    onError = { viewModel.onImageError(page.key) },
                                    onTap = { controlsVisible = !controlsVisible },
                                    modifier = Modifier
                                        .width(pageWidth)
                                        .fillMaxHeight()
                                        .padding(settings.marginsDp.dp),
                                )
                            }
                        }
                    }
                }

                ReaderMode.DOUBLE_PAGE -> {
                    val chunks = remember(pages) { pages.chunked(2) }
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    ) {
                        itemsIndexed(chunks, key = { index, _ -> "chunk-$index" }) { _, chunk ->
                            Row(
                                modifier = Modifier
                                    .width(pageWidth)
                                    .fillMaxHeight(),
                            ) {
                                chunk.forEach { page ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                    ) {
                                        ReaderImage(
                                            page = page,
                                            state = imageStates[page.key] ?: ImageState.Loading,
                                            ratio = imageRatios[page.key],
                                            settings = settings,
                                            vertical = false,
                                            onRetry = { viewModel.retryImage(page.key) },
                                            onSuccess = { width, height ->
                                                viewModel.onImageSuccess(page.key, width, height)
                                            },
                                            onError = { viewModel.onImageError(page.key) },
                                            onTap = { controlsVisible = !controlsVisible },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(settings.marginsDp.dp / 2),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ReaderMode.HORIZONTAL_LTR,
                ReaderMode.HORIZONTAL_RTL,
                -> {
                    val rtl = mode == ReaderMode.HORIZONTAL_RTL
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    ) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                        ) {
                            itemsIndexed(pages, key = { _, page -> page.key }) { _, page ->
                                ReaderImage(
                                    page = page,
                                    state = imageStates[page.key] ?: ImageState.Loading,
                                    ratio = imageRatios[page.key],
                                    settings = settings,
                                    vertical = false,
                                    onRetry = { viewModel.retryImage(page.key) },
                                    onSuccess = { width, height -> viewModel.onImageSuccess(page.key, width, height) },
                                    onError = { viewModel.onImageError(page.key) },
                                    onTap = { controlsVisible = !controlsVisible },
                                    modifier = Modifier
                                        .width(pageWidth)
                                        .fillMaxHeight()
                                        .padding(settings.marginsDp.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && settings.showTopBar,
            enter = fadeIn(enterSpec) + slideInVertically(slideSpec) { -it / 3 },
            exit = fadeOut(tween(if (reduceMotion) 0 else 130)) +
                slideOutVertically(tween(if (reduceMotion) 0 else 130)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = manga?.title.orEmpty(),
                chapterLabel = chapters.getOrNull(currentChapterIndex)?.let { chapter ->
                    appString(R.string.chapter_number, chapter.number)
                }.orEmpty(),
                coverUrl = manga?.coverUrl,
                onBack = onBack,
            )
        }

        // A small, unobtrusive marker at the top of the content while the previous chapter is
        // being fetched. It never covers the middle of the page being read.
        AnimatedVisibility(
            visible = previousChapterLoading,
            enter = fadeIn(enterSpec),
            exit = fadeOut(tween(if (reduceMotion) 0 else 130)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 52.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.reader_loading_previous),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && settings.showBottomBar,
            enter = fadeIn(enterSpec) + slideInVertically(slideSpec) { it / 4 },
            exit = fadeOut(tween(if (reduceMotion) 0 else 130)) +
                slideOutVertically(tween(if (reduceMotion) 0 else 130)) { it / 4 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val currentChapter = chapters.getOrNull(currentChapterIndex)
            val chapterStart = pages.indexOfFirst { it.chapterId == currentChapter?.id }
            val chapterEnd = pages.indexOfLast { it.chapterId == currentChapter?.id }
            val chapterSize = if (chapterStart >= 0 && chapterEnd >= chapterStart) {
                chapterEnd - chapterStart + 1
            } else {
                0
            }
            val visiblePage = if (mode == ReaderMode.DOUBLE_PAGE) {
                listState.firstVisibleItemIndex * 2
            } else {
                listState.firstVisibleItemIndex
            }
            val progress = if (chapterSize > 1 && chapterStart >= 0) {
                ((visiblePage - chapterStart).toFloat() / (chapterSize - 1)).coerceIn(0f, 1f)
            } else {
                0f
            }
            val pageInChapter = if (chapterStart >= 0 && chapterSize > 0) {
                (visiblePage - chapterStart + 1).coerceIn(1, chapterSize)
            } else {
                0
            }

            ReaderBottomBar(
                pageInChapter = pageInChapter,
                chapterSize = chapterSize,
                progress = progress,
                onSeek = { value ->
                    dragging = true
                    if (chapterSize > 1) {
                        val pageIndex = chapterStart + (value * (chapterSize - 1)).toInt()
                        val itemIndex = if (mode == ReaderMode.DOUBLE_PAGE) pageIndex / 2 else pageIndex
                        scope.launch { listState.scrollToItem(itemIndex) }
                    }
                },
                onSeekFinished = { dragging = false },
                canPrevious = currentChapterIndex > 0,
                canNext = currentChapterIndex < chapters.lastIndex,
                onPrevious = {
                    val previous = chapters.getOrNull(currentChapterIndex - 1)
                    if (previous != null) {
                        val appendedIndex = viewModel.pageIndexOfChapter(previous.id)
                        if (settings.continuousReading &&
                            appendedIndex >= 0 &&
                            mode != ReaderMode.DOUBLE_PAGE
                        ) {
                            scope.launch { listState.scrollToItem(appendedIndex) }
                        } else {
                            viewModel.goToChapter(previous)
                        }
                    }
                },
                onNext = {
                    val next = chapters.getOrNull(currentChapterIndex + 1)
                    if (next != null) {
                        val appendedIndex = viewModel.pageIndexOfChapter(next.id)
                        if (settings.continuousReading &&
                            appendedIndex >= 0 &&
                            mode != ReaderMode.DOUBLE_PAGE
                        ) {
                            scope.launch { listState.scrollToItem(appendedIndex) }
                        } else {
                            viewModel.goToChapter(next)
                        }
                    }
                },
                onOpenChapters = { showChapterSheet = true },
                onOpenSettings = { showQuickSettings = true },
                settings = settings,
                onUpdateSettings = viewModel::updateSettings,
                neighbourLoading = nextChapterLoading || previousChapterLoading,
                neighbourError = nextChapterError || previousChapterError,
                onRetryNeighbour = {
                    if (previousChapterError) viewModel.retryPreviousChapter()
                    else viewModel.retryNextChapter()
                },
            )

        }

        if (isLoading && pages.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }

        if (!isLoading && pages.isEmpty() && error != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.xl),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.reader_chapter_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    TextButton(onClick = { viewModel.retry() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.state_error_retry))
                    }
                }
            }
        }
    }

    if (showQuickSettings) {
        ReaderQuickSettingsSheet(
            settings = settings,
            onUpdate = viewModel::updateSettings,
            onDismiss = { showQuickSettings = false },
            onOpenAdvanced = {
                showQuickSettings = false
                onOpenSettings()
            },
        )
    }

    if (showChapterSheet) {
        ReaderChapterSheet(
            chapters = chapters,
            currentChapterId = chapters.getOrNull(currentChapterIndex)?.id,
            onSelect = { chapter ->
                showChapterSheet = false
                val appendedIndex = viewModel.pageIndexOfChapter(chapter.id)
                if (settings.continuousReading &&
                    appendedIndex >= 0 &&
                    mode != ReaderMode.DOUBLE_PAGE
                ) {
                    scope.launch { listState.scrollToItem(appendedIndex) }
                } else {
                    viewModel.goToChapter(chapter)
                }
            },
            onDismiss = { showChapterSheet = false },
        )
    }
}

/**
 * One compact bottom bar: a precise chapter progress line, the page counter, and only the
 * three navigation controls. Reading mode, direction and image options stay in the settings
 * sheet reachable from the top bar, so no control is duplicated between the two bars.
 */
/** Chapter picker for the reader; the current chapter is highlighted, nothing else changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderChapterSheet(
    chapters: List<Chapter>,
    currentChapterId: String?,
    onSelect: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = stringResource(R.string.reader_chapters_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.xl),
        ) {
            items(chapters, key = { it.id }) { chapter ->
                val current = chapter.id == currentChapterId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(chapter) }
                        .background(
                            if (current) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appString(R.string.chapter_number, chapter.number),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        chapter.title?.takeIf { it.isNotBlank() }?.let { title ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (current) {
                        Text(
                            text = stringResource(R.string.reader_current_chapter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                )
            }
        }
    }
}

@Composable
private fun ReaderImage(
    page: ReaderPage,
    state: ImageState,
    ratio: Float?,
    settings: ReaderSettings,
    vertical: Boolean,
    onRetry: () -> Unit,
    onSuccess: (Int, Int) -> Unit,
    onError: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var attempt by remember(page.key) { mutableStateOf(0) }
    var zoomed by remember(page.key) { mutableStateOf(false) }
    var panX by remember(page.key) { mutableStateOf(0f) }
    var panY by remember(page.key) { mutableStateOf(0f) }
    val contentScale = when (settings.imageFit) {
        ImageFit.FIT_WIDTH -> ContentScale.FillWidth
        ImageFit.FIT_HEIGHT -> ContentScale.FillHeight
        ImageFit.FIT_SCREEN -> ContentScale.Fit
        ImageFit.ORIGINAL -> ContentScale.None
    }.let { fit ->
        if (!vertical && fit == ContentScale.FillWidth) ContentScale.Fit else fit
    }
    // The request carries the page's own identity, so a recycled slot or a late response can
    // never place one chapter's image where another page belongs.
    val request = remember(page.key, attempt) {
        readerImageRequest(context, page)
    }

    BoxWithConstraints(
        modifier = modifier
            .then(if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .padding(horizontal = if (vertical) settings.marginsDp.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Reserve the real image height as soon as the ratio is known so scrolling never jumps.
        val reservedModifier = if (vertical) {
            val effectiveRatio = (ratio ?: 1.45f).coerceIn(0.2f, 8f)
            Modifier
                .fillMaxWidth()
                .height((maxWidth.value / effectiveRatio).dp)
        } else {
            Modifier.fillMaxSize()
        }

        Box(
            modifier = reservedModifier
                .clipToBounds()
                .background(Color.White.copy(alpha = 0.03f)),
            contentAlignment = Alignment.Center,
        ) {
            if (state is ImageState.Loading) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.42f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White.copy(alpha = 0.9f),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }
            }
            if (state is ImageState.Error) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(Spacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.reader_image_failed),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    TextButton(
                        onClick = {
                            attempt++
                            onRetry()
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.sm,
                            vertical = Spacing.xxs,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Spacing.xxs))
                        Text(
                            text = stringResource(R.string.state_error_retry),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = contentScale,
                colorFilter = readerColorFilter(settings),
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer {
                        scaleX = if (zoomed) 2f else 1f
                        scaleY = if (zoomed) 2f else 1f
                        translationX = panX
                        translationY = panY
                    }
                    .pointerInput(settings.zoomEnabled, zoomed) {
                        if (!settings.zoomEnabled) return@pointerInput
                        detectTapGestures(
                            onTap = { onTap() },
                            onDoubleTap = {
                                zoomed = !zoomed
                                if (!zoomed) {
                                    panX = 0f
                                    panY = 0f
                                }
                            },
                        )
                    }
                    .pointerInput(zoomed) {
                        if (!zoomed) return@pointerInput
                        detectTransformGestures { _, pan, _, _ ->
                            panX += pan.x
                            panY += pan.y
                        }
                    },
                onSuccess = { result ->
                    val drawable = result.result.drawable
                    onSuccess(drawable.intrinsicWidth, drawable.intrinsicHeight)
                },
                onError = { onError() },
            )
        }
    }
}

/**
 * One request definition shared by display and prefetch. The memory key carries the chapter id,
 * the page index and the URL, so two chapters that reuse the same URL or a recycled list slot
 * can never show each other's image.
 */
private fun readerImageRequest(context: android.content.Context, page: ReaderPage): ImageRequest {
    val headers = Headers.Builder().apply {
        page.headers.forEach { (name, value) -> add(name, value) }
    }.build()
    return ImageRequest.Builder(context)
        .data(page.data(context))
        .headers(headers)
        .memoryCacheKey("reader:${page.key}")
        .diskCacheKey(page.url)
        .crossfade(false)
        .build()
}

/** Coil needs a File for downloaded pages and the URL string for remote pages. */
private fun ReaderPage.data(context: android.content.Context): Any =
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) {
        url
    } else {
        java.io.File(url)
    }

/** Saturation, contrast, brightness and invert share one colour matrix. */
private fun readerColorFilter(settings: ReaderSettings): ColorFilter? {
    val unchanged = !settings.grayscale &&
        settings.saturation == 1f &&
        settings.contrast == 1f &&
        settings.brightness == 1f &&
        !settings.invertColors
    if (unchanged) return null

    val saturation = if (settings.grayscale) 0f else settings.saturation
    val lr = 0.213f
    val lg = 0.715f
    val lb = 0.072f
    val sr = lr * (1 - saturation)
    val sg = lg * (1 - saturation)
    val sb = lb * (1 - saturation)
    val contrast = settings.contrast
    val offsetBase = (1 - contrast) * 127.5f + (settings.brightness - 1f) * 255f
    val factor = if (settings.invertColors) -contrast else contrast
    val offset = if (settings.invertColors) 255f - offsetBase else offsetBase
    val matrix = floatArrayOf(
        (sr + saturation) * factor, sg * factor, sb * factor, 0f, offset,
        sr * factor, (sg + saturation) * factor, sb * factor, 0f, offset,
        sr * factor, sg * factor, (sb + saturation) * factor, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

private fun bgColor(settings: ReaderSettings): Color = when (settings.backgroundColor) {
    com.mangaos.app.domain.model.ReaderBackground.BLACK -> Color.Black
    com.mangaos.app.domain.model.ReaderBackground.WHITE -> Color.White
    com.mangaos.app.domain.model.ReaderBackground.GRAY -> Color.DarkGray
    com.mangaos.app.domain.model.ReaderBackground.CUSTOM -> Color.Black
}
