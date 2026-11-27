package com.mangaos.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.MangaTheme
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.util.displayGenres
import com.mangaos.app.core.util.ratingLabel
import com.mangaos.app.core.util.relativeTime
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus

/**
 * What a card is being shown for. The layout is shared; only the Latest feed carries the newest
 * chapter's number over the cover and its publication date beside the title, and it does so by
 * changing the structure — never by hiding text in a reserved gap.
 */
enum class MangaCardContext {
    /** The "Latest" feed: newest chapter badge + real publication time. */
    LATEST,

    /** Every other surface: cover, title, rating, status and genre only. */
    CATALOG,
}

/** The single grid card used by every catalogue screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaCard(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    context: MangaCardContext = MangaCardContext.CATALOG,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .then(clickModifier(onClick, onLongClick)),
        shape = RoundedCornerShape(CardCorner),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.16f)),
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CoverAspect)
                    .clip(RoundedCornerShape(topStart = CardCorner, topEnd = CardCorner))
                    .background(scheme.surfaceContainerHigh),
            ) {
                MangaCoverArt(manga = manga, modifier = Modifier.fillMaxSize())

                // Latest only: the real newest chapter number, floating over the artwork.
                if (context == MangaCardContext.LATEST) {
                    manga.latestChapter?.takeIf { it.isNotBlank() }?.let { chapter ->
                        ChapterBadge(
                            text = appString(R.string.chapter_number, chapter),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.sm),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(
                    start = Spacing.sm,
                    end = Spacing.sm,
                    top = Spacing.sm,
                    bottom = Spacing.sm,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (context == MangaCardContext.LATEST) {
                        manga.updatedAt?.let { timestamp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.widthIn(max = 92.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = relativeTime(LocalContext.current, timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    RatingPill(manga.rating)
                    StatusPill(manga.status)
                    GenrePill(manga.genres)
                }
            }
        }
    }
}

/** Horizontal list row: the same information in the same order, laid out for scanning. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaListRow(
    manga: Manga,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    context: MangaCardContext = MangaCardContext.CATALOG,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .then(clickModifier(onClick, onLongClick)),
        shape = RoundedCornerShape(CardCorner),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.14f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.sm)) {
            Box(
                modifier = Modifier
                    .width(ListCoverWidth)
                    .aspectRatio(CoverAspect)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHigh),
            ) {
                MangaCoverArt(manga = manga, modifier = Modifier.fillMaxSize())
                if (context == MangaCardContext.LATEST) {
                    manga.latestChapter?.takeIf { it.isNotBlank() }?.let { chapter ->
                        ChapterBadge(
                            text = appString(R.string.chapter_number, chapter),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (context == MangaCardContext.LATEST) {
                    manga.updatedAt?.let { timestamp ->
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = relativeTime(LocalContext.current, timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    RatingPill(manga.rating)
                    StatusPill(manga.status)
                    GenrePill(manga.genres)
                }
            }
        }
    }
}

/** A real rating only; a source without one drops the whole pill. */
@Composable
private fun RatingPill(rating: Double?) {
    val label = ratingLabel(rating) ?: return
    Pill {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MangaTheme.tokens.rating,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(status: MangaStatus) {
    if (status == MangaStatus.UNKNOWN) return
    val color = mangaStatusColor(status)
    Pill(container = color.copy(alpha = 0.16f), content = color) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = appString(mangaStatusLabel(status)),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GenrePill(genres: List<String>) {
    val genre = displayGenres(genres, 1).firstOrNull() ?: return
    Pill {
        Icon(
            imageVector = Icons.Outlined.Category,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = genre,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Pill(
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    body: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = if (content == Color.Unspecified) MaterialTheme.colorScheme.onSurface else content,
        modifier = Modifier.height(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            content = {
                body()
                trailing?.invoke()
            },
        )
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

/**
 * Real cover or a quiet placeholder: a themed block while loading and a broken-image mark when
 * the source has no usable picture, so a failed image never looks like a broken layout.
 */
@Composable
fun CoverImage(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val description = appString(R.string.cd_cover, title)
    SubcomposeAsyncImage(
        model = coverUrl?.takeIf { it.isNotBlank() },
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        },
        success = { SubcomposeAsyncImageContent() },
    )
}

/** Small translucent badge over the artwork; it takes its colours from the active theme. */
@Composable
fun ChapterBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
    context: MangaCardContext = MangaCardContext.CATALOG,
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
                context = context,
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
    context: MangaCardContext = MangaCardContext.CATALOG,
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
                context = context,
            )
        }
        if (footer != null) {
            item { footer() }
        }
    }
}

private const val CoverAspect = 3f / 4f
private val CardCorner = 18.dp
private val ListCoverWidth = 74.dp
