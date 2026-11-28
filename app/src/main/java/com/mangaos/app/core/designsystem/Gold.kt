package com.mangaos.app.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The verification colour. It is a single, deliberately warm gold that stays readable on every
 * palette: deeper on light surfaces, brighter on dark ones, and never used for anything else.
 */
object MangaGold {
    val light = Color(0xFFB8860B)
    val lightBright = Color(0xFFE0A82E)
    val dark = Color(0xFFFFD166)
    val darkBright = Color(0xFFFFE7A3)
}

@Composable
fun goldColor(): Color = if (glassTokens().dark) MangaGold.dark else MangaGold.light

/** True when the active palette is dark, so gold can pick its brighter pair. */
@Composable
fun goldTokensDark(): Boolean = glassTokens().dark

@Composable
fun goldBrush(shift: Float = 0f): Brush {
    val tokens = glassTokens()
    val base = if (tokens.dark) MangaGold.dark else MangaGold.light
    val bright = if (tokens.dark) MangaGold.darkBright else MangaGold.lightBright
    val start = 0f
    val end = 620f
    // The band moves across the text; a fixed offset is simply a gradient.
    val offset = shift * end
    return Brush.linearGradient(
        colors = listOf(base, bright, base),
        start = Offset(start + offset, 0f),
        end = Offset(end + offset, 260f),
    )
}

/**
 * The one golden hairline shared by covers and frames. A brush, not a flat colour, so the line
 * carries the same two-tone warmth as the verified name instead of reading as yellow paint.
 */
@Composable
fun goldBorderStroke(width: Dp = 1.dp): BorderStroke = BorderStroke(width, goldBrush())

/** Kept out of the theme: gold is the verification colour, not a brand accent. */
@Composable
fun goldContainer(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        MangaGold.light.copy(alpha = 0.14f)
    } else {
        MangaGold.dark.copy(alpha = 0.16f)
    }

private fun Color.luminance(): Float =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue)
