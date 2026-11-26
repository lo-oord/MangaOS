package com.mangaos.app.presentation.detail

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaDetailSkeleton
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ChapterSort
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadStatus
import com.mangaos.app.domain.model.ExtensionFailure
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
    val resumeChapter = progress?.let { saved ->
        state.chapters.firstOrNull { it.id == saved.chapterId }
            ?: state.chapters.firstOrNull { it.number == saved.chapterNumber }
    }
    val firstChapter = state.chapters.minWithOrNull(
        compareBy<Chapter> { it.number.toFloatOrNull() ?: Float.MAX_VALUE }
            .thenBy { it.publishedAt ?: Long.MAX_VALUE }
            .thenBy { it.id },
    ) ?: state.chapters.lastOrNull()
    val readingChapter = resumeChapter ?: firstChapter

    fun share() {
        val text = manga.url?.takeIf { it.isNotBlank() } ?: manga.title
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.detail_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            DetailHeader(
                manga = manga,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onBack = onBack,
                onShare = ::share,
                onRefresh = { menuOpen = false; viewModel.retry() },
                onOpenSourcePage = {
                    menuOpen = false
                    val url = manga.url
                    if (url != null) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                },
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen),
            ) {
                Spacer(Modifier.height(Spacing.md))
                MangaFacts(
                    manga = manga,
                    chapterCount = state.chapters
                        .takeIf { !state.chaptersLoading && state.chaptersFailure == null && it.isNotEmpty() }
                        ?.size,
                )
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.toggleLibrary() },
                        modifier = Modifier.height(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.md,
                        ),
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Outlined.BookmarkRemove
                            else Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(
                                if (isSaved) R.string.action_remove_from_library
                                else R.string.action_add_to_library,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Button(
                        onClick = { readingChapter?.let(onOpenChapter) },
                        enabled = readingChapter != null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReadGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(
                                if (resumeChapter != null) R.string.detail_continue_reading
                                else R.string.detail_start_reading,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
                if (progress != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.detail_last_read, progress.chapterNumber),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.lg)) {
                Text(
                    text = stringResource(R.string.detail_story),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                val description = manga.description?.takeIf { it.isNotBlank() }
                if (description == null) {
                    Text(
                        text = stringResource(R.string.detail_story_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                    )
                    if (description.length > 180) {
                        TextButton(
                            onClick = { descriptionExpanded = !descriptionExpanded },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 0.dp,
                                vertical = Spacing.xs,
                            ),
                        ) {
                            Text(
                                text = stringResource(
                                    if (descriptionExpanded) R.string.action_show_less else R.string.action_show_more,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (manga.genres.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    GenreChips(manga.genres)
                }
            }
        }

        item {
            ChapterSectionHeader(
                count = state.chapters.size,
                selectionMode = selectionMode,
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
                        com.mangaos.app.core.ui.components.ShimmerBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
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
                if (selectionMode) {
                    item {
                        SelectionActions(
                            selectedCount = selectedIds.size,
                            allSelected = selectedIds.size == state.chapters.size,
                            onSelectAll = {
                                selectedIds = if (selectedIds.size == state.chapters.size) {
                                    emptySet()
                                } else {
                                    state.chapters.mapTo(LinkedHashSet()) { it.id }
                                }
                            },
                            onDownload = {
                                val chosen = state.chapters.filter { it.id in selectedIds }
                                chosen.forEach { ServiceLocator.downloads.enqueue(manga, it) }
                                selectionMode = false
                                selectedIds = emptySet()
                                scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.detail_download_queued))
                                }
                            },
                            onDelete = {
                                downloads
                                    .filter { it.chapterId in selectedIds }
                                    .forEach { ServiceLocator.downloads.remove(it.id) }
                                ServiceLocator.localStorage.deleteChapters(manga.id, selectedIds)
                                selectionMode = false
                                selectedIds = emptySet()
                                scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.storage_deleted))
                                }
                            },
                        )
                    }
                }

                items(state.chapters, key = { it.id }) { chapter ->
                    val download = downloads.firstOrNull {
                        it.mangaId == manga.key && it.chapterId == chapter.id
                    }
                    ChapterRow(
                        chapter = chapter,
                        isRead = readChapterKey(manga.key, chapter.id) in readChapters,
                        download = download,
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
                            selectionMode = true
                            selectedIds = selectedIds + chapter.id
                        },
                        onDownloadClick = {
                            when (download?.status) {
                                DownloadStatus.FAILED,
                                DownloadStatus.PAUSED,
                                DownloadStatus.CANCELLED -> ServiceLocator.downloads.retry(download.id)
                                DownloadStatus.COMPLETED,
                                DownloadStatus.DOWNLOADING,
                                DownloadStatus.QUEUED,
                                DownloadStatus.CANCELLING,
                                -> Unit
                                null -> ServiceLocator.downloads.enqueue(manga, chapter)
                            }
                        },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Spacing.xxl)) }
    }
}

@Composable
private fun DetailHeader(
    manga: Manga,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSourcePage: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        AsyncImage(
            model = manga.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.55f,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.55f),
                        0.5f to background.copy(alpha = 0.82f),
                        1f to background,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
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
                    if (!manga.url.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.detail_open_source_page)) },
                            leadingIcon = { Icon(Icons.Outlined.OpenInNew, contentDescription = null) },
                            onClick = onOpenSourcePage,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                CoverImage(
                    title = manga.title,
                    coverUrl = manga.coverUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                manga.altTitle?.takeIf { it.isNotBlank() }?.let { alt ->
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = alt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaFacts(manga: Manga, chapterCount: Int?) {
    val authorLabel = stringResource(R.string.detail_author)
    val artistLabel = stringResource(R.string.detail_artist)
    val yearLabel = stringResource(R.string.detail_year)
    val languageLabel = stringResource(R.string.detail_language)
    val facts = buildList {
        manga.author?.let { add(authorLabel to it) }
        manga.artist?.takeIf { it != manga.author }?.let { add(artistLabel to it) }
        manga.year?.let { add(yearLabel to it.toString()) }
        manga.originalLanguage?.let { add(languageLabel to it) }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        manga.rating?.let { FactChip(stringResource(R.string.detail_rating, it)) }
        if (manga.status != MangaStatus.UNKNOWN) {
            FactChip(stringResource(statusLabel(manga.status)))
        }
        chapterCount?.let { FactChip(stringResource(R.string.detail_chapter_count, it)) }
        facts.forEach { (label, value) -> FactChip("$label: $value") }
    }
}

@Composable
private fun FactChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 5.dp),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreChips(genres: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        genres.forEach { genre ->
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ChapterSectionHeader(
    count: Int,
    selectionMode: Boolean,
    onToggleSort: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.detail_chapters, count),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selectionMode) {
            TextButton(onClick = onToggleSelection) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            AppIconButton(
                icon = Icons.Outlined.SwapVert,
                contentDescription = stringResource(R.string.detail_sort),
                onClick = onToggleSort,
            )
        }
    }
}

@Composable
private fun SelectionActions(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.screen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = allSelected, onCheckedChange = { onSelectAll() })
            Text(
                text = stringResource(if (selectedCount == 0) R.string.action_select_all else R.string.selected_count, selectedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = onDownload, enabled = selectedCount > 0) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.action_download_selected))
            }
            TextButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.action_delete_selected))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRow(
    chapter: Chapter,
    isRead: Boolean,
    download: Download?,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(Spacing.xs))
        }
        val chapterLabel = stringResource(R.string.chapter_number, chapter.number)
        val downloadedLabel = stringResource(R.string.detail_downloaded)
        val percent = download?.progress?.times(100f)?.roundToInt()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(chapterLabel)
                    chapter.title?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                chapter.publishedAt?.let { add(formatDate(it)) }
                when (download?.status) {
                    DownloadStatus.DOWNLOADING -> percent?.takeIf { it in 1..99 }?.let { add("$it%") }
                    DownloadStatus.COMPLETED -> add(downloadedLabel)
                    DownloadStatus.FAILED -> add(stringResource(R.string.state_error_retry))
                    DownloadStatus.PAUSED -> add(stringResource(R.string.download_status_paused))
                    DownloadStatus.QUEUED -> add(stringResource(R.string.download_status_queued))
                    else -> chapter.sizeBytes?.let { add(com.mangaos.app.core.util.formatBytes(it)) }
                }
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Icon(
            imageVector = if (isRead) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = stringResource(R.string.detail_read),
            tint = if (isRead) ReadGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.xxs))
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when (download?.status) {
                DownloadStatus.QUEUED -> Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.download_status_queued),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                DownloadStatus.DOWNLOADING -> CircularProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                DownloadStatus.COMPLETED -> Icon(
                    Icons.Outlined.Done,
                    contentDescription = stringResource(R.string.detail_downloaded),
                    tint = ReadGreen,
                    modifier = Modifier.size(19.dp),
                )
                DownloadStatus.FAILED -> IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.state_error_retry),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DownloadStatus.PAUSED, DownloadStatus.CANCELLED -> IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.action_resume),
                        modifier = Modifier.size(18.dp),
                    )
                }
                DownloadStatus.CANCELLING -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                null -> IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringResource(R.string.action_download_selected),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterFailure(failure: ExtensionFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (failure == ExtensionFailure.OFFLINE) R.string.state_offline else R.string.detail_chapters_error,
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
    failure: ExtensionFailure,
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
                ExtensionFailure.OFFLINE, ExtensionFailure.NETWORK -> EmptyState(
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

private fun statusLabel(status: MangaStatus): Int = when (status) {
    MangaStatus.ONGOING -> R.string.status_ongoing
    MangaStatus.COMPLETED -> R.string.status_completed
    MangaStatus.HIATUS -> R.string.status_hiatus
    MangaStatus.CANCELLED -> R.string.status_cancelled
    MangaStatus.UNKNOWN -> R.string.status_unknown
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timestamp))

private val ReadGreen = Color(0xFF16A34A)
