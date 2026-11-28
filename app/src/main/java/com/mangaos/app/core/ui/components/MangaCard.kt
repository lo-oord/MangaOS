package com.mangaos.app.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.MangaGlass
import com.mangaos.app.core.designsystem.glassTokens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.core.util.appString
import com.mangaos.app.core.util.displayGenres
import com.mangaos.app.core.util.ratingLabel
import com.mangaos.app.core.util.relativeTimeCompact
import com.mangaos.app.sources.registry.NativeSourceRegistry
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.MangaViewMode

/*
 * The manga card system.
 *
 * Every screen that lists works draws the same card through [MangaCard] / [MangaListRow] and the
 * same scroll containers through [MangaCollection]; no screen builds its own card. A surface only
 * chooses a [MangaCardContext], which decides whether the newest-chapter facts are present — never
 * the shape, spacing, type or colours of the card.
 */

/** Every size the card uses, so no screen can introduce a near-miss value of its own. */
object MangaCardMetrics {
    /** One cover proportion for grid, list and detail thumbnails; covers are cropped, never squashed. */
    val CoverAspect = Dimens.coverAspect

    /** Title lines reserved in every card, so a short name never produces a shorter card. */
    const val TitleLines = 2

    val corner = 16.dp
    val innerCorner = 12.dp
    val contentPadding = Spacing.sm
    val titleMetaGap = 5.dp
    val chipGap = 4.dp
    val chipHeight = 20.dp
    val chipHorizontalPadding = 5.dp
    val factIconSize = 11.dp
    val listCoverWidth = 66.dp
    val listGap = Spacing.md
    val gridMinWidth = 96.dp
    val gridGap = Spacing.gridGap
    val gridVerticalGap = Spacing.sm
    val badgePadding = 6.dp
    val listBadgePadding = 5.dp
    val pressScale = 0.974f
}

/**
 * What a card is being shown for. The layout and every measurement stay identical; the context
 * only decides whether the newest-chapter number and its publication date belong on this surface.
 */
enum class MangaCardContext {
    /** The "Latest" feed: newest chapter badge on the cover plus the real publication time. */
    LATEST,

    /** Every other surface (catalogue, search, library, downloads): cover, title, facts. */
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
    // The card uses the app-wide glass tokens, so its translucency and border are the same as a
    // dialog's or the top bar's in every theme.
    val scheme = MaterialTheme.colorScheme
    val glass = glassTokens()
    val interaction = remember { MutableInteractionSource() }
    val pressScale by cardPressScale(interaction)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(MangaCardMetrics.corner))
            .then(clickModifier(onClick, onLongClick, interaction)),
        shape = RoundedCornerShape(MangaCardMetrics.corner),
        color = glass.background,
        border = BorderStroke(MangaGlass.borderWidth, glass.border),
        shadowElevation = glass.shadow,
    ) {
        Column {
            MangaCardCover(
                manga = manga,
                context = context,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(MangaCardMetrics.CoverAspect),
            )
            CardInfoSurface(sheen = glass.highlight) {
                MangaCardInfo(
                    title = manga.title,
                    facts = cardFacts(manga, context),
                    scheme = scheme,
                )
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
    val glass = glassTokens()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MangaCardMetrics.corner))
            .then(clickModifier(onClick, onLongClick)),
        shape = RoundedCornerShape(MangaCardMetrics.corner),
        color = glass.background,
        border = BorderStroke(MangaGlass.borderWidth, glass.border),
        shadowElevation = glass.shadow,
    ) {
        Row(modifier = Modifier.padding(MangaCardMetrics.contentPadding)) {
            MangaCardCover(
                manga = manga,
                context = context,
                badgeAlignment = Alignment.TopEnd,
                badgePadding = MangaCardMetrics.listBadgePadding,
                modifier = Modifier
                    .width(MangaCardMetrics.listCoverWidth)
                    .aspectRatio(MangaCardMetrics.CoverAspect),
            )

            Spacer(Modifier.width(MangaCardMetrics.listGap))

            CardInfoSurface(sheen = glass.highlight, modifier = Modifier.weight(1f)) {
                // In a list there is room to set the publication date beside the name, which is
                // exactly where the Latest feed needs it; every other surface never computes it.
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = manga.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        minLines = MangaCardMetrics.TitleLines,
                        maxLines = MangaCardMetrics.TitleLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    latestDateLabel(manga, context)?.let { date ->
                        Spacer(Modifier.width(Spacing.xs))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 1.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(MangaCardMetrics.factIconSize),
                            )
                            Spacer(Modifier.width(Spacing.xxs))
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 96.dp),
                            )
                        }
                    }
                }
                CardFactRow(facts = cardFacts(manga, context))
            }
        }
    }
}

/* --------------------------------------------------------------------------------------- */
/* Shared card pieces                                                                      */
/* --------------------------------------------------------------------------------------- */

/** The rounded cover with a chapter badge for the Latest feed; identical in both layouts. */
@Composable
private fun MangaCardCover(
    manga: Manga,
    context: MangaCardContext,
    modifier: Modifier = Modifier,
    badgeAlignment: Alignment = Alignment.TopEnd,
    badgePadding: Dp = MangaCardMetrics.badgePadding,
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = MangaCardMetrics.corner,
                    topEnd = MangaCardMetrics.corner,
                    bottomStart = MangaCardMetrics.innerCorner,
                    bottomEnd = MangaCardMetrics.innerCorner,
                ),
            )
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        MangaCoverArt(manga = manga, modifier = Modifier.fillMaxSize())

        // Latest only, and only with a real number: no placeholder ever holds this slot.
        if (context == MangaCardContext.LATEST) {
            manga.latestChapter?.takeIf { it.isNotBlank() }?.let { chapter ->
                ChapterBadge(
                    text = appString(R.string.chapter_number, compactChapter(chapter)),
                    modifier = Modifier
                        .align(badgeAlignment)
                        .padding(badgePadding),
                )
            }
        }
    }
}

/** The glass information panel under the cover, with a single subtle sheen. */
@Composable
private fun CardInfoSurface(
    sheen: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(sheen.copy(alpha = 0.35f), Color.Transparent),
                ),
            )
            .padding(
                start = MangaCardMetrics.contentPadding,
                end = MangaCardMetrics.contentPadding,
                top = MangaCardMetrics.contentPadding,
                bottom = MangaCardMetrics.contentPadding,
            ),
    ) {
        content()
    }
}

/** Title on top, one fact row below it; the gap between them is a single shared token. */
@Composable
private fun MangaCardInfo(
    title: String,
    facts: List<CardFact>,
    scheme: androidx.compose.material3.ColorScheme,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurface,
        minLines = MangaCardMetrics.TitleLines,
        maxLines = MangaCardMetrics.TitleLines,
        overflow = TextOverflow.Ellipsis,
    )
    if (facts.isNotEmpty()) {
        Spacer(Modifier.height(MangaCardMetrics.titleMetaGap))
        CardFactRow(facts = facts)
    }
}

/**
 * One fact row that can never overlap itself: at most two facts, each measured with an equal share
 * of the width and ellipsised inside that share, so a long genre cannot push the status away.
 */
@Composable
private fun CardFactRow(facts: List<CardFact>) {
    if (facts.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MangaCardMetrics.chipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        facts.forEach { fact ->
            CardPill(fact = fact, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

/**
 * The facts a card may show, in one fixed order: publication date (Latest only), rating, status,
 * genre. Anything the source did not provide is simply absent, and the row is capped at two facts
 * so the card keeps one line of secondary information.
 */
@Composable
private fun cardFacts(manga: Manga, context: MangaCardContext): List<CardFact> {
    val facts = mutableListOf<CardFact>()
    // In the Latest feed the date is the fact that surface exists for, and the newest chapter is
    // already on the cover: the date therefore takes the whole fact line instead of competing
    // with a rating chip for half of a narrow card.
    latestDateLabel(manga, context)?.let { date -> return listOf(CardFact.Updated(date)) }
    ratingLabel(manga.rating)?.let { facts += CardFact.Rating(it) }
    if (manga.status != MangaStatus.UNKNOWN) facts += CardFact.Status(manga.status)
    displayGenres(manga.genres, 1).firstOrNull()?.let { facts += CardFact.Genre(it) }
    // A work with neither a rating, a state nor a genre still fills the row with its server's
    // current label, so the card keeps one line of real information instead of a gap. The label
    // comes from the registry, never from the name stored with the entry: a library item saved
    // under a server this build no longer ships shows nothing rather than an old brand name.
    if (facts.isEmpty()) {
        NativeSourceRegistry.definition(manga.sourceId)?.name?.let { facts += CardFact.Source(it) }
    }
    return facts.take(MaxCardFacts)
}

/** Only the Latest feed has a publication date to show, and only when the source sent one. */
@Composable
private fun latestDateLabel(manga: Manga, context: MangaCardContext): String? {
    if (context != MangaCardContext.LATEST) return null
    val timestamp = manga.updatedAt ?: return null
    val context0 = LocalContext.current
    return remember(timestamp, context0) { relativeTimeCompact(context0, timestamp) }
}

private const val MaxCardFacts = 2

/** A fact as data, so the chip row decides presentation and never the calling screen. */
private sealed interface CardFact {
    data class Rating(val label: String) : CardFact
    data class Status(val status: MangaStatus) : CardFact
    data class Genre(val label: String) : CardFact
    data class Updated(val label: String) : CardFact
    data class Source(val label: String) : CardFact
}

/**
 * The cover badge has one narrow line: a source that sends a release key or a long label (for
 * example `12@2024-05-01`) is reduced to its chapter number, which is the fact the badge means.
 */
private fun compactChapter(raw: String): String =
    Regex("""\d+(?:\.\d+)?""").find(raw)?.value ?: raw.take(8)

/** One resolved look for a pill, so every fact is drawn with the same measurements. */
private data class PillStyle(
    val container: Color,
    val content: Color,
    val icon: ImageVector?,
    val label: String,
)

@Composable
private fun CardPill(fact: CardFact, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // A chip carries an icon only where it replaces a word the reader needs no explanation for
    // (a star for a score, a calendar for a date). Status and genre are text-only: at card width
    // the icon took the space the label needed and the word was ellipsised.
    val style = when (fact) {
        is CardFact.Rating -> PillStyle(
            container = scheme.surfaceContainerHighest.copy(alpha = 0.9f),
            content = scheme.onSurface,
            icon = Icons.Filled.Star,
            label = fact.label,
        )
        is CardFact.Status -> {
            val color = mangaStatusColor(fact.status)
            PillStyle(
                container = color.copy(alpha = 0.16f),
                content = color,
                icon = null,
                label = appString(mangaStatusLabel(fact.status)),
            )
        }
        is CardFact.Genre -> PillStyle(
            container = scheme.surfaceContainerHighest.copy(alpha = 0.9f),
            content = scheme.onSurfaceVariant,
            icon = null,
            label = fact.label,
        )
        is CardFact.Updated -> PillStyle(
            container = scheme.primary.copy(alpha = 0.10f),
            content = scheme.primary,
            icon = Icons.Outlined.CalendarMonth,
            label = fact.label,
        )
        is CardFact.Source -> PillStyle(
            container = scheme.surfaceContainerHighest.copy(alpha = 0.9f),
            content = scheme.onSurfaceVariant,
            icon = null,
            label = fact.label,
        )
    }
    Surface(
        modifier = modifier.height(MangaCardMetrics.chipHeight),
        shape = RoundedCornerShape(50),
        color = style.container,
        contentColor = style.content,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MangaCardMetrics.chipHorizontalPadding,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            style.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = style.content,
                    modifier = Modifier.size(MangaCardMetrics.factIconSize),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                color = style.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A tiny press scale is the whole acknowledgement: no neighbours move, the grid never reflows. */
@Composable
private fun cardPressScale(interaction: MutableInteractionSource): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) MangaCardMetrics.pressScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "cardPress",
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun clickModifier(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    interaction: MutableInteractionSource? = null,
): Modifier = if (onLongClick != null) {
    if (interaction != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }
} else if (interaction != null) {
    Modifier.clickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        onClick = onClick,
    )
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
    val context = LocalContext.current
    val model = remember(coverUrl) {
        coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(180)
                .build()
        }
    }
    SubcomposeAsyncImage(
        model = model,
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

/**
 * The newest chapter number over the artwork. It is deliberately light: a translucent themed
 * scrim, small type and a hairline border, so the cover stays the subject of the card.
 */
@Composable
fun ChapterBadge(text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = scheme.surface.copy(alpha = 0.82f),
        contentColor = scheme.primary,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.22f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/* --------------------------------------------------------------------------------------- */
/* Containers                                                                              */
/* --------------------------------------------------------------------------------------- */

@Composable
fun MangaGrid(
    mangas: List<Manga>,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    onMangaLongClick: ((Manga) -> Unit)? = null,
    context: MangaCardContext = MangaCardContext.CATALOG,
    footer: (@Composable () -> Unit)? = null,
    caption: (@Composable (Manga) -> Unit)? = null,
) {
    LazyVerticalGrid(
        // Adaptive columns keep the same card proportion on phones and tablets instead of
        // stretching three enormous cards across a wide screen.
        columns = GridCells.Adaptive(minSize = MangaCardMetrics.gridMinWidth),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(MangaCardMetrics.gridVerticalGap),
        horizontalArrangement = Arrangement.spacedBy(MangaCardMetrics.gridGap),
    ) {
        items(items = mangas, key = { it.key }) { manga ->
            Column {
                MangaCard(
                    manga = manga,
                    onClick = { onMangaClick(manga) },
                    onLongClick = onMangaLongClick?.let { callback -> { callback(manga) } },
                    context = context,
                )
                caption?.let { slot ->
                    Spacer(Modifier.height(Spacing.xxs))
                    slot(manga)
                }
            }
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
    caption: (@Composable (Manga) -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.xs,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = mangas, key = { it.key }) { manga ->
            Column {
                MangaListRow(
                    manga = manga,
                    onClick = { onMangaClick(manga) },
                    onLongClick = onMangaLongClick?.let { callback -> { callback(manga) } },
                    context = context,
                )
                caption?.let { slot ->
                    Spacer(Modifier.height(Spacing.xxs))
                    slot(manga)
                }
            }
        }
        if (footer != null) {
            item { footer() }
        }
    }
}

/**
 * The one place that switches a list of works between the two layouts.
 *
 * Both layouts read the same list, keep their own scroll state and cross-fade into each other, and
 * the work the reader was looking at is scrolled back into view in the new layout. Nothing is
 * re-fetched, re-ordered or re-selected by the switch.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MangaCollection(
    mangas: List<Manga>,
    viewMode: MangaViewMode,
    onMangaClick: (Manga) -> Unit,
    modifier: Modifier = Modifier,
    context: MangaCardContext = MangaCardContext.CATALOG,
    onMangaLongClick: ((Manga) -> Unit)? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState(),
    footer: (@Composable () -> Unit)? = null,
    caption: (@Composable (Manga) -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    var previousMode by remember { mutableStateOf(viewMode) }

    LaunchedEffect(viewMode) {
        if (previousMode == viewMode) return@LaunchedEffect
        val anchor = if (previousMode == MangaViewMode.GRID) {
            gridState.firstVisibleItemIndex
        } else {
            listState.firstVisibleItemIndex
        }
        previousMode = viewMode
        if (anchor > 0) {
            // The new layout is measured on the next frame; only then can it be positioned.
            withFrameNanos { }
            if (viewMode == MangaViewMode.GRID) {
                gridState.scrollToItem(anchor)
            } else {
                listState.scrollToItem(anchor)
            }
        }
    }

    AnimatedContent(
        targetState = viewMode,
        transitionSpec = {
            fadeIn(tween(if (reduceMotion) 0 else 180)) togetherWith
                fadeOut(tween(if (reduceMotion) 0 else 120))
        },
        label = "mangaCollectionMode",
    ) { mode ->
        if (mode == MangaViewMode.GRID) {
            MangaGrid(
                mangas = mangas,
                onMangaClick = onMangaClick,
                modifier = modifier,
                gridState = gridState,
                onMangaLongClick = onMangaLongClick,
                context = context,
                footer = footer,
                caption = caption,
            )
        } else {
            MangaList(
                mangas = mangas,
                onMangaClick = onMangaClick,
                modifier = modifier,
                listState = listState,
                onMangaLongClick = onMangaLongClick,
                context = context,
                footer = footer,
                caption = caption,
            )
        }
    }
}
