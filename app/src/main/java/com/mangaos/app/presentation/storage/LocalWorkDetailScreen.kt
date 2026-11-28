package com.mangaos.app.presentation.storage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.mangaStatusColor
import com.mangaos.app.core.ui.components.mangaStatusLabel
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.core.util.ratingLabel
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
 * Offline detail. It uses the same hero, pills, round chapter bubbles and bottom action as the
 * online detail screen; the only difference is that it lists the downloaded chapters only, and
 * everything comes from local records. The source name is never shown here.
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
    val readingChapter = work.chapters.firstOrNull()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 104.dp),
        ) {
            item {
                LocalDetailHero(
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
                        .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = appString(R.string.local_detail_chapters, work.chapters.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
            item { Spacer(Modifier.height(Spacing.xl)) }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = glassPanelFill(),
            border = glassPanelBorder(),
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
                Button(
                    onClick = { readingChapter?.let(onOpenChapter) },
                    enabled = readingChapter != null,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.detail_start_reading),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    IconButton(onClick = { pendingDelete = LocalDeleteTarget.Work }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
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
private fun LocalDetailHero(
    work: StoredWork,
    onBack: () -> Unit,
    onDeleteWork: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val manga = work.manga
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(scheme.background),
        )
        AsyncImage(
            model = work.coverModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .blur(26.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        0f to scheme.background.copy(alpha = 0.55f),
                        0.55f to scheme.background.copy(alpha = 0.86f),
                        1f to scheme.background,
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.xs),
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
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .width(124.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(scheme.surfaceContainerHigh),
                ) {
                    CoverImage(
                        title = manga.title,
                        coverUrl = work.coverModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // The same golden hairline the catalogue covers carry.
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
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = scheme.surfaceContainerHighest.copy(alpha = 0.9f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
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
                        }
                        if (manga.status != MangaStatus.UNKNOWN) {
                            val statusColor = mangaStatusColor(manga.status)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = statusColor.copy(alpha = 0.18f),
                                contentColor = statusColor,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
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
                    Spacer(Modifier.height(Spacing.xs))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.surfaceContainerHighest.copy(alpha = 0.9f),
                        contentColor = scheme.onSurfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SdStorage,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.local_detail_size, formatBytes(work.sizeBytes)),
                                style = MaterialTheme.typography.labelSmall,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalDetailBody(work: StoredWork) {
    val manga = work.manga
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = glassPanelFill(),
            border = glassPanelBorder(),
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = stringResource(R.string.detail_story),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = manga.description?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.local_detail_missing_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (manga.genres.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                manga.genres.forEach { genre ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
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
    }
}

/** One downloaded chapter: round number chip, eye bubble and delete bubble. */
@Composable
private fun LocalChapterRow(
    chapter: Chapter,
    isRead: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = glassPanelFill(),
        border = glassPanelBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(start = Spacing.sm, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (isRead) scheme.surfaceContainerHigh else scheme.primary.copy(alpha = 0.14f),
                contentColor = if (isRead) scheme.onSurfaceVariant else scheme.primary,
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
                Text(
                    text = chapter.title?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_chapter_word),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBytes(chapter.sizeBytes ?: 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = if (isRead) ReadGreen.copy(alpha = 0.16f) else scheme.surfaceContainerHigh,
                contentColor = if (isRead) ReadGreen else scheme.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isRead) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(R.string.detail_read),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.chapter_action_delete_local),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

private val ReadGreen = Color(0xFF16A34A)
