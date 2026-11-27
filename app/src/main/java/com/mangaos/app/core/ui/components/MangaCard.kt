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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.util.relativeTime
import com.mangaos.app.domain.model.Manga

/**
 * The one grid card used by every catalogue screen.
 *
 * Information order is fixed and identical on every surface: cover, title, rating + status,
 * genres, and — only when a screen asks for it — the newest chapter with its real publication
 * date. Rows keep a fixed height so a card without a rating or a genre never shifts its
 * neighbours, and every optional element simply disappears when the source has no value.
 */
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
                    text = appString(R.string.chapter_short, manga.latestChapter),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(modifier = Modifier.height(Dimens.cardTitleHeight)) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(2.dp))

        // Rating and status share one line; either one alone re-flows without leaving a hole.
        Box(modifier = Modifier.height(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RatingLabel(manga.rating)
                StatusBadge(manga.status)
            }
        }

        Spacer(Modifier.height(2.dp))

        Box(modifier = Modifier.height(18.dp)) {
            GenreTags(genres = manga.genres, modifier = Modifier.fillMaxWidth(), limit = 2)
        }

        if (showLatestChapter && manga.latestChapter != null) {
            Spacer(Modifier.height(3.dp))
            LatestChapterLine(
                chapter = manga.latestChapter,
                timestamp = manga.updatedAt.takeIf { showUpdatedAt },
            )
        }
    }
}

/** Horizontal list row: same information, same order, laid out for scanning. */
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RatingLabel(manga.rating)
                StatusBadge(manga.status)
            }

            GenreTags(
                genres = manga.genres,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                limit = 2,
            )

            if (showLatestChapter && manga.latestChapter != null) {
                Spacer(Modifier.height(Spacing.xs))
                LatestChapterLine(
                    chapter = manga.latestChapter,
                    timestamp = manga.updatedAt.takeIf { showUpdatedAt },
                )
            }
        }
    }
}

/**
 * "Ch. 42 · 3 days ago" — only for the Latest feed, and only for a real chapter. The age is the
 * chapter's own publication date; when the source does not provide one the row simply shows the
 * chapter number instead of a fabricated time.
 */
@Composable
private fun LatestChapterLine(chapter: String, timestamp: Long?) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = appString(R.string.chapter_short, chapter),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (timestamp != null) {
            Text(
                text = " · ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = relativeTime(context, timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    val contentDescription = appString(R.string.cd_cover, title)
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
    footer: (@Composable () -> Unit)? = null,
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
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
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
    footer: (@Composable () -> Unit)? = null,
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
        if (footer != null) {
            item { footer() }
        }
    }
}

private const val COVER_ASPECT = 2f / 3f
private val LIST_COVER_WIDTH = 74.dp
