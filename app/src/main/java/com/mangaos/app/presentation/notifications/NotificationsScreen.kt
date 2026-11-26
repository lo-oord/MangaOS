package com.mangaos.app.presentation.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.BrandColors
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.util.relativeTime
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.NotificationType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(modifier: Modifier = Modifier) {
    val viewModel: NotificationsViewModel = viewModel()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AppIconButton(
                icon = Icons.Outlined.DoneAll,
                contentDescription = stringResource(R.string.action_mark_all_read),
                onClick = viewModel::markAllRead,
                enabled = notifications.any { !it.isRead },
            )
            AppIconButton(
                icon = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.action_delete_all),
                onClick = { confirmDeleteAll = true },
                enabled = notifications.isNotEmpty(),
            )
        }

        if (notifications.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.NotificationsNone,
                title = stringResource(R.string.notifications_empty_title),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(items = notifications, key = { it.id }) { notification ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) {
                                viewModel.delete(notification.id)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteSweep,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .padding(end = Spacing.lg)
                                        .size(20.dp),
                                )
                            }
                        },
                    ) {
                        NotificationRow(
                            notification = notification,
                            onClick = { viewModel.markRead(notification.id) },
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AppDialog(
            title = stringResource(R.string.delete_all_notifications_title),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteAll()
                confirmDeleteAll = false
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val accent = notificationAccent(notification.type)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (notification.isRead) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = notificationIcon(notification.type),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (notification.isRead) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (notification.message.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = relativeTime(androidx.compose.ui.platform.LocalContext.current, notification.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            if (!notification.isRead) {
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private fun notificationIcon(type: NotificationType) = when (type) {
    NotificationType.DOWNLOAD_COMPLETED -> Icons.Outlined.DownloadDone
    NotificationType.DOWNLOAD_FAILED -> Icons.Outlined.ErrorOutline
    NotificationType.DOWNLOAD_STATUS -> Icons.Outlined.Downloading
    NotificationType.SYSTEM -> Icons.Outlined.Info
}

@Composable
private fun notificationAccent(type: NotificationType): Color = when (type) {
    NotificationType.DOWNLOAD_COMPLETED -> BrandColors.Ongoing
    NotificationType.DOWNLOAD_FAILED -> MaterialTheme.colorScheme.error
    NotificationType.DOWNLOAD_STATUS -> MaterialTheme.colorScheme.primary
    NotificationType.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
}
