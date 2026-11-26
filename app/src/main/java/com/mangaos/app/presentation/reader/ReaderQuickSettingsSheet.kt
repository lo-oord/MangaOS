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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings

/**
 * Fast, one-handed reader controls. It edits exactly the same [ReaderSettings] flow as the
 * full Settings screen, so the two surfaces never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderQuickSettingsSheet(
    settings: ReaderSettings,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.reader_quick_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )

            QuickSection(stringResource(R.string.reader_group_mode))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ReaderMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.readingMode == mode,
                        onClick = { onUpdate { it.copy(readingMode = mode) } },
                        label = { Text(readerModeText(mode)) },
                    )
                }
            }
            QuickSwitch(
                title = stringResource(R.string.reader_continuous),
                checked = settings.continuousReading,
                onChecked = { value -> onUpdate { it.copy(continuousReading = value) } },
            )
            QuickSwitch(
                title = stringResource(R.string.reader_auto_webtoon),
                checked = settings.autoWebtoonDetection,
                onChecked = { value -> onUpdate { it.copy(autoWebtoonDetection = value) } },
            )

            QuickSection(stringResource(R.string.reader_group_display))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ImageFit.entries.forEach { fit ->
                    FilterChip(
                        selected = settings.imageFit == fit,
                        onClick = { onUpdate { it.copy(imageFit = fit) } },
                        label = { Text(imageFitText(fit)) },
                    )
                }
            }
            QuickSwitch(
                title = stringResource(R.string.reader_zoom),
                checked = settings.zoomEnabled,
                onChecked = { value -> onUpdate { it.copy(zoomEnabled = value) } },
            )
            QuickSlider(
                title = stringResource(R.string.reader_margins),
                value = "${settings.marginsDp}",
                current = settings.marginsDp.toFloat(),
                range = 0f..32f,
                onChange = { value -> onUpdate { it.copy(marginsDp = value.toInt()) } },
            )
            QuickSlider(
                title = stringResource(R.string.reader_spacing),
                value = "${settings.imageSpacingDp}",
                current = settings.imageSpacingDp.toFloat(),
                range = 0f..24f,
                onChange = { value -> onUpdate { it.copy(imageSpacingDp = value.toInt()) } },
            )
            QuickSlider(
                title = stringResource(R.string.reader_brightness),
                value = "%.2f".format(settings.brightness),
                current = settings.brightness,
                range = 0.5f..1.8f,
                onChange = { value -> onUpdate { it.copy(brightness = value) } },
            )

            QuickSection(stringResource(R.string.reader_group_controls))
            QuickSwitch(
                title = stringResource(R.string.reader_show_top),
                checked = settings.showTopBar,
                onChecked = { value -> onUpdate { it.copy(showTopBar = value) } },
            )
            QuickSwitch(
                title = stringResource(R.string.reader_show_bottom),
                checked = settings.showBottomBar,
                onChecked = { value -> onUpdate { it.copy(showBottomBar = value) } },
            )
            QuickSwitch(
                title = stringResource(R.string.reader_auto_hide),
                checked = settings.autoHideControls,
                onChecked = { value -> onUpdate { it.copy(autoHideControls = value) } },
            )
            QuickSwitch(
                title = stringResource(R.string.reader_show_progress),
                checked = settings.showProgressPercent,
                onChecked = { value -> onUpdate { it.copy(showProgressPercent = value) } },
            )

            QuickSection(stringResource(R.string.reader_group_loading))
            QuickSlider(
                title = stringResource(R.string.reader_preload_images),
                value = settings.preloadImages.toString(),
                current = settings.preloadImages.toFloat(),
                range = 1f..5f,
                onChange = { value -> onUpdate { it.copy(preloadImages = value.toInt()) } },
            )
            QuickSlider(
                title = stringResource(R.string.reader_preload_chapters),
                value = settings.preloadChapters.toString(),
                current = settings.preloadChapters.toFloat(),
                range = 0f..3f,
                onChange = { value -> onUpdate { it.copy(preloadChapters = value.toInt()) } },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
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
private fun QuickSection(title: String) {
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.xxs))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}

@Composable
private fun QuickSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
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
private fun QuickSlider(
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
