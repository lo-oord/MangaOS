package com.mangaos.app.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The glass design system.
 *
 * One source of truth for every translucent surface in MangaOS: cards, the top bar, the reader's
 * controls, bottom sheets, dialogs and menus. Nothing here hard-codes a colour — the tokens are
 * derived from the active [MaterialTheme] colour scheme, so all eight themes and the AMOLED mode
 * stay consistent, and a theme change recolours every glass surface on the next frame.
 *
 * Performance: no blur pass is used. The glass look comes from a translucent fill, a hairline
 * border and a single vertical sheen, which cost one extra draw call instead of a full-screen
 * offscreen buffer — long lists and the reader keep their frame rate on modest devices.
 */
object MangaGlass {

    /** Corner radius shared by every glass container. */
    val corner = 22.dp

    /** Tighter radius for dialogs and menus, which sit inside a scrim. */
    val dialogCorner = 24.dp

    /** Hairline border width; a single value so no surface looks heavier than another. */
    val borderWidth = 1.dp

    /** Blur is disabled by design; kept as a documented token for future devices. */
    val blurRadius = 0.dp
}

/** Resolved glass values for the palette that is currently applied. */
@Immutable
data class GlassTokens(
    /** Translucent fill drawn over whatever is behind the surface. */
    val background: Color,
    /** Hairline outline that separates the surface from the background. */
    val border: Color,
    /** Top sheen; the only decorative gradient in the system. */
    val highlight: Color,
    /** Scrim used behind dialogs and sheets. */
    val scrim: Color,
    /** Drop shadow; zero in the dark palette where a shadow would only smear the edge. */
    val shadow: Dp,
    /** True when the active palette is dark, for the few callers that need a contrast nudge. */
    val dark: Boolean,
)

@Composable
fun glassTokens(): GlassTokens {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        // The surface is the theme's own surface, not an arbitrary grey: in the light palette it
        // is a bright pane that never disappears into the background, in the dark one it is a
        // lifted pane that never turns into a halo.
        val dark = scheme.background.luminance() < 0.5f
        GlassTokens(
            background = if (dark) {
                scheme.surface.copy(alpha = 0.84f)
            } else {
                scheme.surface.copy(alpha = 0.92f)
            },
            border = scheme.primary.copy(alpha = if (dark) 0.22f else 0.16f),
            highlight = if (dark) {
                Color.White.copy(alpha = 0.05f)
            } else {
                scheme.surfaceContainerLowest.copy(alpha = 0.55f)
            },
            scrim = scheme.scrim.copy(alpha = if (dark) 0.62f else 0.42f),
            shadow = if (dark) 0.dp else 6.dp,
            dark = dark,
        )
    }
}

/**
 * The glass toolbar used by top bars: the same translucent fill as every other surface plus one
 * hairline along its bottom edge, which is what separates a floating bar from the page content.
 */
@Composable
fun Modifier.glassBar(): Modifier {
    val tokens = glassTokens()
    return this
        .background(tokens.background)
        .background(Brush.verticalGradient(listOf(tokens.highlight, Color.Transparent)))
        .drawBehind {
            drawLine(
                color = tokens.border,
                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                strokeWidth = MangaGlass.borderWidth.toPx(),
            )
        }
}

/**
 * A glass container: shadow (light palettes only), translucent fill, top sheen and a hairline
 * border. Every popup, card and floating control is built on this one composable.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MangaGlass.corner),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bordered: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = glassTokens()
    Box(
        modifier = modifier
            .shadow(tokens.shadow, shape, clip = false)
            .clip(shape)
            .background(tokens.background)
            .background(
                Brush.verticalGradient(
                    listOf(tokens.highlight, Color.Transparent),
                ),
            )
            .then(
                if (bordered) {
                    Modifier.border(MangaGlass.borderWidth, tokens.border, shape)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}
