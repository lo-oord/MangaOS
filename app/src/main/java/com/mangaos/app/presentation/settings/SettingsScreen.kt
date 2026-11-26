package com.mangaos.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.designsystem.colorSchemeFor
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.ComingSoonRow
import com.mangaos.app.core.ui.components.SettingsOptionRow
import com.mangaos.app.core.ui.components.SettingsRow
import com.mangaos.app.core.ui.components.SettingsSection
import com.mangaos.app.core.ui.components.SettingsSwitchRow

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val darkPreference by viewModel.darkMode.collectAsStateWithLifecycle()

    val darkTheme = darkPreference ?: isSystemInDarkTheme()

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SettingsSection(title = stringResource(R.string.settings_appearance)) {
            SettingsRow(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.settings_language),
                value = languageLabel(language),
                showDivider = true,
                onClick = { showLanguageSheet = true },
            )
            SettingsRow(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_theme),
                value = themeLabel(theme),
                showDivider = true,
                onClick = { showThemeSheet = true },
            )
            SettingsSwitchRow(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.settings_dark_mode),
                checked = darkTheme,
                onCheckedChange = viewModel::setDarkMode,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_reader)) {
            ComingSoonRow(
                icon = Icons.Outlined.AutoStories,
                title = stringResource(R.string.settings_reader_soon),
                badge = stringResource(R.string.badge_soon),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_notifications)) {
            ComingSoonRow(
                icon = Icons.Outlined.NotificationsActive,
                title = stringResource(R.string.settings_notifications_soon),
                badge = stringResource(R.string.badge_soon),
            )
        }

        Spacer(Modifier.height(Spacing.xl))
    }

    if (showLanguageSheet) {
        AppBottomSheet(onDismiss = { showLanguageSheet = false }) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            )
            listOf(
                "ar" to R.string.language_arabic,
                "en" to R.string.language_english,
                "ja" to R.string.language_japanese,
            ).forEach { (code, labelRes) ->
                SettingsOptionRow(
                    label = stringResource(labelRes),
                    selected = language == code,
                    onClick = {
                        showLanguageSheet = false
                        viewModel.setLanguage(code)
                    },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }

    if (showThemeSheet) {
        AppBottomSheet(onDismiss = { showThemeSheet = false }) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            )
            AppTheme.entries.forEach { option ->
                SettingsOptionRow(
                    label = themeLabel(option),
                    selected = theme == option,
                    onClick = {
                        showThemeSheet = false
                        viewModel.setTheme(option)
                    },
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(colorSchemeFor(option, darkTheme).primary),
                        )
                    },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun languageLabel(code: String): String = stringResource(
    when (code) {
        "ar" -> R.string.language_arabic
        "ja" -> R.string.language_japanese
        else -> R.string.language_english
    },
)

@Composable
private fun themeLabel(theme: AppTheme): String = stringResource(
    when (theme) {
        AppTheme.DEFAULT -> R.string.theme_default
        AppTheme.MIKU -> R.string.theme_miku
        AppTheme.NEON -> R.string.theme_neon
        AppTheme.SKY -> R.string.theme_sky
    },
)
