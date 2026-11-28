package com.mangaos.app.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rocket
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangaos.app.core.ui.LocalReduceMotion

/**
 * One icon vocabulary for the whole app. Screens never pick a one-off Material glyph for a
 * concept that already has an entry here, and an icon is always drawn through [AppIcon] so it
 * takes its colour from the active theme instead of a hard-coded tint.
 */
object MangaOsIcons {

    // Work state
    val stateOngoing = Icons.Outlined.PlayArrow
    val stateCompleted = Icons.Outlined.CheckCircle
    val stateHiatus = Icons.Outlined.PauseCircleOutline
    val stateCancelled = Icons.Outlined.Cancel

    // Genres
    val genreAction = Icons.Outlined.LocalFireDepartment
    val genreAdventure = Icons.Outlined.Explore
    val genreRomance = Icons.Outlined.FavoriteBorder
    val genreFantasy = Icons.Outlined.AutoAwesome
    val genreHorror = Icons.Outlined.DarkMode
    val genreComedy = Icons.Outlined.SentimentSatisfied
    val genreDrama = Icons.Outlined.Theaters
    val genrePsychological = Icons.Outlined.Psychology
    val genreThriller = Icons.Outlined.Bolt
    val genreSports = Icons.Outlined.EmojiEvents
    val genreSchool = Icons.Outlined.School
    val genreHistorical = Icons.Outlined.AccountBalance
    val genreSciFi = Icons.Outlined.Rocket
    val genreMystery = Icons.Outlined.QuestionMark
    val genreSliceOfLife = Icons.Outlined.LocalCafe

    // Work information
    val rating = Icons.Outlined.Star
    val views = Icons.Outlined.Visibility
    val chapters = Icons.Outlined.MenuBook
    val updated = Icons.Outlined.CalendarMonth
    val author = Icons.Outlined.Person
    val palette = Icons.Outlined.Palette
    val language = Icons.Outlined.Language
    val globe = Icons.Outlined.Public

    // Library
    val library = Icons.Outlined.LibraryBooks
    val bookmark = Icons.Outlined.BookmarkBorder
    val favorite = Icons.Outlined.FavoriteBorder
    val history = Icons.Outlined.History

    // Chapters
    val chapterList = Icons.Outlined.FormatListBulleted
    val chapterRead = Icons.Outlined.CheckCircle

    // Downloads
    val download = Icons.Outlined.Download
    val cloudDownload = Icons.Outlined.CloudDownload
    val loading = Icons.Outlined.Refresh
    val pause = Icons.Outlined.Pause
    val play = Icons.Outlined.PlayArrow
    val refresh = Icons.Outlined.Refresh
    val success = Icons.Outlined.CheckCircle
    val error = Icons.Outlined.ErrorOutline

    // Sources and system
    val sources = Icons.Outlined.Extension
    val add = Icons.Outlined.AddCircleOutline
    val delete = Icons.Outlined.Delete
    val search = Icons.Outlined.Search
    val info = Icons.Outlined.Info
    val settings = Icons.Outlined.Settings
    val readerSettings = Icons.Outlined.Tune
    val sun = Icons.Outlined.LightMode
    val maximize = Icons.Outlined.Fullscreen
    val minimize = Icons.Outlined.FullscreenExit
    val eye = Icons.Outlined.Visibility
    val eyeOff = Icons.Outlined.VisibilityOff
    val previous = Icons.Outlined.ChevronLeft
    val next = Icons.Outlined.ChevronRight

    val notification = Icons.Outlined.Notifications
    val languages = Icons.Outlined.Language
    val moon = Icons.Outlined.DarkMode
    val lock = Icons.Outlined.Lock
    val unlock = Icons.Outlined.LockOpen
    val help = Icons.Outlined.HelpOutline
    val home = Icons.Outlined.Home
    val storage = Icons.Outlined.SdStorage

    val theme: ImageVector = Icons.Outlined.Palette
}

/**
 * Semantic colour roles for icons. A role is resolved against the active theme every time, so a
 * theme switch recolours every icon without touching a component.
 */
enum class IconRole {
    Primary,
    Secondary,
    Muted,
    Success,
    Warning,
    Error,
    Info,
    Disabled,
    OnSurface,
    OnPrimary,
}

@Composable
fun iconRoleColor(role: IconRole): Color {
    val scheme = MaterialTheme.colorScheme
    val tokens = MangaTheme.tokens
    return when (role) {
        IconRole.Primary -> scheme.primary
        IconRole.Secondary -> scheme.secondary
        IconRole.Muted -> scheme.onSurfaceVariant.copy(alpha = 0.72f)
        IconRole.Success -> tokens.success
        IconRole.Warning -> tokens.warning
        IconRole.Error -> scheme.error
        IconRole.Info -> scheme.tertiary
        IconRole.Disabled -> tokens.disabled
        IconRole.OnSurface -> scheme.onSurface
        IconRole.OnPrimary -> scheme.onPrimary
    }
}

/**
 * The only place an icon is drawn. [active] runs a short colour/scale transition for selection
 * states, so highlighting never redraws a whole screen and never animates when the system asks
 * for reduced motion.
 */
@Composable
fun AppIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    role: IconRole = IconRole.OnSurface,
    active: Boolean = false,
    activeRole: IconRole = IconRole.Primary,
    size: Dp = 20.dp,
) {
    val reduceMotion = LocalReduceMotion.current
    val target = iconRoleColor(if (active) activeRole else role)
    val tint by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 180),
        label = "iconTint",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale",
    )
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = if (reduceMotion) 1f else scale
                scaleY = if (reduceMotion) 1f else scale
            },
    )
}
