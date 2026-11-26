package com.mangaos.app.presentation.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.BrandColors
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.DownloadProgressIndicator
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.core.util.formatPercent
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadStatus

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val viewModel: DownloadsViewModel = viewModel()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    if (downloads.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Download,
            title = stringResource(R.string.downloads_empty_title),
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = downloads, key = { it.id }) { download ->
            DownloadRow(
                download = download,
                onPause = { viewModel.pause(download.id) },
                onResume = { viewModel.resume(download.id) },
                onRetry = { viewModel.retry(download.id) },
                onCancel = { viewModel.cancel(download.id) },
                onDelete = { viewModel.remove(download.id) },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: Download,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(
                    title = download.mangaTitle,
                    coverUrl = null,
                    modifier = Modifier
                        .width(42.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.mangaTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.chapter_number, download.chapterNumber),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                StatusChip(status = download.status)
            }

            if (download.status != DownloadStatus.COMPLETED || download.progress > 0f) {
                Spacer(Modifier.height(Spacing.md))
                DownloadProgressIndicator(progress = download.progress)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = buildString {
                        append(formatPercent(download.progress))
                        append(" · ")
                        append(formatBytes(download.downloadedBytes))
                        if (download.totalBytes > 0L) {
                            append(" / ")
                            append(formatBytes(download.totalBytes))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (download.status) {
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                        ActionButton(Icons.Outlined.Pause, R.string.action_pause, onPause)
                        ActionButton(Icons.Outlined.Close, R.string.action_cancel, onCancel)
                    }
                    DownloadStatus.PAUSED -> {
                        ActionButton(Icons.Outlined.PlayArrow, R.string.action_resume, onResume)
                        ActionButton(Icons.Outlined.Close, R.string.action_cancel, onCancel)
                    }
                    DownloadStatus.FAILED -> {
                        ActionButton(Icons.Outlined.Refresh, R.string.action_retry, onRetry)
                        ActionButton(Icons.Outlined.Close, R.string.action_cancel, onCancel)
                    }
                    DownloadStatus.CANCELLED -> {
                        ActionButton(Icons.Outlined.Refresh, R.string.action_retry, onRetry)
                        ActionButton(Icons.Outlined.DeleteOutline, R.string.action_delete, onDelete)
                    }
                    DownloadStatus.COMPLETED -> {
                        ActionButton(Icons.Outlined.DeleteOutline, R.string.action_delete, onDelete)
                    }
                    DownloadStatus.CANCELLING -> Unit
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    val label = stringResource(labelRes)
    AppIconButton(
        icon = icon,
        contentDescription = label,
        onClick = onClick,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatusChip(status: DownloadStatus) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = statusColor(status).copy(alpha = 0.12f),
        contentColor = statusColor(status),
    ) {
        Text(
            text = stringResource(statusLabel(status)),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

private fun statusLabel(status: DownloadStatus): Int = when (status) {
    DownloadStatus.QUEUED -> R.string.download_status_queued
    DownloadStatus.DOWNLOADING -> R.string.download_status_downloading
    DownloadStatus.PAUSED -> R.string.download_status_paused
    DownloadStatus.FAILED -> R.string.download_status_failed
    DownloadStatus.COMPLETED -> R.string.download_status_completed
    DownloadStatus.CANCELLING -> R.string.download_status_cancelling
    DownloadStatus.CANCELLED -> R.string.download_status_cancelled
}

@Composable
private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.COMPLETED -> BrandColors.Ongoing
    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
