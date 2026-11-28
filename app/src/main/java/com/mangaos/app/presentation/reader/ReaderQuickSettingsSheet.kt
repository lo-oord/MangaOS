package com.mangaos.app.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.util.Numbers
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.designsystem.glassPanelFill
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings

/**
 * Fast, one-handed reader controls. It edits exactly the same [ReaderSettings] flow as the
 * full settings screen, so the two surfaces never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderQuickSettingsSheet(
    settings: ReaderSettings,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = glassPanelFill(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.reader_quick_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.reader_quick_more),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))

            SheetSection(title = stringResource(R.string.reader_group_mode), icon = Icons.Outlined.Tune)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ReaderMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.readingMode == mode,
                        onClick = { onUpdate { it.copy(readingMode = mode) } },
                        label = { Text(readerModeText(mode)) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
            SheetSwitch(
                title = stringResource(R.string.reader_continuous),
                checked = settings.continuousReading,
                onChecked = { value -> onUpdate { it.copy(continuousReading = value) } },
            )
            SheetSwitch(
                title = stringResource(R.string.reader_auto_webtoon),
                checked = settings.autoWebtoonDetection,
                onChecked = { value -> onUpdate { it.copy(autoWebtoonDetection = value) } },
            )

            if (settings.readingMode == ReaderMode.HORIZONTAL_LTR ||
                settings.readingMode == ReaderMode.HORIZONTAL_RTL
            ) {
                SheetSection(title = stringResource(R.string.reader_direction))
                Text(
                    text = stringResource(R.string.reader_direction_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    FilterChip(
                        selected = settings.readingMode == ReaderMode.HORIZONTAL_RTL,
                        onClick = { onUpdate { it.copy(readingMode = ReaderMode.HORIZONTAL_RTL) } },
                        label = { Text(stringResource(R.string.reader_mode_rtl)) },
                        shape = RoundedCornerShape(10.dp),
                    )
                    FilterChip(
                        selected = settings.readingMode == ReaderMode.HORIZONTAL_LTR,
                        onClick = { onUpdate { it.copy(readingMode = ReaderMode.HORIZONTAL_LTR) } },
                        label = { Text(stringResource(R.string.reader_mode_ltr)) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }

            SheetSection(title = stringResource(R.string.reader_group_display))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ImageFit.entries.forEach { fit ->
                    FilterChip(
                        selected = settings.imageFit == fit,
                        onClick = { onUpdate { it.copy(imageFit = fit) } },
                        label = { Text(imageFitText(fit)) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
            SheetSwitch(
                title = stringResource(R.string.reader_zoom),
                checked = settings.zoomEnabled,
                onChecked = { value -> onUpdate { it.copy(zoomEnabled = value) } },
            )
            SheetSlider(
                title = stringResource(R.string.reader_margins),
                value = "${settings.marginsDp}",
                current = settings.marginsDp.toFloat(),
                range = 0f..32f,
                onChange = { value -> onUpdate { it.copy(marginsDp = value.toInt()) } },
            )
            SheetSlider(
                title = stringResource(R.string.reader_spacing),
                value = "${settings.imageSpacingDp}",
                current = settings.imageSpacingDp.toFloat(),
                range = 0f..24f,
                onChange = { value -> onUpdate { it.copy(imageSpacingDp = value.toInt()) } },
            )
            SheetSlider(
                title = stringResource(R.string.reader_brightness),
                value = Numbers.decimal(settings.brightness, 2),
                current = settings.brightness,
                range = 0.5f..1.8f,
                onChange = { value -> onUpdate { it.copy(brightness = value) } },
            )

            SheetSection(title = stringResource(R.string.reader_group_controls))
            SheetSwitch(
                title = stringResource(R.string.reader_show_top),
                checked = settings.showTopBar,
                onChecked = { value -> onUpdate { it.copy(showTopBar = value) } },
            )
            SheetSwitch(
                title = stringResource(R.string.reader_show_bottom),
                checked = settings.showBottomBar,
                onChecked = { value -> onUpdate { it.copy(showBottomBar = value) } },
            )
            SheetSwitch(
                title = stringResource(R.string.reader_auto_hide),
                checked = settings.autoHideControls,
                onChecked = { value -> onUpdate { it.copy(autoHideControls = value) } },
            )
            SheetSwitch(
                title = stringResource(R.string.reader_show_progress),
                checked = settings.showProgressPercent,
                onChecked = { value -> onUpdate { it.copy(showProgressPercent = value) } },
            )

            SheetSection(title = stringResource(R.string.reader_group_loading))
            SheetSlider(
                title = stringResource(R.string.reader_preload_images),
                value = settings.preloadImages.toString(),
                current = settings.preloadImages.toFloat(),
                range = 0f..5f,
                onChange = { value -> onUpdate { it.copy(preloadImages = value.toInt()) } },
            )
            SheetSlider(
                title = stringResource(R.string.reader_preload_chapters),
                value = settings.preloadChapters.toString(),
                current = settings.preloadChapters.toFloat(),
                range = 0f..3f,
                onChange = { value -> onUpdate { it.copy(preloadChapters = value.toInt()) } },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onOpenAdvanced) {
                    Text(stringResource(R.string.reader_quick_more))
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun SheetSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        androidx.compose.material3.HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SheetSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SheetSlider(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(30.dp),
        )
    }
}

@Composable
private fun readerModeText(mode: ReaderMode): String = stringResource(
    when (mode) {
        ReaderMode.VERTICAL -> R.string.reader_mode_vertical
        ReaderMode.HORIZONTAL_LTR -> R.string.reader_mode_ltr
        ReaderMode.HORIZONTAL_RTL -> R.string.reader_mode_rtl
        ReaderMode.SINGLE_PAGE -> R.string.reader_mode_single
        ReaderMode.DOUBLE_PAGE -> R.string.reader_mode_double
        ReaderMode.WEBTOON -> R.string.reader_mode_webtoon
    },
)

@Composable
private fun imageFitText(fit: ImageFit): String = stringResource(
    when (fit) {
        ImageFit.FIT_WIDTH -> R.string.reader_fit_width
        ImageFit.FIT_HEIGHT -> R.string.reader_fit_height
        ImageFit.FIT_SCREEN -> R.string.reader_fit_screen
        ImageFit.ORIGINAL -> R.string.reader_fit_original
    },
)
