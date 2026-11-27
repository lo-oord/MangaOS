package com.mangaos.app.sources.network

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date parsing shared by the sources. Every source here publishes ISO-8601 timestamps or plain
 * dates; nothing is guessed from the moment the request was made, so a chapter never shows the
 * time it was fetched as its publication date.
 */

fun String?.toInstantMillis(): Long? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    // A bare date ("2024-03-01") is midnight UTC, never "now".
    runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()?.let { return it.toEpochMilli() }
    runCatching { Instant.parse(value) }.getOrNull()?.let { return it.toEpochMilli() }
    runCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
        .getOrNull()?.let { return it.toEpochMilli() }
    runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() }
        .getOrNull()?.let { return it.toEpochMilli() }
    // Some APIs return "2024-03-01 10:20:30".
    runCatching { LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }
        .getOrNull()?.let { return it.toEpochMilli() }
    return null
}

fun Long?.epochMillis(): Long? = this?.takeIf { it > 0L }

/** Unix seconds or milliseconds, whichever the site used. */
fun String?.toEpochFromSecondsOrMillis(): Long? {
    val seconds = this?.trim()?.toLongOrNull() ?: return null
    if (seconds <= 0L) return null
    return if (seconds < 100_000_000_000L) seconds * 1000L else seconds
}

private val ENGLISH_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
private val SHORT_ENGLISH_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

/** "March 3, 2024" / "Mar 3, 2024" as used by the WordPress themes. */
fun String?.parseEnglishDate(): Long? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    toInstantMillis()?.let { return it }
    runCatching { LocalDate.parse(value, ENGLISH_DATE) }
        .getOrNull()?.let { return it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    runCatching { LocalDate.parse(value, SHORT_ENGLISH_DATE) }
        .getOrNull()?.let { return it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    return null
}

/** Start of the current day in the device zone, for a source that only publishes a date. */
fun LocalDate.toEpochMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
