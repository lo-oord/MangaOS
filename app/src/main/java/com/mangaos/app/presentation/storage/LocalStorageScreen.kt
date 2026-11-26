package com.mangaos.app.presentation.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.mangaos.app.domain.model.StoredWork
import kotlinx.coroutines.launch

private sealed interface DeleteTarget {
    data class SingleChapter(
        val mangaId: String,
        val chapterId: String,
        val label: String,
    ) : DeleteTarget

    data class Work(val mangaId: String, val title: String) : DeleteTarget

    data object Selected : DeleteTarget
}

@Composable
fun LocalStorageScreen(modifier: Modifier = Modifier) {
    val viewModel: LocalStorageViewModel = viewModel()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    val notifyDeleted = {
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.storage_deleted)) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (viewModel.selectedChapterIds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.selected_count, viewModel.selectedChapterIds.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    AppIconButton(
                        icon = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        onClick = { pendingDelete = DeleteTarget.Selected },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    AppIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        onClick = viewModel::clearSelection,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (works.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SdStorage,
                title = stringResource(R.string.storage_empty_title),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items = works, key = { it.manga.id }) { work ->
                    StoredWorkCard(
                        work = work,
                        expanded = work.manga.id in viewModel.expandedWorkIds,
                        isSelected = { chapter -> viewModel.isSelected(work.manga.id, chapter.id) },
                        onToggleExpanded = { viewModel.toggleExpanded(work.manga.id) },
                        onToggleChapter = { chapter -> viewModel.toggleSelected(work.manga.id, chapter.id) },
                        onDeleteChapter = { chapter ->
                            pendingDelete = DeleteTarget.SingleChapter(
                                mangaId = work.manga.id,
                                chapterId = chapter.id,
                                label = context.getString(R.string.chapter_number, chapter.number),
                            )
                        },
                        onDeleteWork = {
                            pendingDelete = DeleteTarget.Work(work.manga.id, work.manga.title)
                        },
                    )
                }
            }
        }
    }

    when (val target = pendingDelete) {
        is DeleteTarget.SingleChapter -> AppDialog(
            title = stringResource(R.string.delete_chapter_title),
            message = stringResource(R.string.delete_chapter_message, target.label),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteChapter(target.mangaId, target.chapterId)
                pendingDelete = null
                notifyDeleted()
            },
            onDismiss = { pendingDelete = null },
        )

        is DeleteTarget.Work -> AppDialog(
            title = stringResource(R.string.delete_work_title),
            message = stringResource(R.string.delete_work_message, target.title),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteWork(target.mangaId)
                pendingDelete = null
                notifyDeleted()
            },
            onDismiss = { pendingDelete = null },
        )

        DeleteTarget.Selected -> AppDialog(
            title = stringResource(R.string.delete_chapters_title),
            message = stringResource(R.string.selected_count, viewModel.selectedChapterIds.size),
            confirmLabel = stringResource(R.string.action_delete),
            dismissLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.deleteSelected()
                pendingDelete = null
                notifyDeleted()
            },
            onDismiss = { pendingDelete = null },
        )

        null -> Unit
    }
}

@Composable
private fun StoredWorkCard(
    work: StoredWork,
    expanded: Boolean,
    isSelected: (Chapter) -> Boolean,
    onToggleExpanded: () -> Unit,
    onToggleChapter: (Chapter) -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onDeleteWork: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    title = work.manga.title,
                    coverUrl = work.manga.coverUrl,
                    modifier = Modifier
                        .width(40.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = work.manga.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.storage_chapter_count, work.chapters.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = formatBytes(work.chapters.sumOf { it.sizeBytes ?: 0L }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
                AppIconButton(
                    icon = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = work.manga.title,
                    onClick = onToggleExpanded,
                )
                AppIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = onDeleteWork,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    work.chapters.forEach { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            selected = isSelected(chapter),
                            onToggle = { onToggleChapter(chapter) },
                            onDelete = { onDeleteChapter(chapter) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    selected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = Spacing.xs, end = Spacing.sm, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Text(
            text = stringResource(R.string.chapter_number, chapter.number),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatBytes(chapter.sizeBytes ?: 0L),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppIconButton(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.action_delete),
            onClick = onDelete,
        )
    }
}
