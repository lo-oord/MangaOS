package com.mangaos.app.presentation.storage

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.StoredWork
import com.mangaos.app.domain.repository.readChapterKey
import kotlinx.coroutines.launch

private sealed interface LocalDeleteTarget {
    data class SingleChapter(val chapter: Chapter) : LocalDeleteTarget
    data object Work : LocalDeleteTarget
}

/**
 * Offline detail. Everything shown here comes from the local records and files; missing
 * metadata is labelled as unavailable instead of being invented, and only fully downloaded
 * chapters are listed.
 */
@Composable
fun LocalWorkDetailScreen(
    sourceId: String,
    mangaId: String,
    onBack: () -> Unit,
    onOpenChapter: (Chapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocalWorkDetailViewModel = viewModel()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val readChapters by com.mangaos.app.di.ServiceLocator.library.readChapters
        .collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<LocalDeleteTarget?>(null) }

    val key = "$sourceId/$mangaId"
    val work = works.firstOrNull { it.manga.key == key }

    if (work == null) {
        Column(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.SdStorage,
                    title = stringResource(R.string.local_detail_not_found),
                    actionLabel = stringResource(R.string.cd_back),
                    onAction = onBack,
                )
            }
        }
        return
    }

    val blocked = {
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.storage_delete_blocked)) }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            LocalDetailHeader(
                work = work,
                onBack = onBack,
                onDeleteWork = { pendingDelete = LocalDeleteTarget.Work },
            )
        }
        item {
            LocalDetailBody(work = work)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.local_detail_chapters, work.chapters.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatBytes(work.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (work.chapters.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.local_detail_empty_chapters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
        } else {
            items(items = work.chapters, key = { it.id }) { chapter ->
                LocalChapterRow(
                    chapter = chapter,
                    isRead = readChapterKey(work.manga.key, chapter.id) in readChapters,
                    onOpen = { onOpenChapter(chapter) },
                    onDelete = { pendingDelete = LocalDeleteTarget.SingleChapter(chapter) },
                )
            }
        }
        item { Spacer(Modifier.height(Spacing.xxl)) }
    }

    when (val target = pendingDelete) {
        is LocalDeleteTarget.SingleChapter -> AppDialog(
            title = stringResource(R.string.delete_chapter_title),
            message = stringResource(R.string.delete_chapter_message, target.chapter.number),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                val deleted = viewModel.deleteChapter(work.manga.key, target.chapter.id)
                pendingDelete = null
                if (deleted) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.storage_deleted))
                    }
                } else {
                    blocked()
                }
            },
            onDismiss = { pendingDelete = null },
        )
        LocalDeleteTarget.Work -> AppDialog(
            title = stringResource(R.string.delete_work_title),
            message = stringResource(R.string.delete_work_message, work.manga.title),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                val deleted = viewModel.deleteWork(work.manga.key)
                pendingDelete = null
                if (deleted) {
                    onBack()
                } else {
                    blocked()
                }
            },
            onDismiss = { pendingDelete = null },
        )
        null -> Unit
    }
}

@Composable
private fun LocalDetailHeader(
    work: StoredWork,
    onBack: () -> Unit,
    onDeleteWork: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                icon = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.action_delete),
                onClick = onDeleteWork,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                CoverImage(
                    title = work.manga.title,
                    coverUrl = work.coverModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = work.manga.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                work.manga.altTitle?.takeIf { it.isNotBlank() }?.let { alt ->
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
                    text = stringResource(R.string.local_detail_size, formatBytes(work.sizeBytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalDetailBody(work: StoredWork) {
    val manga = work.manga
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            manga.rating?.let {
                LocalFact(stringResource(R.string.detail_rating, it))
            }
            if (manga.status != MangaStatus.UNKNOWN) {
                LocalFact(stringResource(localStatusLabel(manga.status)))
            }
            LocalFact(
                manga.sourceName?.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.local_detail_source, it) }
                    ?: stringResource(R.string.local_detail_source, manga.sourceId),
            )
        }

        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.detail_story),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = manga.description?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.local_detail_missing_info),
            style = MaterialTheme.typography.bodyMedium,
            color = if (manga.description.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        if (manga.genres.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                manga.genres.forEach { genre ->
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
    }
}

@Composable
private fun LocalFact(text: String) {
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

@Composable
private fun LocalChapterRow(
    chapter: Chapter,
    isRead: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(stringResource(R.string.chapter_number, chapter.number))
                    chapter.title?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatBytes(chapter.sizeBytes ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(
            imageVector = if (isRead) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = stringResource(R.string.detail_read),
            tint = if (isRead) ReadGreen
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
        AppIconButton(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.chapter_action_delete_local),
            onClick = onDelete,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun localStatusLabel(status: MangaStatus): Int = when (status) {
    MangaStatus.ONGOING -> R.string.status_ongoing
    MangaStatus.COMPLETED -> R.string.status_completed
    MangaStatus.HIATUS -> R.string.status_hiatus
    MangaStatus.CANCELLED -> R.string.status_cancelled
    MangaStatus.UNKNOWN -> R.string.status_unknown
}

private val ReadGreen = Color(0xFF16A34A)
