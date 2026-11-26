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

/** The four visual identities offered in Settings → Theme. */
enum class AppTheme(val storageKey: String) {
    DEFAULT("default"),
    MIKU("miku"),
    NEON("neon"),
    SKY("sky");

    companion object {
        fun fromStorage(value: String?): AppTheme =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}
