package com.mangaos.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.designsystem.colorSchemeFor
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.SettingsOptionRow
import com.mangaos.app.core.ui.components.SettingsRow
import com.mangaos.app.core.ui.components.SettingsSection as SettingsGroup
import com.mangaos.app.core.ui.components.SettingsSwitchRow
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.domain.model.LibraryFilter
import com.mangaos.app.domain.model.LibrarySort
import com.mangaos.app.domain.model.MangaViewMode
import com.mangaos.app.presentation.reader.ReaderSettingsScreen
import kotlinx.coroutines.launch

@Composable
fun SettingsDetailScreen(
    section: SettingsSection,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (section == SettingsSection.READER) {
        ReaderSettingsScreen(modifier = modifier)
        return
    }

    val viewModel: SettingsViewModel = viewModel()
    val app by viewModel.appSettings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val clearingCache by viewModel.clearingCache.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val darkTheme = darkMode ?: isSystemInDarkTheme()

    var showLanguageSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.sm,
            bottom = Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        when (section) {
            SettingsSection.APPEARANCE -> {
                item {
                    SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
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
                            onClick = { showThemeSheet = true },
                        )
                    }
                }
                item {
                    SettingsGroup(title = stringResource(R.string.settings_dark_mode)) {
                        SettingsOptionRow(
                            label = stringResource(R.string.settings_dark_mode_system),
                            selected = darkMode == null,
                            onClick = { viewModel.setDarkMode(null) },
                            leading = { Icon(Icons.Outlined.SettingsBrightness, contentDescription = null) },
                        )
                        SettingsOptionRow(
                            label = stringResource(R.string.settings_dark_mode_light),
                            selected = darkMode == false,
                            onClick = { viewModel.setDarkMode(false) },
                            leading = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                        )
                        SettingsOptionRow(
                            label = stringResource(R.string.settings_dark_mode_dark),
                            selected = darkMode == true,
                            onClick = { viewModel.setDarkMode(true) },
                            leading = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                        )
                    }
                }
            }

            SettingsSection.NOTIFICATIONS -> item {
                SettingsGroup(title = stringResource(R.string.settings_section_notifications)) {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = stringResource(R.string.settings_notifications_enabled),
                        checked = app.notificationsEnabled,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(notificationsEnabled = value) }
                        },
                        showDivider = true,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Download,
                        title = stringResource(R.string.settings_notifications_downloads),
                        checked = app.downloadNotifications,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(downloadNotifications = value) }
                        },
                    )
                }
            }

            SettingsSection.DOWNLOADS -> {
                item {
                    SettingsGroup(title = stringResource(R.string.settings_section_downloads)) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Wifi,
                            title = stringResource(R.string.settings_downloads_wifi_only),
                            checked = app.wifiOnlyDownloads,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(wifiOnlyDownloads = value) }
                            },
                            showDivider = true,
                        )
                        SettingsRow(
                            icon = Icons.Outlined.Folder,
                            title = stringResource(R.string.settings_downloads_location),
                            value = context.filesDir.absolutePath.substringAfterLast('/'),
                            showDivider = true,
                        )
                        SettingsRow(
                            icon = Icons.Outlined.Download,
                            title = stringResource(R.string.settings_downloads_manage),
                            onClick = onOpenDownloads,
                        )
                    }
                }
            }

            SettingsSection.LIBRARY -> {
                item {
                    SettingsGroup(title = stringResource(R.string.settings_library_view)) {
                        SettingsOptionRow(
                            label = stringResource(R.string.settings_library_grid),
                            selected = viewMode == MangaViewMode.GRID,
                            onClick = { viewModel.setViewMode(MangaViewMode.GRID) },
                            leading = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                        )
                        SettingsOptionRow(
                            label = stringResource(R.string.settings_library_list),
                            selected = viewMode == MangaViewMode.LIST,
                            onClick = { viewModel.setViewMode(MangaViewMode.LIST) },
                            leading = { Icon(Icons.Outlined.ViewAgenda, contentDescription = null) },
                        )
                    }
                }
                item {
                    SettingsGroup(title = stringResource(R.string.settings_library_sort)) {
                        LibrarySort.entries.forEach { sort ->
                            SettingsOptionRow(
                                label = librarySortLabel(sort),
                                selected = app.librarySort == sort,
                                onClick = { viewModel.update { it.copy(librarySort = sort) } },
                                leading = { Icon(Icons.Outlined.Sort, contentDescription = null) },
                            )
                        }
                    }
                }
                item {
                    SettingsGroup(title = stringResource(R.string.settings_library_filter)) {
                        LibraryFilter.entries.forEach { filter ->
                            SettingsOptionRow(
                                label = libraryFilterLabel(filter),
                                selected = app.libraryFilter == filter,
                                onClick = { viewModel.update { it.copy(libraryFilter = filter) } },
                                leading = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
                            )
                        }
                    }
                }
                item {
                    SettingsGroup(title = stringResource(R.string.settings_section_behavior)) {
                        SettingsSwitchRow(
                            icon = Icons.Outlined.Refresh,
                            title = stringResource(R.string.settings_behavior_refresh_on_open),
                            checked = app.autoRefreshOnOpen,
                            onCheckedChange = { value ->
                                viewModel.update { it.copy(autoRefreshOnOpen = value) }
                            },
                        )
                    }
                }
            }

            SettingsSection.PERFORMANCE -> {
                item {
                    SettingsGroup(title = stringResource(R.string.settings_performance_cache)) {
                        SettingsRow(
                            icon = Icons.Outlined.Speed,
                            title = stringResource(R.string.settings_performance_cache_size),
                            value = formatBytes(cacheBytes),
                            showDivider = true,
                            onClick = { viewModel.refreshCacheSize() },
                        )
                        SettingsRow(
                            icon = Icons.Outlined.CleaningServices,
                            title = stringResource(
                                if (clearingCache) R.string.settings_performance_clearing
                                else R.string.settings_performance_clear,
                            ),
                            onClick = { if (!clearingCache) showClearCacheDialog = true },
                        )
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.settings_performance_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            }

            SettingsSection.INTERACTION -> item {
                SettingsGroup(title = stringResource(R.string.settings_section_interaction)) {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Animation,
                        title = stringResource(R.string.settings_interaction_reduce_motion),
                        checked = app.reduceMotion,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(reduceMotion = value) }
                        },
                        showDivider = true,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Vibration,
                        title = stringResource(R.string.settings_interaction_haptics),
                        checked = app.hapticFeedback,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(hapticFeedback = value) }
                        },
                    )
                }
            }

            SettingsSection.BEHAVIOR -> item {
                SettingsGroup(title = stringResource(R.string.settings_section_behavior)) {
                    SettingsSwitchRow(
                        icon = Icons.Outlined.RestartAlt,
                        title = stringResource(R.string.settings_behavior_restore_last),
                        checked = app.restoreLastScreen,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(restoreLastScreen = value) }
                        },
                        showDivider = true,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.AutoStories,
                        title = stringResource(R.string.settings_behavior_confirm_exit),
                        checked = app.confirmExit,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(confirmExit = value) }
                        },
                        showDivider = true,
                    )
                    SettingsSwitchRow(
                        icon = Icons.Outlined.Refresh,
                        title = stringResource(R.string.settings_behavior_refresh_on_open),
                        checked = app.autoRefreshOnOpen,
                        onCheckedChange = { value ->
                            viewModel.update { it.copy(autoRefreshOnOpen = value) }
                        },
                    )
                }
            }

            SettingsSection.READER -> Unit
        }

        item { Spacer(Modifier.height(Spacing.xl)) }
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

    if (showClearCacheDialog) {
        AppDialog(
            title = stringResource(R.string.settings_performance_clear_confirm_title),
            message = stringResource(R.string.settings_performance_clear_confirm_message),
            confirmLabel = stringResource(R.string.settings_performance_clear),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showClearCacheDialog = false
                viewModel.clearCache { freed ->
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(R.string.settings_performance_freed, formatBytes(freed)),
                        )
                    }
                }
            },
            onDismiss = { showClearCacheDialog = false },
        )
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

@Composable
private fun librarySortLabel(sort: LibrarySort): String = stringResource(
    when (sort) {
        LibrarySort.TITLE -> R.string.settings_sort_title
        LibrarySort.LAST_UPDATED -> R.string.settings_sort_last_updated
        LibrarySort.LAST_READ -> R.string.settings_sort_last_read
    },
)

@Composable
private fun libraryFilterLabel(filter: LibraryFilter): String = stringResource(
    when (filter) {
        LibraryFilter.ALL -> R.string.settings_filter_all
        LibraryFilter.READING -> R.string.settings_filter_reading
        LibraryFilter.UNREAD -> R.string.settings_filter_unread
    },
)
