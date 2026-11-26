package com.mangaos.app.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.core.ui.components.SettingsSection
import com.mangaos.app.core.ui.components.SettingsSwitchRow
import com.mangaos.app.domain.model.ImageFit
import com.mangaos.app.domain.model.ReaderBackground
import com.mangaos.app.domain.model.ReaderMode

@Composable
fun ReaderSettingsScreen(modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val viewModel: ReaderSettingsViewModel = viewModel()
    val s by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Reader settings", style = MaterialTheme.typography.headlineSmall)

        SettingsSection(title = "Reading mode") {
            ReaderMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = s.readingMode == mode,
                        onClick = { viewModel.update { it.copy(readingMode = mode) } },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(mode.name)
                }
            }
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Auto Webtoon detection",
                checked = s.autoWebtoonDetection,
                onCheckedChange = { v -> viewModel.update { it.copy(autoWebtoonDetection = v) } },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Continuous reading",
                checked = s.continuousReading,
                onCheckedChange = { v -> viewModel.update { it.copy(continuousReading = v) } },
            )
        }

        SettingsSection(title = "Display") {
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Keep screen on",
                checked = s.keepScreenOn,
                onCheckedChange = { v -> viewModel.update { it.copy(keepScreenOn = v) } },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Show top bar",
                checked = s.showTopBar,
                onCheckedChange = { v -> viewModel.update { it.copy(showTopBar = v) } },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Show bottom bar",
                checked = s.showBottomBar,
                onCheckedChange = { v -> viewModel.update { it.copy(showBottomBar = v) } },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Auto hide controls",
                checked = s.autoHideControls,
                onCheckedChange = { v -> viewModel.update { it.copy(autoHideControls = v) } },
            )
            Text("Margins: ${s.marginsDp}")
            Slider(value = s.marginsDp.toFloat(), onValueChange = { v -> viewModel.update { rs -> rs.copy(marginsDp = v.toInt()) } }, valueRange = 0f..32f)
            Text("Spacing: ${s.imageSpacingDp}")
            Slider(value = s.imageSpacingDp.toFloat(), onValueChange = { v -> viewModel.update { rs -> rs.copy(imageSpacingDp = v.toInt()) } }, valueRange = 0f..24f)
        }

        SettingsSection(title = "Image adjustments") {
            Text("Brightness: ${"%.2f".format(s.brightness)}")
            Slider(value = s.brightness, onValueChange = { v -> viewModel.update { rs -> rs.copy(brightness = v) } }, valueRange = 0.5f..2f)
            Text("Contrast: ${"%.2f".format(s.contrast)}")
            Slider(value = s.contrast, onValueChange = { v -> viewModel.update { rs -> rs.copy(contrast = v) } }, valueRange = 0.5f..2f)
            Text("Saturation: ${"%.2f".format(s.saturation)}")
            Slider(value = s.saturation, onValueChange = { v -> viewModel.update { rs -> rs.copy(saturation = v) } }, valueRange = 0f..2f)
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Grayscale",
                checked = s.grayscale,
                onCheckedChange = { v -> viewModel.update { it.copy(grayscale = v) } },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.AutoStories,
                title = "Invert colors",
                checked = s.invertColors,
                onCheckedChange = { v -> viewModel.update { it.copy(invertColors = v) } },
            )
        }

        SettingsSection(title = "Loading") {
            Text("Preload images: ${s.preloadImages}")
            Slider(value = s.preloadImages.toFloat(), onValueChange = { v -> viewModel.update { rs -> rs.copy(preloadImages = v.toInt().coerceAtLeast(1)) } }, valueRange = 1f..10f)
            Text("Preload chapters: ${s.preloadChapters}")
            Slider(value = s.preloadChapters.toFloat(), onValueChange = { v -> viewModel.update { rs -> rs.copy(preloadChapters = v.toInt().coerceAtLeast(0)) } }, valueRange = 0f..5f)
        }

        androidx.compose.material3.Button(onClick = { viewModel.reset() }) {
            Text("Reset all")
        }

        Spacer(Modifier.height(32.dp))
    }
}