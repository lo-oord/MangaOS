package com.mangaos.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** One spacing scale for every screen and component. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    val screen = 16.dp
    val gridGap = 10.dp
    val sectionGap = 20.dp
}

object Dimens {
    val topBarHeight = 56.dp
    val iconButtonSize = 40.dp
    val iconSize = 22.dp
    val buttonHeight = 52.dp
    val coverAspect = 2f / 3f
    val cardTitleHeight = 38.dp
    val avatarSmall = 32.dp
    val avatarMedium = 48.dp
    val avatarLarge = 96.dp
}

val MangaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
