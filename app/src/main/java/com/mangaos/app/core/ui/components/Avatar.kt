package com.mangaos.app.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mangaos.app.core.designsystem.MangaGold
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.goldColor
import com.mangaos.app.core.designsystem.goldTokensDark
import com.mangaos.app.core.ui.LocalReduceMotion

/**
 * Avatar used by the drawer, the account page and the setup flow. The same fallback is shown for a
 * missing image and for a failed load, so a broken URI never leaves an empty circle.
 *
 * A verified account gets three things on the picture itself: a golden ring, a very slow band of
 * light travelling across it, and the golden badge pinned to its lower corner. The shimmer is one
 * animated gradient offset — no blur, no second bitmap — and it stops when the system asks for
 * reduced motion.
 */
@Composable
fun ProfileAvatar(
    name: String,
    avatarUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    reloadKey: Int = 0,
    verified: Boolean = false,
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val model = remember(avatarUri, reloadKey, context) {
        avatarUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .memoryCacheKey("$uri#$reloadKey")
                .build()
        }
    }
    val dark = goldTokensDark()
    val gold = goldColor()
    val bright = if (dark) MangaGold.darkBright else MangaGold.lightBright
    val reduceMotion = LocalReduceMotion.current
    val shimmer = if (verified && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "avatarShimmer")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "avatarGoldShift",
        )
        value
    } else {
        0f
    }
    // A hairline that grows with the picture, but never becomes a heavy band on a big avatar.
    val ringWidth = (size.value / 44f).coerceIn(1f, 2.5f).dp
    val badgeSize = (size.value * 0.32f).coerceIn(18f, 30f).dp

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUri != null) {
                SubcomposeAsyncImage(
                    model = model,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape),
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(minOf(size * 0.3f, 28.dp)),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    error = {
                        AvatarInitial(name, size)
                        androidx.compose.runtime.LaunchedEffect(Unit) { onError() }
                    },
                )
            } else {
                AvatarInitial(name, size)
            }

            if (verified) {
                // The band of light: a diagonal gold gradient that slides across the circle.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .drawBehind {
                            val w = this.size.width
                            val h = this.size.height
                            val travel = w * 1.8f
                            val x = -w * 0.6f + travel * shimmer
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        bright.copy(alpha = 0.42f),
                                        Color.Transparent,
                                    ),
                                    start = Offset(x - w * 0.35f, 0f),
                                    end = Offset(x + w * 0.35f, h),
                                ),
                            )
                        },
                )
            }
        }

        if (verified) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    gold,
                                    bright.copy(alpha = 0.85f),
                                    gold,
                                ),
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(ringWidth.toPx()),
                        )
                    },
            )
            if (size >= 40.dp) {
                AvatarVerifiedBadge(size = badgeSize, modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
    }
}

/** The golden tick on the picture's corner: a solid disc so it reads over any photo. */
@Composable
private fun AvatarVerifiedBadge(size: Dp, modifier: Modifier = Modifier) {
    val gold = goldColor()
    val bright = if (goldTokensDark()) MangaGold.darkBright else MangaGold.lightBright
    val strokeWidth = (size.value / 18f).coerceIn(1f, 1.6f).dp
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawCircle(
                    brush = Brush.linearGradient(colors = listOf(gold, bright, gold)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MangaOsIcons.verified,
            contentDescription = null,
            tint = gold,
            modifier = Modifier.size(size * 0.66f),
        )
    }
}

@Composable
private fun AvatarInitial(name: String, size: Dp) {
    Text(
        text = name.trim().take(1).uppercase().ifBlank { "M" },
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
        fontSize = (size.value * 0.38f).sp,
    )
}
