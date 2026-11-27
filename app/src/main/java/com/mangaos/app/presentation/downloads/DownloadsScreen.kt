package com.mangaos.app.presentation.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.core.util.formatPercent
import com.mangaos.app.data.download.LocalDownloadRepository
import com.mangaos.app.data.download.StorageStatus
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadChapter
import com.mangaos.app.domain.model.DownloadChapterState
import com.mangaos.app.domain.model.DownloadState

@Composable
fun DownloadsScreen(
    onOpenWork: (sourceId: String, mangaId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The storage row reports the real folder and its real free space, and never decides that
    // a download will fail: that check belongs to the download manager, with its real error.
    val viewModel: DownloadsViewModel = viewModel()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var pendingDeleteFiles by remember { mutableStateOf<Download?>(null) }
    var pendingRemoveRecord by remember { mutableStateOf<Download?>(null) }
    var pendingDeleteChapter by remember { mutableStateOf<Pair<Download, DownloadChapter>?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        FilterRow(
            selected = viewModel.filter,
            onSelect = viewModel::selectFilter,
        )

        val visible = viewModel.visible(downloads)
        when {
            downloads.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Download,
                title = stringResource(R.string.downloads_empty_title),
            )
            visible.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Download,
                title = stringResource(R.string.downloads_no_matching),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items = visible, key = { it.id }) { task ->
                    DownloadCard(
                        task = task,
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onCancel = { viewModel.cancel(task.id) },
                        onRetry = { viewModel.retry(task.id) },
                        onOpenWork = { onOpenWork(task.manga.sourceId, task.manga.id) },
                        onRemoveRecord = { pendingRemoveRecord = task },
                        onDeleteFiles = { pendingDeleteFiles = task },
                        onDeleteChapter = { chapter ->
                            pendingDeleteChapter = task to chapter
                        },
                    )
                }
            }
        }
    }

    pendingRemoveRecord?.let { task ->
        AppDialog(
            title = stringResource(R.string.downloads_remove_title),
            message = stringResource(R.string.downloads_remove_message),
            confirmLabel = stringResource(R.string.downloads_remove_record),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                viewModel.removeRecord(task.id)
                pendingRemoveRecord = null
            },
            onDismiss = { pendingRemoveRecord = null },
        )
    }

    pendingDeleteFiles?.let { task ->
        AppDialog(
            title = stringResource(R.string.downloads_delete_files_title),
            message = stringResource(R.string.downloads_delete_files_message, task.mangaTitle),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteLocal(task.id)
                pendingDeleteFiles = null
            },
            onDismiss = { pendingDeleteFiles = null },
        )
    }

    pendingDeleteChapter?.let { (task, chapter) ->
        AppDialog(
            title = stringResource(R.string.downloads_delete_chapter_title),
            message = appString(R.string.chapter_number, chapter.number),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteChapter(task.manga.key, chapter.chapterId)
                pendingDeleteChapter = null
            },
            onDismiss = { pendingDeleteChapter = null },
        )
    }
}

@Composable
private fun FilterRow(
    selected: DownloadFilter,
    onSelect: (DownloadFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DownloadFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label())) },
                leadingIcon = if (filter == selected) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun DownloadCard(
    task: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenWork: () -> Unit,
    onRemoveRecord: () -> Unit,
    onDeleteFiles: () -> Unit,
    onDeleteChapter: (DownloadChapter) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var expanded by remember(task.id) { mutableStateOf(false) }
    val hasLocalFiles = task.chapters.any { it.state == DownloadChapterState.COMPLETED }
    val active = task.state == DownloadState.QUEUED ||
        task.state == DownloadState.DOWNLOADING ||
        task.state == DownloadState.PAUSED

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(
                    title = task.mangaTitle,
                    coverUrl = task.coverModel,
                    modifier = Modifier
                        .width(46.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.mangaTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (task.totalChapters == 1) {
                            stringResource(
                                R.string.downloads_task_chapter,
                                task.chapters.firstOrNull()?.number.orEmpty(),
                            )
                        } else {
                            appString(R.string.downloads_task_chapters, task.totalChapters)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                StatusChip(state = task.state)
                Box {
                    AppIconButton(
                        icon = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.detail_more),
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (hasLocalFiles) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_open_work)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onOpenWork()
                                },
                            )
                        }
                        if (!active) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_remove_record)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onRemoveRecord()
                                },
                            )
                        }
                        if (hasLocalFiles) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.downloads_delete_files)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onDeleteFiles()
                                },
                            )
                        }
                    }
                }
            }

            if (task.hasPendingChapters || hasLocalFiles) {
                Spacer(Modifier.height(Spacing.sm))
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = if (task.state == DownloadState.COMPLETED) OfflineGreen
                    else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = buildString {
                            append(formatPercent(task.progress))
                            if (task.totalChapters > 1) {
                                append(" · ")
                                append(
                                    stringResource(
                                        R.string.downloads_chapter_progress,
                                        task.completedChapters,
                                        task.totalChapters,
                                    ),
                                )
                            }
                            if (task.downloadedBytes > 0L) {
                                append(" · ")
                                append(formatBytes(task.downloadedBytes))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (task.totalChapters > 1) {
                        AppIconButton(
                            icon = if (expanded) Icons.Outlined.ExpandLess
                            else Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.downloads_chapters_header),
                            onClick = { expanded = !expanded },
                        )
                    }
                }
            }

            task.error?.let { message ->
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (task.state) {
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> {
                        ActionButton(Icons.Outlined.Pause, R.string.action_pause, onPause)
                        ActionButton(Icons.Outlined.Close, R.string.action_cancel, onCancel)
                    }
                    DownloadState.PAUSED -> {
                        ActionButton(Icons.Outlined.PlayArrow, R.string.action_resume, onResume)
                        ActionButton(Icons.Outlined.Close, R.string.action_cancel, onCancel)
                    }
                    DownloadState.FAILED, DownloadState.PARTIAL -> {
                        ActionButton(Icons.Outlined.Refresh, R.string.action_retry, onRetry)
                        ActionButton(Icons.Outlined.Close, R.string.action_cancel, onCancel)
                    }
                    DownloadState.CANCELLED -> {
                        ActionButton(Icons.Outlined.Refresh, R.string.action_retry, onRetry)
                        AppIconButton(
                            icon = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.downloads_remove_record),
                            onClick = onRemoveRecord,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadState.COMPLETED -> {
                        if (hasLocalFiles) {
                            AppIconButton(
                                icon = Icons.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.downloads_open_work),
                                onClick = onOpenWork,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(Spacing.xs))
                    task.chapters.forEach { chapter ->
                        ChapterStatusRow(chapter = chapter, onDelete = { onDeleteChapter(chapter) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterStatusRow(chapter: DownloadChapter, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = chapterIcon(chapter.state),
            contentDescription = null,
            tint = chapterTint(chapter.state),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = appString(R.string.chapter_number, chapter.number),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = chapterStateLabel(chapter),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (chapter.state == DownloadChapterState.COMPLETED) {
            AppIconButton(
                icon = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.chapter_action_delete_local),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun chapterStateLabel(chapter: DownloadChapter): String = when (chapter.state) {
    DownloadChapterState.COMPLETED -> formatBytes(chapter.bytes)
    DownloadChapterState.DOWNLOADING -> if (chapter.pageCount > 0) {
        "${formatPercent(chapter.progress)} · ${chapter.downloadedPages}/${chapter.pageCount}"
    } else {
        stringResource(R.string.download_status_downloading)
    }
    DownloadChapterState.QUEUED -> stringResource(R.string.download_status_queued)
    DownloadChapterState.PAUSED -> stringResource(R.string.download_status_paused)
    DownloadChapterState.FAILED -> stringResource(R.string.download_status_failed)
    DownloadChapterState.CANCELLED -> stringResource(R.string.download_status_cancelled)
}

private fun chapterIcon(state: DownloadChapterState) = when (state) {
    DownloadChapterState.COMPLETED -> Icons.Outlined.Done
    DownloadChapterState.DOWNLOADING -> Icons.Outlined.Download
    DownloadChapterState.QUEUED -> Icons.Outlined.Schedule
    DownloadChapterState.PAUSED -> Icons.Outlined.Pause
    DownloadChapterState.FAILED -> Icons.Outlined.Close
    DownloadChapterState.CANCELLED -> Icons.Outlined.Close
}

@Composable
private fun chapterTint(state: DownloadChapterState): Color = when (state) {
    DownloadChapterState.COMPLETED -> OfflineGreen
    DownloadChapterState.DOWNLOADING -> MaterialTheme.colorScheme.primary
    DownloadChapterState.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    AppIconButton(
        icon = icon,
        contentDescription = stringResource(labelRes),
        onClick = onClick,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusChip(state: DownloadState) {
    val accent = when (state) {
        DownloadState.COMPLETED -> OfflineGreen
        DownloadState.FAILED, DownloadState.PARTIAL -> MaterialTheme.colorScheme.error
        DownloadState.DOWNLOADING, DownloadState.QUEUED, DownloadState.PAUSED ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
        modifier = Modifier.padding(end = Spacing.xxs),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == DownloadState.DOWNLOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.4.dp,
                    color = accent,
                )
                Spacer(Modifier.width(4.dp))
            }
            if (state == DownloadState.COMPLETED) {
                Icon(
                    imageVector = Icons.Outlined.Done,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = stringResource(stateLabel(state)),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

private fun stateLabel(state: DownloadState): Int = when (state) {
    DownloadState.QUEUED -> R.string.download_status_queued
    DownloadState.DOWNLOADING -> R.string.download_status_downloading
    DownloadState.PAUSED -> R.string.download_status_paused
    DownloadState.PARTIAL -> R.string.download_status_partial
    DownloadState.COMPLETED -> R.string.download_status_completed
    DownloadState.FAILED -> R.string.download_status_failed
    DownloadState.CANCELLED -> R.string.download_status_cancelled
}

private fun DownloadFilter.label(): Int = when (this) {
    DownloadFilter.ALL -> R.string.downloads_filter_all
    DownloadFilter.ACTIVE -> R.string.downloads_filter_active
    DownloadFilter.QUEUED -> R.string.downloads_filter_queued
    DownloadFilter.COMPLETED -> R.string.downloads_filter_completed
    DownloadFilter.FAILED -> R.string.downloads_filter_failed
}

private val OfflineGreen = Color(0xFF16A34A)
