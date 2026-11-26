package com.mangaos.app.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Brand colours sampled directly from the MangaOS logo artwork.
 * They are used as accents, never as full-screen washes.
 */
object BrandColors {
    val Cyan = Color(0xFF4FD4FE)
    val Azure = Color(0xFF1CB3FE)
    val Blue = Color(0xFF018AFE)
    val Deep = Color(0xFF005AFD)
    val Navy = Color(0xFF00098E)

    val Primary = Color(0xFF0B6BE0)
    val PrimaryDark = Color(0xFF6FB0FF)
    val Rating = Color(0xFFF0A93B)
    val Ongoing = Color(0xFF2F9E6B)
    val Completed = Color(0xFF4E7396)
    val Hiatus = Color(0xFFB78633)
    val Cancelled = Color(0xFFB2564F)
}

/**
 * The visual identities offered in Settings → Appearance. Order is the order shown to the
 * user: the calm default first, then the themed accents. Every entry is a complete,
 * balanced palette — never a single accent applied over everything.
 */
enum class AppTheme(val storageKey: String) {
    DEFAULT("default"),
    SAKURA("sakura"),
    SHANA("shana"),
    MIKU("miku"),
    NEON("neon"),
    SKY("sky");

    companion object {
        fun fromStorage(value: String?): AppTheme =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}

/**
 * Light / dark / AMOLED choice, independent of [AppTheme] so any theme can be combined with
 * any mode. Stored under the legacy `dark_mode` key, whose old values ("light"/"dark")
 * already map onto the new entries, so upgrades keep the previous selection.
 */
enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled");

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK, AMOLED -> true
        SYSTEM -> systemDark
    }

    val isAmoled: Boolean get() = this == AMOLED

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}
