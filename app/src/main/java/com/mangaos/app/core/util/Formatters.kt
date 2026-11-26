package com.mangaos.app.core.util

import java.util.Locale

/** Stable, locale-independent measurement text for download rows. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val megabytes = bytes / 1_048_576.0
    return if (megabytes >= 1024) {
        String.format(Locale.US, "%.1f GB", megabytes / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", megabytes)
    }
}

fun formatPercent(progress: Float): String =
    String.format(Locale.US, "%d%%", (progress.coerceIn(0f, 1f) * 100).toInt())
