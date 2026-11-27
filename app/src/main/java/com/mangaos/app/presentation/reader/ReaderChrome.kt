package com.mangaos.app.presentation.reader

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.MangaTheme
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.CoverImage
import com.mangaos.app.domain.model.ReaderSettings

/*
 * The reader's chrome. Both bars are translucent panels floating above the page: they never
 * take layout space from the artwork, they hide completely, and every colour comes from the
 * active theme so a light theme is readable in daylight and an AMOLED one stays black.
 */

@Composable
fun ReaderTopBar(
    title: String,
    chapterLabel: String,
    coverUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MangaTheme.tokens
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            scheme.surface.copy(alpha = 0.92f),
                            scheme.surface.copy(alpha = 0.72f),
                            scheme.surface.copy(alpha = 0.34f),
                        ),
                    ),
                )
                // A tap on the bar itself never toggles the controls under the finger.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = scheme.surfaceContainerHigh,
                    modifier = Modifier.size(width = 34.dp, height = 46.dp),
                ) {
                    CoverImage(title = title, coverUrl = coverUrl, modifier = Modifier.size(34.dp, 46.dp))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chapterLabel.isNotBlank()) {
                        Text(
                            text = chapterLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.xs))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    contentColor = scheme.onSurface,
                    modifier = Modifier.size(40.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBottomBar(
    pageInChapter: Int,
    chapterSize: Int,
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    canPrevious: Boolean,
    canNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    settings: ReaderSettings,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    neighbourLoading: Boolean,
    neighbourError: Boolean,
    onRetryNeighbour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tokens = MangaTheme.tokens
    var brightnessOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
        color = scheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.16f)),
        shadowElevation = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.xxs)
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.reader_settings),
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.surfaceContainerHighest,
                        contentColor = tokens.textPrimary,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (neighbourLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = scheme.primary,
                                )
                                Spacer(Modifier.width(5.dp))
                            } else if (neighbourError) {
                                IconButton(onClick = onRetryNeighbour, modifier = Modifier.size(14.dp)) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = stringResource(R.string.state_error_retry),
                                        tint = scheme.error,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = if (chapterSize > 0) "$pageInChapter / $chapterSize" else "—",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Slider(
                        value = progress,
                        onValueChange = onSeek,
                        onValueChangeFinished = onSeekFinished,
                        enabled = chapterSize > 1,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.xs)
                            .height(24.dp),
                        thumb = {
                            Box(
                                Modifier
                                    .size(11.dp)
                                    .background(scheme.primary, RoundedCornerShape(50)),
                            )
                        },
                        track = { state ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(scheme.onSurface.copy(alpha = 0.14f), RoundedCornerShape(50)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(state.value)
                                        .height(4.dp)
                                        .background(scheme.primary, RoundedCornerShape(50)),
                                )
                            }
                        },
                    )
                }

                // Auto scroll is a real setting; the button starts and stops it.
                IconButton(
                    onClick = {
                        onUpdateSettings { it.copy(autoScroll = !it.autoScroll) }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (settings.autoScroll) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(
                            if (settings.autoScroll) R.string.action_pause else R.string.reader_auto_scroll,
                        ),
                        tint = if (settings.autoScroll) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (brightnessOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Brightness6,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.reader_brightness),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary,
                        maxLines = 1,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(
                            value = settings.brightness,
                            onValueChange = { value ->
                                onUpdateSettings { it.copy(brightness = value) }
                            },
                            valueRange = 0.3f..1.5f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Spacing.sm)
                                .height(22.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReaderTool(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.reader_chapters_title),
                    onClick = onOpenChapters,
                )
                ReaderTool(
                    icon = Icons.Outlined.SkipPrevious,
                    label = stringResource(R.string.reader_previous_chapter),
                    enabled = canPrevious,
                    onClick = onPrevious,
                )
                ReaderTool(
                    icon = Icons.Outlined.SkipNext,
                    label = stringResource(R.string.reader_next_chapter),
                    enabled = canNext,
                    onClick = onNext,
                )
                ReaderTool(
                    icon = Icons.Outlined.Brightness6,
                    label = stringResource(R.string.reader_brightness),
                    active = brightnessOpen,
                    onClick = { brightnessOpen = !brightnessOpen },
                )
                ReaderTool(
                    icon = Icons.Outlined.ZoomIn,
                    label = stringResource(R.string.reader_zoom),
                    active = settings.zoomEnabled,
                    onClick = { onUpdateSettings { it.copy(zoomEnabled = !it.zoomEnabled) } },
                )
            }
        }
    }
}

/** One labelled tool: the label is what makes these discoverable without a tooltip. */
@Composable
private fun ReaderTool(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.32f)
        active -> scheme.primary
        else -> MangaTheme.tokens.textSecondary
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
