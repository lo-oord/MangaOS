package com.mangaos.app.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.BrandColors
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.relativeTime
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import java.util.Locale

/** The single card used by every catalogue screen, so all grids stay identical. */
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
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .then(clickModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Dimens.coverAspect)
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
            modifier = Modifier.height(Dimens.cardTitleHeight),
        )

        Spacer(Modifier.height(Spacing.xxs))

        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = BrandColors.Rating,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = String.format(Locale.US, "%.1f", manga.rating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        if (showUpdatedAt) {
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

@Composable
fun MangaCoverArt(manga: Manga, modifier: Modifier = Modifier) {
    CoverImage(
        title = manga.title,
        coverUrl = manga.coverUrl,
        coverRes = manga.localCoverRes,
        modifier = modifier,
    )
}

/** Shared cover rendering: bundled demo art now, cached network covers later. */
@Composable
fun CoverImage(
    title: String,
    coverUrl: String?,
    @androidx.annotation.DrawableRes coverRes: Int?,
    modifier: Modifier = Modifier,
) {
    val contentDescription = stringResource(R.string.cd_cover, title)
    if (coverRes != null) {
        Image(
            painter = painterResource(id = coverRes),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = coverUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
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
        items(items = mangas, key = { it.id }) { manga ->
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

private fun statusLabel(status: MangaStatus): Int = when (status) {
    MangaStatus.ONGOING -> R.string.status_ongoing
    MangaStatus.COMPLETED -> R.string.status_completed
    MangaStatus.HIATUS -> R.string.status_hiatus
    MangaStatus.CANCELLED -> R.string.status_cancelled
}

private fun statusColor(status: MangaStatus): Color = when (status) {
    MangaStatus.ONGOING -> BrandColors.Ongoing
    MangaStatus.COMPLETED -> BrandColors.Completed
    MangaStatus.HIATUS -> BrandColors.Hiatus
    MangaStatus.CANCELLED -> BrandColors.Cancelled
}
