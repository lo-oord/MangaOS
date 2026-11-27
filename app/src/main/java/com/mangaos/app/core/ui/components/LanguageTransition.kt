package com.mangaos.app.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.appString
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The language request that is being applied right now. The screen that changes the language
 * announces the target code here, [com.mangaos.app.presentation.MangaOsApp] applies it and
 * clears the request, and the overlay below reads the very same request — so the message can
 * never name a language that differs from the one actually being applied.
 */
data class LanguageSwitch(val code: String) {
    /** The language's own name, e.g. العربية / English / 日本語 — independent of the UI locale. */
    val displayName: String
        get() = Locale.forLanguageTag(code).getDisplayName(Locale.forLanguageTag(code))
}

object LanguageTransition {

    private val _pending = MutableStateFlow<LanguageSwitch?>(null)
    val pending: StateFlow<LanguageSwitch?> = _pending.asStateFlow()

    fun begin(code: String) {
        _pending.value = LanguageSwitch(code)
    }

    fun finish(code: String) {
        if (_pending.value?.code == code) _pending.value = null
    }
}

/**
 * A centred, theme-aware card shown while a language is being applied. It floats over the app
 * with a soft scrim so nothing jumps before the new resources land, and it animates an icon
 * rather than pretending to do work: the caller only keeps it up until the change has applied.
 */
@Composable
fun LanguageTransitionOverlay(switch: LanguageSwitch, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "languageTransition")
    val turn by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "languageTurn",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "languagePulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.scrim.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.96f),
            contentColor = scheme.onSurface,
            shadowElevation = 18.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = Spacing.xl)
                .border(1.dp, scheme.primary.copy(alpha = 0.28f), RoundedCornerShape(26.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
                    // Two rings turning in opposite directions read as "language swapping".
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .rotate(turn)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        scheme.primary.copy(alpha = 0f),
                                        scheme.primary.copy(alpha = 0.55f),
                                        scheme.tertiary.copy(alpha = 0.15f),
                                        scheme.primary.copy(alpha = 0f),
                                    ),
                                ),
                                CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(scheme.surfaceContainerLowest, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier
                                .size(30.dp)
                                .rotate(-turn / 3f),
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = switch.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                        modifier = Modifier.padding(end = Spacing.xxs),
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp * pulse)
                            .background(scheme.primary, CircleShape),
                    )
                }

                Spacer(Modifier.height(Spacing.xs))

                Text(
                    text = appString(R.string.language_switching_to, switch.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
