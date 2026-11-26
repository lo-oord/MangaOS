package com.mangaos.app.domain.model

/**
 * Pure helpers for the periodic new-chapter check. Kept free of Android types so the baseline
 * rules can be unit tested without a device.
 */

/** Numeric value of a chapter label; sources may return "12", "12.5" or something odd. */
fun chapterNumberValue(number: String?): Float? =
    number?.trim()?.replace(',', '.')?.toFloatOrNull()

/** Highest chapter of a source list, ignoring non numeric labels. */
fun latestChapterOf(chapters: List<Chapter>): Chapter? = chapters
    .filter { chapterNumberValue(it.number) != null }
    .maxWithOrNull(
        compareBy<Chapter> { chapterNumberValue(it.number) ?: 0f }
            .thenBy { it.publishedAt ?: 0L }
            .thenBy { it.id },
    )

fun latestChapterNumber(chapters: List<Chapter>): Float? =
    chapters.mapNotNull { chapterNumberValue(it.number) }.maxOrNull()

/**
 * Chapters that appeared after the stored baseline.
 *
 * [knownIds] is null when no baseline exists yet: the first sync is only a baseline and must
 * never notify, otherwise installing the app would announce every chapter in the library.
 * A chapter is new when its id was never seen and it is not older than the last known number.
 */
fun detectNewChapters(
    chapters: List<Chapter>,
    knownIds: Set<String>?,
    baselineNumber: Float?,
): List<Chapter> {
    if (knownIds == null) return emptyList()
    return chapters
        .filter { it.id.isNotBlank() && it.id !in knownIds }
        .filter { chapter ->
            val value = chapterNumberValue(chapter.number)
            baselineNumber == null || value == null || value >= baselineNumber
        }
        .sortedWith(
            compareBy<Chapter> { chapterNumberValue(it.number) ?: -1f }
                .thenBy { it.publishedAt ?: 0L }
                .thenBy { it.id },
        )
}

/** Chapter ids worth remembering for de-duplication; bounded so the record cannot grow forever. */
fun baselineChapterIds(
    chapters: List<Chapter>,
    previous: Set<String> = emptySet(),
): Set<String> {
    val merged = LinkedHashSet(previous)
    chapters.sortedByDescending { it.publishedAt ?: 0L }.forEach { merged += it.id }
    return if (merged.size <= MAX_KNOWN_CHAPTERS) {
        merged
    } else {
        merged.toList().takeLast(MAX_KNOWN_CHAPTERS).toSet()
    }
}

private const val MAX_KNOWN_CHAPTERS = 300
