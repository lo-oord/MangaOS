package com.mangaos.app.presentation.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.mangaos.app.R

/** One entry in the settings hub; each opens its own focused sub-screen. */
enum class SettingsSection(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
) {
    APPEARANCE(
        route = "settings/appearance",
        titleRes = R.string.settings_section_appearance,
        descriptionRes = R.string.settings_section_appearance_desc,
        icon = Icons.Outlined.Palette,
    ),
    READER(
        route = "settings/reader",
        titleRes = R.string.settings_section_reader,
        descriptionRes = R.string.settings_section_reader_desc,
        icon = Icons.Outlined.AutoStories,
    ),
    NOTIFICATIONS(
        route = "settings/notifications",
        titleRes = R.string.settings_section_notifications,
        descriptionRes = R.string.settings_section_notifications_desc,
        icon = Icons.Outlined.NotificationsActive,
    ),
    DOWNLOADS(
        route = "settings/downloads",
        titleRes = R.string.settings_section_downloads,
        descriptionRes = R.string.settings_section_downloads_desc,
        icon = Icons.Outlined.Download,
    ),
    LIBRARY(
        route = "settings/library",
        titleRes = R.string.settings_section_library,
        descriptionRes = R.string.settings_section_library_desc,
        icon = Icons.Outlined.CollectionsBookmark,
    ),
    PERFORMANCE(
        route = "settings/performance",
        titleRes = R.string.settings_section_performance,
        descriptionRes = R.string.settings_section_performance_desc,
        icon = Icons.Outlined.Speed,
    ),
    INTERACTION(
        route = "settings/interaction",
        titleRes = R.string.settings_section_interaction,
        descriptionRes = R.string.settings_section_interaction_desc,
        icon = Icons.Outlined.TouchApp,
    ),
    BEHAVIOR(
        route = "settings/behavior",
        titleRes = R.string.settings_section_behavior,
        descriptionRes = R.string.settings_section_behavior_desc,
        icon = Icons.Outlined.Tune,
    );

    companion object {
        fun fromRoute(route: String?): SettingsSection? = entries.firstOrNull { it.route == route }
    }
}
