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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.mangaos.app.domain.model.ChapterDownloadStatus
import com.mangaos.app.domain.model.ChapterSort
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.ReadingProgress
import java.text.DateFormat
import java.util.Date
import java.util.Locale
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
    var sortMenuOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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
                isSaved = isSaved,
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
                MangaFacts(manga)
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = stringResource(
                        if (isSaved) R.string.action_remove_from_library else R.string.action_add_to_library,
                    ),
                    icon = if (isSaved) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
                    onClick = { viewModel.toggleLibrary() },
                    height = 48.dp,
                )
                if (progress != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = stringResource(R.string.detail_last_read, progress.chapterNumber),
                        style = MaterialTheme.typography.labelMedium,
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
                sort = viewModel.sort,
                selectionMode = selectionMode,
                sortMenuOpen = sortMenuOpen,
                onSortMenuOpenChange = { sortMenuOpen = it },
                onSortSelected = {
                    viewModel.changeSort(it)
                    sortMenuOpen = false
                },
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
                                chosen.forEach { ServiceLocator.downloads.enqueue(manga, it.number) }
                                selectionMode = false
                                selectedIds = emptySet()
                                scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.detail_download_queued))
                                }
                            },
                            onDelete = {
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
                    ChapterRow(
                        chapter = chapter,
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
                                viewModel.markOpened(chapter)
                                onOpenChapter(chapter)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(R.string.detail_marked_read, chapter.number),
                                    )
                                }
                            }
                        },
                        onLongClick = {
                            selectionMode = true
                            selectedIds = selectedIds + chapter.id
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
    isSaved: Boolean,
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
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = if (isSaved) {
                        stringResource(R.string.library_saved)
                    } else {
                        stringResource(R.string.library_not_saved)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MangaFacts(manga: Manga) {
    val authorLabel = stringResource(R.string.detail_author)
    val artistLabel = stringResource(R.string.detail_artist)
    val yearLabel = stringResource(R.string.detail_year)
    val languageLabel = stringResource(R.string.detail_language)
    val sourceLabel = stringResource(R.string.detail_source)
    val facts = buildList {
        manga.author?.let { add(authorLabel to it) }
        manga.artist?.takeIf { it != manga.author }?.let { add(artistLabel to it) }
        manga.year?.let { add(yearLabel to it.toString()) }
        manga.originalLanguage?.let { add(languageLabel to it) }
        manga.sourceName?.let { add(sourceLabel to it) }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (manga.status != MangaStatus.UNKNOWN) {
            FactChip(stringResource(statusLabel(manga.status)))
        }
        manga.rating?.let { FactChip(stringResource(R.string.detail_rating, it)) }
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
    sort: ChapterSort,
    selectionMode: Boolean,
    sortMenuOpen: Boolean,
    onSortMenuOpenChange: (Boolean) -> Unit,
    onSortSelected: (ChapterSort) -> Unit,
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
        Box {
            AppIconButton(
                icon = Icons.Outlined.Sort,
                contentDescription = stringResource(R.string.detail_sort),
                onClick = { onSortMenuOpenChange(true) },
            )
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { onSortMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_sort_newest)) },
                    trailingIcon = {
                        if (sort == ChapterSort.NEWEST_FIRST) Icon(Icons.Outlined.Done, contentDescription = null)
                    },
                    onClick = { onSortSelected(ChapterSort.NEWEST_FIRST) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_sort_oldest)) },
                    trailingIcon = {
                        if (sort == ChapterSort.OLDEST_FIRST) Icon(Icons.Outlined.Done, contentDescription = null)
                    },
                    onClick = { onSortSelected(ChapterSort.OLDEST_FIRST) },
                )
            }
        }
        TextButton(onClick = onToggleSelection, enabled = count > 0) {
            Text(
                text = stringResource(
                    if (selectionMode) R.string.action_cancel else R.string.action_select,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(Spacing.xs))
        }
        val chapterLabel = stringResource(R.string.chapter_number, chapter.number)
        val downloadedLabel = stringResource(R.string.detail_downloaded)
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
                chapter.sizeBytes?.let { add(com.mangaos.app.core.util.formatBytes(it)) }
                if (chapter.downloadStatus == ChapterDownloadStatus.DOWNLOADED) {
                    add(downloadedLabel)
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
        if (chapter.isRead) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.detail_read),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
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
