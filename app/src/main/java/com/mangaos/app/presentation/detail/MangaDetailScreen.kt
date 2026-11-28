package com.mangaos.app.presentation.detail

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.designsystem.glassPanelBorder
import com.mangaos.app.core.designsystem.glassPanelFill
import com.mangaos.app.core.designsystem.goldBorderStroke
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.core.ui.components.ShimmerBlock
import com.mangaos.app.core.ui.components.mangaStatusColor
import com.mangaos.app.core.ui.components.mangaStatusLabel
import com.mangaos.app.core.util.displayGenres
import com.mangaos.app.core.util.ratingLabel
import com.mangaos.app.core.util.relativeTime
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaDetailSkeleton
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterOrder
import com.mangaos.app.domain.model.ChapterReadingState
import com.mangaos.app.domain.model.ChapterSort
import com.mangaos.app.domain.model.chapterReadingState
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadChapter
import com.mangaos.app.domain.model.DownloadChapterState
import com.mangaos.app.domain.model.findChapter
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.ReadingProgress
import com.mangaos.app.domain.repository.readChapterKey
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun MangaDetailScreen(
    sourceId: String,
    mangaId: String,
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenChapter: (Chapter) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: MangaDetailViewModel = viewModel()
    LaunchedEffect(sourceId, mangaId) { viewModel.load(sourceId, mangaId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val readChapters by viewModel.readChapters.collectAsStateWithLifecycle()
    val downloads by ServiceLocator.downloads.downloads.collectAsStateWithLifecycle()

    when (val current = state) {
        DetailUiState.Loading -> DetailLoading(onBack = onBack, modifier = modifier)
        is DetailUiState.Error -> DetailFailure(
            failure = current.failure,
            onBack = onBack,
            onRetry = viewModel::retry,
            onOpenSources = onOpenSources,
            modifier = modifier,
        )
        is DetailUiState.Content -> DetailContent(
            state = current,
            isSaved = current.manga.key in savedIds,
            progress = progress[current.manga.key],
            readChapters = readChapters,
            downloads = downloads,
            viewModel = viewModel,
            onBack = onBack,
            onOpenChapter = onOpenChapter,
            modifier = modifier,
        )
    }
}
@Composable
private fun DetailContent(
    state: DetailUiState.Content,
    isSaved: Boolean,
    progress: ReadingProgress?,
    readChapters: Set<String>,
    downloads: List<Download>,
    viewModel: MangaDetailViewModel,
    onBack: () -> Unit,
    onOpenChapter: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manga = state.manga
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionChapter by remember { mutableStateOf<Chapter?>(null) }
    val resumeChapter = progress?.let { saved ->
        state.chapters.firstOrNull { it.id == saved.chapterId }
            ?: state.chapters.firstOrNull { it.number == saved.chapterNumber }
    }
    val firstChapter = remember(state.chapters) {
        ChapterOrder.ascending(state.chapters).firstOrNull()
    }
    val readingChapter = resumeChapter ?: firstChapter
    val downloadedChapterIds = remember(downloads) {
        downloads.flatMapTo(HashSet()) { task ->
            task.chapters
                .filter { it.state == DownloadChapterState.COMPLETED }
                .map { it.chapterId }
        }
    }
    val visibleChapters = state.chapters
    val readCount = remember(state.chapters, readChapters, manga.key) {
        state.chapters.count { readChapterKey(manga.key, it.id) in readChapters }
    }
    val downloadedCount = remember(state.chapters, downloadedChapterIds) {
        state.chapters.count { it.id in downloadedChapterIds }
    }

    fun share() {
        // The share sheet carries the work's title, never a source address.
        val text = manga.title
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.detail_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp),
        ) {
            item {
                DetailHero(
                    manga = manga,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onBack = onBack,
                    onShare = ::share,
                    onRefresh = { menuOpen = false; viewModel.retry() },
                )
            }

            item {
                DescriptionSection(
                    description = manga.description,
                    expanded = descriptionExpanded,
                    onToggle = { descriptionExpanded = !descriptionExpanded },
                )
            }

            item {
                ChapterSectionHeader(
                    count = state.chapters.size,
                    readCount = readCount,
                    downloadedCount = downloadedCount,
                    selectionMode = selectionMode,
                    selectedCount = selectedIds.size,
                    onToggleSort = viewModel::toggleSort,
                    onToggleSelection = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selectedIds = emptySet()
                    },
                )
            }

            when {
                state.chaptersLoading -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        repeat(5) {
                            ShimmerBlock(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(62.dp),
                                shape = RoundedCornerShape(16.dp),
                            )
                        }
                    }
                }
                state.chaptersFailure != null -> item {
                    ChapterFailure(
                        failure = state.chaptersFailure,
                        onRetry = viewModel::retry,
                    )
                }
                state.chapters.isEmpty() -> item {
                    Text(
                        text = stringResource(R.string.detail_chapters_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                    )
                }
                else -> {
                    if (visibleChapters.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.detail_chapters_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                            )
                        }
                    }

                    items(visibleChapters, key = { it.id }) { chapter ->
                        val pair = downloads.findChapter(manga.key, chapter.id)
                        ChapterRow(
                            chapter = chapter,
                            readingState = chapterReadingState(
                                isRead = readChapterKey(manga.key, chapter.id) in readChapters,
                                holdsReadingPosition = resumeChapter?.id == chapter.id,
                            ),
                            downloadChapter = pair?.second,
                            selectionMode = selectionMode,
                            selected = chapter.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (chapter.id in selectedIds) {
                                        selectedIds - chapter.id
                                    } else {
                                        selectedIds + chapter.id
                                    }
                                } else {
                                    onOpenChapter(chapter)
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) selectionMode = true
                                selectedIds = if (chapter.id in selectedIds) {
                                    selectedIds - chapter.id
                                } else {
                                    selectedIds + chapter.id
                                }
                            },
                            onMoreClick = { actionChapter = chapter },
                            onReadToggleClick = {
                                if (readChapterKey(manga.key, chapter.id) in readChapters) {
                                    ServiceLocator.library.markChapterUnread(manga.key, chapter.id)
                                } else {
                                    ServiceLocator.library.markChapterRead(manga.key, chapter.id)
                                }
                            },
                            onDownloadClick = {
                                val task = pair?.first
                                val downloadState = pair?.second?.state
                                when (downloadState) {
                                    DownloadChapterState.FAILED,
                                    DownloadChapterState.CANCELLED -> task?.let {
                                        ServiceLocator.downloads.retry(it.id)
                                    }
                                    DownloadChapterState.QUEUED,
                                    DownloadChapterState.DOWNLOADING -> task?.let {
                                        ServiceLocator.downloads.pause(it.id)
                                    }
                                    DownloadChapterState.PAUSED -> task?.let {
                                        ServiceLocator.downloads.resume(it.id)
                                    }
                                    DownloadChapterState.COMPLETED -> Unit
                                    null -> ServiceLocator.downloads.enqueue(manga, chapter)
                                }
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(Spacing.xl)) }
        }

        DetailActionBar(
            readingChapter = readingChapter,
            resuming = resumeChapter != null,
            progress = progress,
            isSaved = isSaved,
            onRead = { readingChapter?.let(onOpenChapter) },
            onLibrary = { viewModel.toggleLibrary() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = selectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = Spacing.md, end = Spacing.md),
        ) {
            val chosen = state.chapters.filter { it.id in selectedIds }
            val chosenStates = chosen.mapNotNull { chapter ->
                downloads.findChapter(manga.key, chapter.id)?.second?.state
            }
            SelectionActions(
                selectedCount = selectedIds.size,
                allSelected = visibleChapters.isNotEmpty() &&
                    visibleChapters.all { it.id in selectedIds },
                canDownload = chosen.any { chapter ->
                    downloads.findChapter(manga.key, chapter.id) == null
                },
                canRetry = chosenStates.any {
                    it == DownloadChapterState.FAILED ||
                        it == DownloadChapterState.CANCELLED
                },
                canCancel = chosenStates.any {
                    it == DownloadChapterState.QUEUED ||
                        it == DownloadChapterState.DOWNLOADING ||
                        it == DownloadChapterState.PAUSED
                },
                canDelete = chosen.any { it.id in downloadedChapterIds },
                onRetry = {
                    chosen.mapNotNull { downloads.findChapter(manga.key, it.id)?.first }
                        .distinctBy { it.id }
                        .forEach { ServiceLocator.downloads.retry(it.id) }
                },
                onCancel = {
                    chosen.mapNotNull { downloads.findChapter(manga.key, it.id)?.first }
                        .distinctBy { it.id }
                        .forEach { ServiceLocator.downloads.cancel(it.id) }
                },
                onMarkUnread = {
                    chosen.forEach { chapter ->
                        ServiceLocator.library.markChapterUnread(manga.key, chapter.id)
                    }
                    selectionMode = false
                    selectedIds = emptySet()
                },
                onSelectAll = {
                    val allChosen = visibleChapters.isNotEmpty() &&
                        visibleChapters.all { it.id in selectedIds }
                    selectedIds = if (allChosen) {
                        emptySet()
                    } else {
                        selectedIds + visibleChapters.map { it.id }
                    }
                },
                onDownload = {
                    ServiceLocator.downloads.enqueue(manga, chosen)
                    selectionMode = false
                    selectedIds = emptySet()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.detail_download_queued))
                    }
                },
                onDelete = {
                    chosen.forEach { chapter ->
                        ServiceLocator.downloads.deleteLocalChapter(manga.key, chapter.id)
                    }
                    selectionMode = false
                    selectedIds = emptySet()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.storage_deleted))
                    }
                },
                onMarkRead = {
                    chosen.forEach { chapter ->
                        ServiceLocator.library.markChapterRead(manga.key, chapter.id)
                    }
                    selectionMode = false
                    selectedIds = emptySet()
                },
                onClear = {
                    selectedIds = emptySet()
                    selectionMode = false
                },
            )
        }

    }

    actionChapter?.let { chapter ->
        val pair = downloads.findChapter(manga.key, chapter.id)
        ChapterActionsSheet(
            chapter = chapter,
            mangaKey = manga.key,
            isRead = readChapterKey(manga.key, chapter.id) in readChapters,
            task = pair?.first,
            downloadChapter = pair?.second,
            onDismiss = { actionChapter = null },
            onOpen = {
                actionChapter = null
                onOpenChapter(chapter)
            },
            onSelect = {
                actionChapter = null
                selectionMode = true
                selectedIds = selectedIds + chapter.id
            },
            onDownload = {
                actionChapter = null
                ServiceLocator.downloads.enqueue(manga, chapter)
            },
            onDeleteLocal = {
                actionChapter = null
                ServiceLocator.downloads.deleteLocalChapter(manga.key, chapter.id)
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.storage_deleted))
                }
            },
        )
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHero(
    manga: Manga,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth()) {
        // The artwork itself becomes the backdrop: blurred, dimmed and faded into the canvas so
        // the title stays legible in every theme without a hard colour behind it.
        AsyncImage(
            model = manga.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.75f,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .blur(26.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        0f to scheme.background.copy(alpha = 0.42f),
                        0.55f to scheme.background.copy(alpha = 0.80f),
                        1f to scheme.background,
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Spacing.xs, end = Spacing.xs, top = 0.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                AppIconButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.detail_share),
                    onClick = onShare,
                )
                Box {
                    AppIconButton(
                        icon = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.detail_more),
                        onClick = { onMenuOpenChange(true) },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_refresh)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                            onClick = onRefresh,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surfaceContainerHigh),
                ) {
                    CoverImage(
                        title = manga.title,
                        coverUrl = manga.coverUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Drawn after the artwork so the hairline is never covered by it.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(goldBorderStroke(1.5.dp), RoundedCornerShape(18.dp)),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    manga.altTitle?.takeIf { it.isNotBlank() }?.let { alt ->
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = alt,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        ratingLabel(manga.rating)?.let { label ->
                            HeroPill(container = scheme.surfaceContainerHighest.copy(alpha = 0.9f)) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = com.mangaos.app.core.designsystem.MangaTheme.tokens.rating,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (manga.status != MangaStatus.UNKNOWN) {
                            val statusColor = mangaStatusColor(manga.status)
                            HeroPill(
                                container = statusColor.copy(alpha = 0.18f),
                                content = statusColor,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(statusColor, CircleShape),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = appString(mangaStatusLabel(manga.status)),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            val tiles = buildList {
                manga.year?.let {
                    add(
                        DetailTileData(
                            icon = Icons.Outlined.CalendarMonth,
                            label = R.string.detail_publication_date,
                            value = it.toString(),
                        ),
                    )
                }
                manga.updatedAt?.let { timestamp ->
                    add(
                        DetailTileData(
                            icon = Icons.Outlined.Schedule,
                            label = R.string.detail_last_update,
                            value = relativeTime(LocalContext.current, timestamp),
                        ),
                    )
                }
            }
            if (tiles.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                // Facts wrap instead of being squeezed: a long date is never truncated to a
                // scrap of text, and each tile keeps enough width for its own label.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    tiles.forEach { tile ->
                        DetailInfoTile(tile = tile, modifier = Modifier.widthIn(min = 148.dp))
                    }
                }
            }

            val genres = displayGenres(manga.genres, 6)
            if (genres.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    genres.forEach { genre ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = scheme.primary.copy(alpha = 0.12f),
                            contentColor = scheme.primary,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                scheme.primary.copy(alpha = 0.22f),
                            ),
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun HeroPill(
    container: Color,
    content: Color = MaterialTheme.colorScheme.onSurface,
    body: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = body,
        )
    }
}

private data class DetailTileData(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: Int,
    val value: String,
)

/** One real fact, laid out as a quiet tile; unknown facts are never rendered. */
@Composable
private fun DetailInfoTile(tile: DetailTileData, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = glassPanelFill(),
        border = glassPanelBorder(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = stringResource(tile.label),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = tile.value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DescriptionSection(
    description: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        shape = RoundedCornerShape(20.dp),
        color = glassPanelFill(),
        border = glassPanelBorder(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_story),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.MenuBook,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(Spacing.xs))

            val text = description?.takeIf { it.isNotBlank() }
            if (text == null) {
                Text(
                    text = stringResource(R.string.detail_story_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                )
                if (text.length > 180) {
                    TextButton(
                        onClick = onToggle,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = Spacing.xxs,
                        ),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(
                                if (expanded) R.string.action_show_less else R.string.action_show_more,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** The single primary action, kept in reach while the chapter list scrolls under it. */
@Composable
private fun DetailActionBar(
    readingChapter: Chapter?,
    resuming: Boolean,
    progress: ReadingProgress?,
    isSaved: Boolean,
    onRead: () -> Unit,
    onLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surface.copy(alpha = 0.97f),
        shadowElevation = 14.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (progress != null) {
                    Text(
                        text = appString(R.string.detail_last_read, progress.chapterNumber),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                }
                Button(
                    onClick = onRead,
                    enabled = readingChapter != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = if (resuming && readingChapter != null) {
                            appString(R.string.detail_continue_from, readingChapter.number)
                        } else {
                            stringResource(R.string.detail_start_reading)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSaved) scheme.primaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (isSaved) scheme.onPrimaryContainer else scheme.onSurface,
                modifier = Modifier.size(52.dp),
            ) {
                IconButton(onClick = onLibrary, modifier = Modifier.size(52.dp)) {
                    Icon(
                        imageVector = if (isSaved) Icons.Outlined.BookmarkRemove
                        else Icons.Outlined.BookmarkAdd,
                        contentDescription = stringResource(
                            if (isSaved) R.string.action_remove_from_library
                            else R.string.action_add_to_library,
                        ),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/**
 * The first information tier: real rating, real status and the real chapter count, in that
 * order. Anything the source does not provide is simply absent from the row.
 */
/** Labelled rows keep author, artist, year and language aligned and never repeat each other. */
@Composable
private fun ChapterSectionHeader(
    count: Int,
    readCount: Int,
    downloadedCount: Int,
    selectionMode: Boolean,
    selectedCount: Int,
    onToggleSort: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.FormatListBulleted,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = stringResource(R.string.detail_story_chapters),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = scheme.primary.copy(alpha = 0.12f),
            contentColor = scheme.primary,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (selectionMode) {
            Text(
                text = appString(R.string.selected_count, selectedCount),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
            )
            TextButton(onClick = onToggleSelection) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSort),
                shape = CircleShape,
                color = scheme.surfaceContainerHigh,
                contentColor = scheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = stringResource(R.string.detail_sort),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionActions(
    selectedCount: Int,
    allSelected: Boolean,
    canDownload: Boolean,
    canRetry: Boolean,
    canCancel: Boolean,
    canDelete: Boolean,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        shape = RoundedCornerShape(12.dp),
        color = glassPanelFill(),
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allSelected, onCheckedChange = { onSelectAll() })
                Text(
                    text = stringResource(R.string.selected_count, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.action_clear_selection))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionAction(
                    icon = Icons.Outlined.Download,
                    labelRes = R.string.action_download_selected,
                    enabled = selectedCount > 0 && canDownload,
                    onClick = onDownload,
                )
                SelectionAction(
                    icon = Icons.Outlined.Refresh,
                    labelRes = R.string.action_retry_selected,
                    enabled = canRetry,
                    onClick = onRetry,
                )
                SelectionAction(
                    icon = Icons.Outlined.Close,
                    labelRes = R.string.action_cancel_selected,
                    enabled = canCancel,
                    onClick = onCancel,
                )
                SelectionAction(
                    icon = Icons.Outlined.DeleteOutline,
                    labelRes = R.string.action_delete_selected,
                    enabled = canDelete,
                    onClick = onDelete,
                )
                SelectionAction(
                    icon = Icons.Outlined.CheckCircle,
                    labelRes = R.string.action_mark_selected_read,
                    enabled = selectedCount > 0,
                    onClick = onMarkRead,
                )
                SelectionAction(
                    icon = Icons.Outlined.VisibilityOff,
                    labelRes = R.string.action_mark_selected_unread,
                    enabled = selectedCount > 0,
                    onClick = onMarkUnread,
                )
            }
        }
    }
}

/** One action in the selection bar, as a round bubble with its icon only. */
@Composable
private fun SelectionAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) scheme.surfaceContainerHighest else scheme.surfaceContainerLow,
        contentColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.35f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(labelRes),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    readingState: ChapterReadingState,
    downloadChapter: DownloadChapter?,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onReadToggleClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val downloadState = downloadChapter?.state
    val percent = ((downloadChapter?.progress ?: 0f) * 100f).roundToInt()
    val read = readingState == ChapterReadingState.READ
    val inProgress = readingState == ChapterReadingState.IN_PROGRESS
    val isNew = chapter.publishedAt?.let { published ->
        !read && System.currentTimeMillis() - published in 0..NEW_CHAPTER_WINDOW_MS
    } == true
    val transition = rememberInfiniteTransition(label = "chapterNew")
    val badgeAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "chapterNewAlpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected && selectionMode) {
            scheme.primary.copy(alpha = 0.14f)
        } else {
            glassPanelFill()
        },
        border = if (selected && selectionMode) {
            androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = 0.5f))
        } else {
            glassPanelBorder()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(start = Spacing.sm, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(Spacing.xxs))
            }

            // The number appears exactly once: in its own round chip.
            Surface(
                shape = CircleShape,
                color = if (read) scheme.surfaceContainerHigh else scheme.primary.copy(alpha = 0.14f),
                contentColor = if (read) scheme.onSurfaceVariant else scheme.primary,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = chapter.number,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chapter.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.detail_chapter_word),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isNew) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = scheme.primary.copy(alpha = 0.16f * badgeAlpha + 0.06f),
                            contentColor = scheme.primary,
                        ) {
                            Text(
                                text = stringResource(R.string.detail_chapter_new),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                val status = when (downloadState) {
                    DownloadChapterState.DOWNLOADING -> "${percent}%"
                    DownloadChapterState.COMPLETED -> stringResource(R.string.detail_downloaded)
                    DownloadChapterState.FAILED -> stringResource(R.string.state_error_retry)
                    DownloadChapterState.PAUSED -> stringResource(R.string.download_status_paused)
                    DownloadChapterState.QUEUED -> stringResource(R.string.download_status_queued)
                    DownloadChapterState.CANCELLED -> stringResource(R.string.download_status_cancelled)
                    null -> if (inProgress) stringResource(R.string.detail_in_progress) else null
                }
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(Spacing.xxs))

            // Reading state lives on its own eye bubble, right next to the download one.
            ChapterBubble(
                onClick = onReadToggleClick,
                contentDescription = stringResource(
                    if (read) R.string.chapter_action_mark_unread
                    else R.string.chapter_action_mark_read,
                ),
                container = if (read) ReadGreen.copy(alpha = 0.16f) else scheme.surfaceContainerHigh,
                content = if (read) ReadGreen else scheme.onSurfaceVariant,
            ) {
                Icon(
                    imageVector = if (read) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
            }

            Spacer(Modifier.width(Spacing.xxs))

            ChapterBubble(
                onClick = onDownloadClick,
                contentDescription = stringResource(R.string.action_download_selected),
                container = when (downloadState) {
                    DownloadChapterState.COMPLETED -> ReadGreen.copy(alpha = 0.16f)
                    DownloadChapterState.FAILED, DownloadChapterState.CANCELLED ->
                        scheme.error.copy(alpha = 0.14f)
                    DownloadChapterState.DOWNLOADING, DownloadChapterState.QUEUED ->
                        scheme.primary.copy(alpha = 0.16f)
                    else -> scheme.surfaceContainerHigh
                },
                content = when (downloadState) {
                    DownloadChapterState.COMPLETED -> ReadGreen
                    DownloadChapterState.FAILED, DownloadChapterState.CANCELLED -> scheme.error
                    DownloadChapterState.DOWNLOADING, DownloadChapterState.QUEUED -> scheme.primary
                    else -> scheme.onSurface
                },
            ) {
                when (downloadState) {
                    DownloadChapterState.DOWNLOADING -> Text(
                        text = "$percent",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    DownloadChapterState.QUEUED -> Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    DownloadChapterState.PAUSED -> Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    DownloadChapterState.COMPLETED -> Icon(
                        Icons.Outlined.Done,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    DownloadChapterState.FAILED, DownloadChapterState.CANCELLED -> Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    null -> Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            IconButton(onClick = onMoreClick, modifier = Modifier.size(26.dp)) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** One round action: the same bubble shape for download, reading state and sort. */
@Composable
private fun ChapterBubble(
    onClick: () -> Unit,
    contentDescription: String,
    container: Color,
    content: Color,
    body: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Box(
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) { body() }
    }
}

@Composable
private fun ChapterActionsSheet(
    chapter: Chapter,
    mangaKey: String,
    isRead: Boolean,
    task: Download?,
    downloadChapter: DownloadChapter?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDeleteLocal: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Text(
            text = appString(R.string.chapter_number, chapter.number),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        when (downloadChapter?.state) {
            null -> {
                ChapterActionRow(Icons.Outlined.Download, R.string.chapter_action_download, onDownload)
                ChapterActionRow(Icons.Outlined.CheckCircle, R.string.chapter_action_select, onSelect)
            }
            DownloadChapterState.QUEUED, DownloadChapterState.DOWNLOADING -> {
                ChapterActionRow(Icons.Outlined.Pause, R.string.chapter_action_pause) {
                    task?.let { ServiceLocator.downloads.pause(it.id) }
                    onDismiss()
                }
                ChapterActionRow(Icons.Outlined.Close, R.string.chapter_action_cancel) {
                    task?.let { ServiceLocator.downloads.cancel(it.id) }
                    onDismiss()
                }
                ChapterActionRow(Icons.Outlined.CheckCircle, R.string.chapter_action_select, onSelect)
            }
            DownloadChapterState.PAUSED -> {
                ChapterActionRow(Icons.Outlined.PlayArrow, R.string.chapter_action_resume) {
                    task?.let { ServiceLocator.downloads.resume(it.id) }
                    onDismiss()
                }
                ChapterActionRow(Icons.Outlined.Close, R.string.chapter_action_cancel) {
                    task?.let { ServiceLocator.downloads.cancel(it.id) }
                    onDismiss()
                }
            }
            DownloadChapterState.FAILED, DownloadChapterState.CANCELLED -> {
                ChapterActionRow(Icons.Outlined.Refresh, R.string.chapter_action_retry) {
                    task?.let { ServiceLocator.downloads.retry(it.id) }
                    onDismiss()
                }
                ChapterActionRow(Icons.Outlined.CheckCircle, R.string.chapter_action_select, onSelect)
            }
            DownloadChapterState.COMPLETED -> {
                ChapterActionRow(Icons.Outlined.PlayArrow, R.string.chapter_action_open, onOpen)
                ChapterActionRow(Icons.Outlined.DeleteOutline, R.string.chapter_action_delete_local, onDeleteLocal)
            }
        }
        // Reading state is independent of the download state, so it is always available.
        ChapterActionRow(
            icon = if (isRead) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            labelRes = if (isRead) {
                R.string.chapter_action_mark_unread
            } else {
                R.string.chapter_action_mark_read
            },
        ) {
            if (isRead) {
                ServiceLocator.library.markChapterUnread(mangaKey, chapter.id)
            } else {
                ServiceLocator.library.markChapterRead(mangaKey, chapter.id)
            }
            onDismiss()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChapterFailure(failure: SourceFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (failure == SourceFailure.OFFLINE) R.string.state_offline else R.string.detail_chapters_error,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.state_error_retry), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DetailLoading(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
        }
        MangaDetailSkeleton(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailFailure(
    failure: SourceFailure,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            when (failure) {
                SourceFailure.OFFLINE, SourceFailure.NETWORK -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.state_offline),
                    actionLabel = stringResource(R.string.state_error_retry),
                    onAction = onRetry,
                )
                else -> EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(R.string.detail_source_unavailable),
                    actionLabel = stringResource(R.string.state_no_source_action),
                    onAction = onOpenSources,
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))

private val ReadGreen = Color(0xFF16A34A)

/** Single row height, used by every chapter state so the list keeps one rhythm. */
private val ChapterRowHeight = 60.dp

/** A chapter counts as new for three hours after it was published. */
private const val NEW_CHAPTER_WINDOW_MS = 3L * 60L * 60L * 1000L
