package com.mangaos.app.domain.model

/**
 * One canonical reading order for a work, independent of how a source happens to list its
 * chapters. Forward and backward navigation must agree on this order, otherwise "next" and
 * "previous" point at different neighbours.
 *
 * Rules, in order:
 * 1. Chapters with a number are ordered numerically (2 before 10, 10.5 between them), so a
 *    source that returns newest-first still reads oldest-first.
 * 2. Equal or suffixed numbers ("12", "12.5", "12-2") are ordered by the remaining text, which
 *    keeps specials next to the chapter they belong to.
 * 3. Text-only chapters ("Extra", "Oneshot") follow the numbered ones and fall back to the
 *    publication date; when both are unknown the source order is kept (the sort is stable).
 */
object ChapterOrder {

    fun ascending(chapters: List<Chapter>): List<Chapter> = chapters.sortedWith(comparator)

    private val comparator = Comparator<Chapter> { left, right ->
        val leftNumber = numericValue(left.number)
        val rightNumber = numericValue(right.number)
        when {
            leftNumber != null && rightNumber != null -> {
                val byNumber = leftNumber.compareTo(rightNumber)
                if (byNumber != 0) byNumber else compareLabels(left, right)
            }
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> compareDated(left, right)
        }
    }

    /** First decimal number anywhere in the label: "Ch. 12", "10.5", "3-2" → 12, 10.5, 3. */
    fun numericValue(label: String): Double? {
        val match = NUMBER.find(label) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }

    private fun compareLabels(left: Chapter, right: Chapter): Int {
        val byLabel = left.number.trim().lowercase().compareTo(right.number.trim().lowercase())
        if (byLabel != 0) return byLabel
        // A work can repeat a number across scanlators; the id keeps the order deterministic.
        return left.id.compareTo(right.id)
    }

    private fun compareDated(left: Chapter, right: Chapter): Int {
        val leftDate = left.publishedAt
        val rightDate = right.publishedAt
        return when {
            leftDate != null && rightDate != null -> leftDate.compareTo(rightDate)
            leftDate != null -> -1
            rightDate != null -> 1
            else -> 0
        }
    }

    private val NUMBER = Regex("""\d+(?:[.,]\d+)?""")
}
