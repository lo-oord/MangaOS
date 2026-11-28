package com.mangaos.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

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
