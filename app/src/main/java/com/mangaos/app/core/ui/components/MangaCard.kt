package com.mangaos.app.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.BrandColors
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.relativeTime
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import java.util.Locale

/** The single grid card used by every catalogue screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showLatestChapter: Boolean = false,
    showUpdatedAt: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .then(clickModifier(onClick, onLongClick)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            MangaCoverArt(manga = manga, modifier = Modifier.fillMaxSize())
            if (showLatestChapter && manga.latestChapter != null) {
                ChapterBadge(
                    text = stringResource(R.string.chapter_short, manga.latestChapter),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(34.dp),
        )

        Spacer(Modifier.height(Spacing.xxs))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (manga.status != MangaStatus.UNKNOWN) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(statusColor(manga.status)),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = stringResource(statusLabel(manga.status)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            RatingBadge(manga.rating)
        }

        if (showUpdatedAt && manga.updatedAt != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = relativeTime(LocalContext.current, manga.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}

/** Horizontal list row: same information as the card, laid out for scanning. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaListRow(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showLatestChapter: Boolean = false,
    showUpdatedAt: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(clickModifier(onClick, onLongClick))
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(LIST_COVER_WIDTH)
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            MangaCoverArt(manga = manga, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(Spacing.xs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (manga.status != MangaStatus.UNKNOWN) {
                    Text(
                        text = stringResource(statusLabel(manga.status)),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(manga.status),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                }
                RatingBadge(manga.rating)
            }

            if (manga.genres.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = manga.genres.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showLatestChapter && manga.latestChapter != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.chapter_number, manga.latestChapter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }

            val meta = buildList {
                manga.sourceName?.takeIf { it.isNotBlank() }?.let(::add)
                if (showUpdatedAt && manga.updatedAt != null) {
                    add(relativeTime(LocalContext.current, manga.updatedAt))
                }
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RatingBadge(rating: Double?) {
    if (rating == null) return
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = null,
        tint = BrandColors.Rating,
        modifier = Modifier.size(12.dp),
    )
    Spacer(Modifier.width(2.dp))
    Text(
        text = String.format(Locale.US, "%.1f", rating),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun clickModifier(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }

@Composable
fun MangaCoverArt(manga: Manga, modifier: Modifier = Modifier) {
    CoverImage(
        title = manga.title,
        coverUrl = manga.coverUrl,
        modifier = modifier,
    )
}

@Composable
fun CoverImage(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val contentDescription = stringResource(R.string.cd_cover, title)
    AsyncImage(
        model = coverUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
fun ChapterBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun MangaGrid(
    mangas: List<Manga>,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    onMangaLongClick: ((Manga) -> Unit)? = null,
    showLatestChapter: Boolean = false,
    showUpdatedAt: Boolean = false,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.gridGap),
    ) {
        items(items = mangas, key = { it.key }) { manga ->
            MangaCard(
                manga = manga,
                onClick = { onMangaClick(manga) },
                onLongClick = onMangaLongClick?.let { callback -> { callback(manga) } },
                showLatestChapter = showLatestChapter,
                showUpdatedAt = showUpdatedAt,
            )
        }
    }
}

@Composable
fun MangaList(
    mangas: List<Manga>,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onMangaLongClick: ((Manga) -> Unit)? = null,
    showLatestChapter: Boolean = false,
    showUpdatedAt: Boolean = false,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = mangas, key = { it.key }) { manga ->
            MangaListRow(
                manga = manga,
                onClick = { onMangaClick(manga) },
                onLongClick = onMangaLongClick?.let { callback -> { callback(manga) } },
                showLatestChapter = showLatestChapter,
                showUpdatedAt = showUpdatedAt,
            )
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

private fun statusColor(status: MangaStatus): Color = when (status) {
    MangaStatus.ONGOING -> BrandColors.Ongoing
    MangaStatus.COMPLETED -> BrandColors.Completed
    MangaStatus.HIATUS -> BrandColors.Hiatus
    MangaStatus.CANCELLED -> BrandColors.Cancelled
    MangaStatus.UNKNOWN -> BrandColors.Hiatus
}

private const val COVER_ASPECT = 2f / 3f
private val LIST_COVER_WIDTH = 74.dp
