package com.mangaos.app.core.util

/** Stable, locale-independent measurement text for download rows. */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val megabytes = bytes / 1_048_576.0
    return if (megabytes >= 1024) {
        "${Numbers.decimal(megabytes / 1024)} GB"
    } else {
        "${Numbers.decimal(megabytes)} MB"
    }
}

fun formatPercent(progress: Float): String = Numbers.percent(progress)
