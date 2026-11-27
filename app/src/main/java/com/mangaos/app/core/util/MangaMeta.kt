package com.mangaos.app.core.util

import java.util.Locale

/**
 * Presentation rules for the facts a source may or may not provide. Nothing here invents data:
 * a missing value stays missing and the layouts drop the element instead of showing a placeholder.
 */

/**
 * MangaOS sources report a rating on a 0..10 scale. Values outside that range, NaN or a zero
 * (which every source uses for "not rated") are treated as missing instead of being rescaled or
 * shown as a fake zero, so the number on screen is always the source's own value.
 */
fun normalizedRating(rating: Double?): Double? =
    rating?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }

/** One decimal, western digits, locale-independent: the same work reads the same on every device. */
fun ratingLabel(rating: Double?): String? =
    normalizedRating(rating)?.let { String.format(Locale.US, "%.1f", it) }

/**
 * Genres are trimmed, de-duplicated case-insensitively and capped, so a source that repeats a
 * tag or returns twenty of them cannot push the title out of the card.
 */
fun displayGenres(genres: List<String>, limit: Int = 2): List<String> =
    genres.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(limit.coerceAtLeast(0))
        .toList()
