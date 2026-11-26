package com.mangaos.app.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import android.view.WindowManager
import android.app.Activity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings

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

    var controlsVisible by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // auto hide
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && settings.autoHideControls) {
            kotlinx.coroutines.delay(settings.autoHideDelayMs)
            controlsVisible = false
        }
    }

    val context = LocalContext.current
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // continuous ensure next
    LaunchedEffect(listState.firstVisibleItemIndex, pages.size) {
        viewModel.ensureNextLoaded(listState.firstVisibleItemIndex)
    }

    Box(modifier = modifier.fillMaxSize().background(bgColor(settings))) {
        // content
        when (settings.readingMode) {
            ReaderMode.VERTICAL, ReaderMode.WEBTOON -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(pages, key = { _, p -> p.url }) { index, page ->
                        ReaderImage(
                            page = page,
                            state = imageStates[page.url] ?: ImageState.Loading,
                            settings = settings,
                            onRetry = { viewModel.retryImage(page.url) },
                            onSuccess = { viewModel.onImageSuccess(page.url) },
                            onError = { viewModel.onImageError(page.url) },
                        )
                        if (settings.readingMode == ReaderMode.WEBTOON) Spacer(Modifier.height(settings.imageSpacingDp.dp)) else Spacer(Modifier.height(settings.imageSpacingDp.dp))
                    }
                }
            }
            ReaderMode.HORIZONTAL_LTR, ReaderMode.HORIZONTAL_RTL, ReaderMode.SINGLE_PAGE, ReaderMode.DOUBLE_PAGE -> {
                LazyRow(
                    state = rememberLazyListState(),
                    reverseLayout = settings.readingMode == ReaderMode.HORIZONTAL_RTL,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(pages, key = { _, p -> p.url }) { _, page ->
                        ReaderImage(
                            page = page,
                            state = imageStates[page.url] ?: ImageState.Loading,
                            settings = settings,
                            onRetry = { viewModel.retryImage(page.url) },
                            onSuccess = { viewModel.onImageSuccess(page.url) },
                            onError = { viewModel.onImageError(page.url) },
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
            }

        }

        // tap to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { controlsVisible = !controlsVisible },
        )

        // top bar
        AnimatedVisibility(visible = controlsVisible && settings.showTopBar, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Color.White) }
                manga?.coverUrl?.let { url ->
                    coil.compose.AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = manga?.title ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(text = stringResource(R.string.chapter_number, chapters.getOrNull(viewModel.currentChapterIndex.value)?.number ?: ""), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color.White) }
            }
        }

        // bottom bar
        AnimatedVisibility(visible = controlsVisible && settings.showBottomBar, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(Spacing.sm),
            ) {
                val total = viewModel.totalCount()
                val loaded = viewModel.loadedCount()
                val percent = if (total > 0) (viewModel.loadedCount().toFloat() / total * 100).toInt() else 0
                if (settings.showProgressPercent) {
                    Text(text = "${percent}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                // reading progress slider
                val progress = if (pages.isNotEmpty()) {
                    if (false) {
                        0f
                    } else {
                        listState.firstVisibleItemIndex.toFloat() / (pages.size - 1).coerceAtLeast(1)
                    }
                } else 0f
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { v ->
                        val idx = (v * (pages.size - 1)).toInt().coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        scope.launch { listState.animateScrollToItem(idx) }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = {
                        val idx = (currentChapterIndex - 1).coerceAtLeast(0)
                        if (idx != currentChapterIndex) chapters.getOrNull(idx)?.let { viewModel.goToChapter(it) }
                    }, enabled = currentChapterIndex > 0) { Icon(Icons.Outlined.SkipPrevious, contentDescription = null, tint = Color.White) }
                    Text(
                        text = "$loaded/$total",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    IconButton(onClick = {
                        val idx = (currentChapterIndex + 1).coerceAtMost(chapters.size - 1)
                        if (idx != currentChapterIndex) chapters.getOrNull(idx)?.let { viewModel.goToChapter(it) }
                    }, enabled = currentChapterIndex < chapters.size - 1) { Icon(Icons.Outlined.SkipNext, contentDescription = null, tint = Color.White) }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun ReaderImage(
    page: ReaderPage,
    state: ImageState,
    settings: ReaderSettings,
    onRetry: () -> Unit,
    onSuccess: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contentScale = when (settings.imageFit) {
        ImageFit.FIT_WIDTH -> ContentScale.FillWidth
        ImageFit.FIT_HEIGHT -> ContentScale.FillHeight
        ImageFit.FIT_SCREEN -> ContentScale.Fit
        ImageFit.ORIGINAL -> ContentScale.None
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = settings.marginsDp.dp, vertical = (settings.imageSpacingDp / 2).dp),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ImageState.Loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            ImageState.Error -> Text(text = "Retry", color = Color.White, modifier = Modifier.clickable { onRetry() })
            ImageState.Success -> {}
        }
                    val colorFilter = if (settings.grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
            AsyncImage(
                model = ImageRequest.Builder(context).data(page.url).crossfade(true).build(),
                contentDescription = null,
                contentScale = contentScale,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth(),
                onSuccess = { onSuccess() },
                onError = { onError() },
            )
    }
}

private fun bgColor(s: ReaderSettings): Color = when (s.backgroundColor) {
    com.mangaos.app.domain.model.ReaderBackground.BLACK -> Color.Black
    com.mangaos.app.domain.model.ReaderBackground.WHITE -> Color.White
    com.mangaos.app.domain.model.ReaderBackground.GRAY -> Color.DarkGray
    com.mangaos.app.domain.model.ReaderBackground.CUSTOM -> Color.Black
}