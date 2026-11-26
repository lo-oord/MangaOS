package com.mangaos.app.presentation.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import com.mangaos.app.core.designsystem.Spacing
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

    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val imageStates by viewModel.imageStates.collectAsStateWithLifecycle()
    val manga by viewModel.manga.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val nextChapterLoading by viewModel.nextChapterLoading.collectAsStateWithLifecycle()
    val nextChapterError by viewModel.nextChapterError.collectAsStateWithLifecycle()
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
    var restored by remember(sourceId, mangaId, chapterId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

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

    LaunchedEffect(controlsVisible, settings.autoHideControls, settings.autoHideDelayMs) {
        if (controlsVisible && settings.autoHideControls) {
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

    LaunchedEffect(mode, pages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { visible ->
                delay(220)
                val pageIndex = if (mode == ReaderMode.DOUBLE_PAGE) visible * 2 else visible
                viewModel.onPageChanged(pageIndex)
                viewModel.ensureNextLoaded(pageIndex)
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

    // Prefetch the upcoming pages with the same headers Coil uses for display.
    LaunchedEffect(listState.firstVisibleItemIndex, pages.size, settings.preloadImages) {
        if (pages.isEmpty() || settings.preloadImages <= 0) return@LaunchedEffect
        val loader = context.imageLoader
        val start = listState.firstVisibleItemIndex + 1
        pages.drop(start).take(settings.preloadImages).forEach { page ->
            val headers = Headers.Builder().apply {
                page.headers.forEach { (name, value) -> add(name, value) }
            }.build()
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(page.url)
                    .headers(headers)
                    .build(),
            )
        }
    }

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
                    val rtl = false
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                        reverseLayout = rtl,
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
                        reverseLayout = false,
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
                    ) {
                        itemsIndexed(pages, key = { _, page -> page.key }) { _, page ->
                            ReaderImage(
                                page = page,
                                state = imageStates[page.key] ?: ImageState.Loading,
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
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                    )
                }
                manga?.coverUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manga?.title.orEmpty(),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.chapter_number,
                            chapters.getOrNull(currentChapterIndex)?.number.orEmpty(),
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible && settings.showBottomBar,
            enter = fadeIn(),
            exit = fadeOut(),
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
            val localProgress = if (chapterSize > 1 && chapterStart >= 0) {
                ((visiblePage - chapterStart).toFloat() / (chapterSize - 1)).coerceIn(0f, 1f)
            } else {
                0f
            }
            val pageInChapter = if (chapterStart >= 0 && chapterSize > 0) {
                (visiblePage - chapterStart + 1).coerceIn(1, chapterSize)
            } else {
                0
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(start = Spacing.xs, end = Spacing.xs, top = 2.dp, bottom = 2.dp),
            ) {
                Slider(
                    value = localProgress,
                    onValueChange = { value ->
                        if (chapterSize > 1) {
                            val pageIndex = chapterStart + (value * (chapterSize - 1)).toInt()
                            val itemIndex = if (mode == ReaderMode.DOUBLE_PAGE) pageIndex / 2 else pageIndex
                            scope.launch { listState.scrollToItem(itemIndex) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .padding(horizontal = Spacing.sm),
                    thumb = {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape),
                        )
                    },
                    track = { state ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(state.value)
                                    .height(2.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            val previous = chapters.getOrNull(currentChapterIndex - 1)
                                ?: return@IconButton
                            val appendedIndex = viewModel.pageIndexOfChapter(previous.id)
                            if (viewModel.settings.value.continuousReading && appendedIndex >= 0) {
                                scope.launch {
                                    listState.scrollToItem(
                                        if (mode == ReaderMode.DOUBLE_PAGE) appendedIndex / 2 else appendedIndex,
                                    )
                                }
                            } else {
                                viewModel.goToChapter(previous)
                            }
                        },
                        enabled = currentChapterIndex > 0,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipPrevious,
                            contentDescription = stringResource(R.string.reader_previous_chapter),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentChapter?.number?.let {
                                    stringResource(R.string.chapter_number, it)
                                }.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                            if (nextChapterLoading) {
                                Spacer(Modifier.width(Spacing.xs))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = Color.White,
                                    strokeWidth = 1.5.dp,
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$pageInChapter / $chapterSize",
                                color = Color.White.copy(alpha = 0.68f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (settings.showProgressPercent) {
                                Text(
                                    text = " · ${(readingFraction * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (nextChapterError) {
                                TextButton(
                                    onClick = { viewModel.retryNextChapter() },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.height(22.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Replay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        text = stringResource(R.string.reader_next_failed),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            val nextIndex = currentChapterIndex + 1
                            val next = chapters.getOrNull(nextIndex) ?: return@IconButton
                            val appendedIndex = viewModel.pageIndexOfChapter(next.id)
                            if (viewModel.settings.value.continuousReading && appendedIndex >= 0) {
                                scope.launch {
                                    listState.scrollToItem(
                                        if (mode == ReaderMode.DOUBLE_PAGE) appendedIndex / 2 else appendedIndex,
                                    )
                                }
                            } else {
                                viewModel.goToChapter(next)
                            }
                        },
                        enabled = currentChapterIndex < chapters.lastIndex,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipNext,
                            contentDescription = stringResource(R.string.reader_next_chapter),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { showQuickSettings = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.reader_settings),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (isLoading && pages.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!isLoading && pages.isEmpty() && error != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.reader_chapter_error),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.xs))
                TextButton(onClick = { viewModel.retry() }) {
                    Text(
                        text = stringResource(R.string.state_error_retry),
                        color = MaterialTheme.colorScheme.primary,
                    )
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
}


@Composable
private fun ReaderImage(
    page: ReaderPage,
    state: ImageState,
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
    val headers = remember(page.headers) {
        Headers.Builder().apply { page.headers.forEach { (name, value) -> add(name, value) } }.build()
    }
    val request = remember(page.url, attempt, headers) {
        ImageRequest.Builder(context)
            .data(
                if (page.url.startsWith("http://") || page.url.startsWith("https://") || page.url.startsWith("data:")) {
                    page.url
                } else {
                    java.io.File(page.url)
                },
            )
            .headers(headers)
            .crossfade(true)
            .build()
    }
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .padding(
                horizontal = if (vertical) settings.marginsDp.dp else 0.dp,
                vertical = if (vertical) 0.dp else 0.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (state is ImageState.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.dp,
            )
        }
        if (state is ImageState.Error) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable {
                        attempt++
                        onRetry()
                    }
                    .padding(Spacing.md),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Replay,
                    contentDescription = null,
                    tint = Color.White,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.state_error_retry),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = contentScale,
            colorFilter = readerColorFilter(settings),
            modifier = Modifier
                .then(if (vertical) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
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
