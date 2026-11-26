package com.mangaos.app.presentation.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.mangaos.app.R

object Routes {
    const val SPLASH = "splash"
    const val AUTH_CHOICE = "auth/choice"
    const val AUTH_SIGN_UP = "auth/sign-up"
    const val AUTH_PROFILE = "auth/profile"
    const val AUTH_VERIFY = "auth/verify"
    const val AUTH_SIGN_IN = "auth/sign-in"
    const val MAIN = "main"

    const val NOTIFICATIONS = "notifications"
    const val PRIVACY = "privacy"
    const val TERMS = "terms"
    const val SOURCES = "sources"
    const val MANGA_DETAIL = "detail/{sourceId}/{mangaId}"
    const val ARG_SOURCE_ID = "sourceId"
    const val ARG_MANGA_ID = "mangaId"

    /** Manga ids are often source-relative URLs, so both parts are percent-encoded. */
    fun mangaDetail(sourceId: String, mangaId: String): String =
        "detail/${Uri.encode(sourceId)}/${Uri.encode(mangaId)}"
}

/** Drawer destinations, in the order required by the product specification. */
enum class DrawerDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    LATEST("latest", R.string.nav_latest, Icons.Outlined.NewReleases),
    MANGA("manga", R.string.nav_manga, Icons.Outlined.MenuBook),
    LIBRARY("library", R.string.nav_library, Icons.Outlined.CollectionsBookmark),
    SOURCES(Routes.SOURCES, R.string.nav_sources, Icons.Outlined.Extension),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Outlined.Download),
    STORAGE("storage", R.string.nav_storage, Icons.Outlined.SdStorage),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
    ABOUT("about", R.string.nav_about, Icons.Outlined.Info);

    companion object {
        val startRoute: String = LATEST.route
        fun fromRoute(route: String?): DrawerDestination? = entries.firstOrNull { it.route == route }
    }
}
