package com.mangaos.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.goldBrush
import com.mangaos.app.core.designsystem.goldColor
import com.mangaos.app.core.ui.LocalReduceMotion

/**
 * A verified name in gold with one slow highlight travelling across it. The motion is a single
 * gradient offset, it stops completely when the system asks for reduced motion, and the badge is
 * the same protected icon everywhere it appears.
 */
@Composable
fun VerifiedName(
    name: String,
    verified: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 16.dp,
    showBadge: Boolean = true,
    maxLines: Int = 1,
) {
    val reduceMotion = LocalReduceMotion.current
    val shift = if (verified && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "verifiedShimmer")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "goldShift",
        )
        value
    } else {
        0f
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = if (verified) style.copy(brush = goldBrush(shift)) else style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (verified && showBadge) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = MangaOsIcons.verified,
                contentDescription = null,
                tint = goldColor(),
                modifier = Modifier.size(badgeSize),
            )
        }
    }
}

/** The badge on its own, for places that already print the name elsewhere. */
@Composable
fun VerifiedBadge(size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Icon(
        imageVector = MangaOsIcons.verified,
        contentDescription = null,
        tint = goldColor(),
        modifier = modifier.size(size),
    )
}
