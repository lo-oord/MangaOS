package com.mangaos.app.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.util.Numbers
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.SettingsOptionRow
import com.mangaos.app.core.ui.components.SettingsSection
import com.mangaos.app.core.ui.components.SettingsSwitchRow
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderBackground
import com.mangaos.app.domain.model.ReaderMode
import com.mangaos.app.domain.model.ReaderSettings

@Composable
fun ReaderSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val viewModel: ReaderSettingsViewModel = viewModel()
    val s by viewModel.settings.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SettingsSection(title = stringResource(R.string.reader_group_mode)) {
            ReaderMode.entries.forEach { mode ->
                SettingsOptionRow(
                    label = readerModeLabel(mode),
                    selected = s.readingMode == mode,
                    onClick = { viewModel.update { it.copy(readingMode = mode) } },
                )
            }
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = stringResource(R.string.reader_auto_webtoon),
                checked = s.autoWebtoonDetection,
                onCheckedChange = { value -> viewModel.update { it.copy(autoWebtoonDetection = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.FlipToFront,
                title = stringResource(R.string.reader_continuous),
                checked = s.continuousReading,
                onCheckedChange = { value -> viewModel.update { it.copy(continuousReading = value) } },
            )
        }

        SettingsSection(title = stringResource(R.string.reader_group_display)) {
            ImageFit.entries.forEach { fit ->
                SettingsOptionRow(
                    label = imageFitLabel(fit),
                    selected = s.imageFit == fit,
                    onClick = { viewModel.update { it.copy(imageFit = fit) } },
                )
            }
            SettingsSwitchRow(
                icon = Icons.Outlined.ZoomIn,
                title = stringResource(R.string.reader_zoom),
                checked = s.zoomEnabled,
                onCheckedChange = { value -> viewModel.update { it.copy(zoomEnabled = value) } },
                showDivider = true,
            )
            SliderRow(
                title = stringResource(R.string.reader_margins),
                value = "${s.marginsDp} dp",
                sliderValue = s.marginsDp.toFloat(),
                range = 0f..32f,
                onValue = { value -> viewModel.update { it.copy(marginsDp = value.toInt()) } },
            )
            SliderRow(
                title = stringResource(R.string.reader_spacing),
                value = "${s.imageSpacingDp} dp",
                sliderValue = s.imageSpacingDp.toFloat(),
                range = 0f..24f,
                onValue = { value -> viewModel.update { it.copy(imageSpacingDp = value.toInt()) } },
            )
        }

        SettingsSection(title = stringResource(R.string.reader_group_image)) {
            SliderRow(
                title = stringResource(R.string.reader_brightness),
                value = Numbers.decimal(s.brightness, 2),
                sliderValue = s.brightness,
                range = 0.5f..2f,
                onValue = { value -> viewModel.update { it.copy(brightness = value) } },
            )
            SliderRow(
                title = stringResource(R.string.reader_contrast),
                value = Numbers.decimal(s.contrast, 2),
                sliderValue = s.contrast,
                range = 0.5f..2f,
                onValue = { value -> viewModel.update { it.copy(contrast = value) } },
            )
            SliderRow(
                title = stringResource(R.string.reader_saturation),
                value = Numbers.decimal(s.saturation, 2),
                sliderValue = s.saturation,
                range = 0f..2f,
                onValue = { value -> viewModel.update { it.copy(saturation = value) } },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Contrast,
                title = stringResource(R.string.reader_grayscale),
                checked = s.grayscale,
                onCheckedChange = { value -> viewModel.update { it.copy(grayscale = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.InvertColors,
                title = stringResource(R.string.reader_invert),
                checked = s.invertColors,
                onCheckedChange = { value -> viewModel.update { it.copy(invertColors = value) } },
            )
        }

        SettingsSection(title = stringResource(R.string.reader_group_scroll)) {
            SettingsSwitchRow(
                icon = Icons.Outlined.Speed,
                title = stringResource(R.string.reader_auto_scroll),
                checked = s.autoScroll,
                onCheckedChange = { value -> viewModel.update { it.copy(autoScroll = value) } },
                showDivider = true,
            )
            SliderRow(
                title = stringResource(R.string.reader_auto_scroll_speed),
                value = Numbers.decimal(s.autoScrollSpeed) + "x",
                sliderValue = s.autoScrollSpeed,
                range = 0.5f..3f,
                onValue = { value -> viewModel.update { it.copy(autoScrollSpeed = value) } },
            )
        }

        SettingsSection(title = stringResource(R.string.reader_group_loading)) {
            SliderRow(
                title = stringResource(R.string.reader_preload_images),
                value = s.preloadImages.toString(),
                sliderValue = s.preloadImages.toFloat(),
                range = 1f..5f,
                onValue = { value ->
                    viewModel.update { it.copy(preloadImages = value.toInt().coerceIn(1, 5)) }
                },
            )
            SliderRow(
                title = stringResource(R.string.reader_preload_chapters),
                value = s.preloadChapters.toString(),
                sliderValue = s.preloadChapters.toFloat(),
                range = 0f..3f,
                onValue = { value ->
                    viewModel.update { it.copy(preloadChapters = value.toInt().coerceIn(0, 3)) }
                },
            )
        }

        SettingsSection(title = stringResource(R.string.reader_group_screen)) {
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = stringResource(R.string.reader_keep_screen),
                checked = s.keepScreenOn,
                onCheckedChange = { value -> viewModel.update { it.copy(keepScreenOn = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Image,
                title = stringResource(R.string.reader_fullscreen),
                checked = s.fullscreen,
                onCheckedChange = { value -> viewModel.update { it.copy(fullscreen = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Brightness6,
                title = stringResource(R.string.reader_hide_status),
                checked = s.hideStatusBar,
                onCheckedChange = { value -> viewModel.update { it.copy(hideStatusBar = value) } },
                showDivider = true,
            )
            Text(
                text = stringResource(R.string.reader_orientation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
            listOf(
                0 to R.string.reader_orientation_auto,
                1 to R.string.reader_orientation_portrait,
                2 to R.string.reader_orientation_landscape,
            ).forEach { (value, label) ->
                SettingsOptionRow(
                    label = stringResource(label),
                    selected = s.screenOrientation == value,
                    onClick = { viewModel.update { it.copy(screenOrientation = value) } },
                    leading = { androidx.compose.material3.Icon(Icons.Outlined.ScreenRotation, contentDescription = null) },
                )
            }
            Text(
                text = stringResource(R.string.reader_background),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md),
            )
            ReaderBackground.entries.forEach { background ->
                SettingsOptionRow(
                    label = backgroundLabel(background),
                    selected = s.backgroundColor == background,
                    onClick = { viewModel.update { it.copy(backgroundColor = background) } },
                    leading = { androidx.compose.material3.Icon(Icons.Outlined.Palette, contentDescription = null) },
                )
            }
        }

        SettingsSection(title = stringResource(R.string.reader_group_controls)) {
            SettingsSwitchRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.reader_show_top),
                checked = s.showTopBar,
                onCheckedChange = { value -> viewModel.update { it.copy(showTopBar = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.reader_show_bottom),
                checked = s.showBottomBar,
                onCheckedChange = { value -> viewModel.update { it.copy(showBottomBar = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.reader_auto_hide),
                checked = s.autoHideControls,
                onCheckedChange = { value -> viewModel.update { it.copy(autoHideControls = value) } },
                showDivider = true,
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.Tune,
                title = stringResource(R.string.reader_show_progress),
                checked = s.showProgressPercent,
                onCheckedChange = { value -> viewModel.update { it.copy(showProgressPercent = value) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showResetDialog = true }) {
                Text(
                    text = stringResource(R.string.reader_reset),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }

    if (showResetDialog) {
        AppDialog(
            title = stringResource(R.string.reader_reset),
            message = stringResource(R.string.reader_reset_message),
            confirmLabel = stringResource(R.string.reader_reset),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showResetDialog = false
                viewModel.reset()
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(value = sliderValue, onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun readerModeLabel(mode: ReaderMode): String = stringResource(
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
private fun imageFitLabel(fit: ImageFit): String = stringResource(
    when (fit) {
        ImageFit.FIT_WIDTH -> R.string.reader_fit_width
        ImageFit.FIT_HEIGHT -> R.string.reader_fit_height
        ImageFit.FIT_SCREEN -> R.string.reader_fit_screen
        ImageFit.ORIGINAL -> R.string.reader_fit_original
    },
)

@Composable
private fun backgroundLabel(background: ReaderBackground): String = stringResource(
    when (background) {
        ReaderBackground.BLACK -> R.string.reader_bg_black
        ReaderBackground.WHITE -> R.string.reader_bg_white
        ReaderBackground.GRAY -> R.string.reader_bg_gray
        ReaderBackground.CUSTOM -> R.string.reader_bg_custom
    },
)
