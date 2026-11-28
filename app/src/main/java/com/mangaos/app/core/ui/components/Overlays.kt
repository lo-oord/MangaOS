package com.mangaos.app.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.GlassSurface
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaGlass
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.designsystem.glassTokens
import com.mangaos.app.core.ui.LocalReduceMotion
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's popup system.
 *
 * Every dialog and sheet is one glass surface drawn from [MangaGlass], with one entrance motion
 * (a short fade plus a 4% scale) and the same paddings, corner, border and button order. Screens
 * never build their own popup chrome; they pass content to the types below.
 */

/** The dialog's single entrance: fast, stable, and skipped when motion is reduced. */
@Composable
private fun popupEntrance(reduceMotion: Boolean): Modifier {
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) progress.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
    }
    return Modifier.graphicsLayer {
        alpha = progress.value
        val scale = 0.96f + 0.04f * progress.value
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A confirmation, notice or small form. One glass card over the theme scrim: [title], an optional
 * [message], optional custom [content] and a confirm/dismiss pair. Tapping outside or the system
 * back button dismisses it, and repeated taps on confirm are the caller's to guard.
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissLabel: String? = null,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = glassTokens()
    val reduceMotion = LocalReduceMotion.current
    val entrance = popupEntrance(reduceMotion)
    val interaction = remember { MutableInteractionSource() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Scrim: the screen behind stays visible but recedes.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.scrim),
            )
            GlassSurface(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth(0.88f)
                    .then(entrance),
                shape = RoundedCornerShape(MangaGlass.dialogCorner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            AppIcon(
                                icon = icon,
                                contentDescription = null,
                                role = if (destructive) IconRole.Error else IconRole.Primary,
                                size = 20.dp,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (message != null) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    content?.let { body ->
                        Spacer(Modifier.height(Spacing.sm))
                        body()
                    }
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            TextButton(onClick = onDismiss) {
                                Text(
                                    text = dismissLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(Spacing.xs))
                        }
                        TextButton(onClick = onConfirm) {
                            Text(
                                text = confirmLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (destructive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A short confirmation that only needs a title and one action. */
@Composable
fun AppNoticeDialog(
    title: String,
    message: String? = null,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = title,
        message = message,
        confirmLabel = stringResource(R.string.action_done),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        icon = MangaOsIcons.info,
    )
}

/**
 * A glass bottom sheet. The sheet header is part of the system, so every sheet in the app opens
 * with the same handle, title and spacing, and the content area is already padded for the
 * navigation bar and an open keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = glassTokens()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = tokens.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = tokens.scrim,
        shape = RoundedCornerShape(topStart = MangaGlass.corner, topEnd = MangaGlass.corner),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        AppIcon(
                            icon = icon,
                            contentDescription = null,
                            role = IconRole.Primary,
                            size = 18.dp,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }
            content()
        }
    }
}

@Composable
fun DownloadProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}
