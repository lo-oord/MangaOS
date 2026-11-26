package com.mangaos.app.domain.model

/**
 * Pure ordering rules for the continuous reader's single page sequence.
 *
 * Pages from a chapter always stay together and always sit where that chapter belongs in the
 * reading order, whether the chapter was appended at the end or inserted above the content the
 * user is reading. Duplicated chapters are ignored, and a chapter that is not part of the
 * current chapter list is never inserted.
 */
object PageSequence {

    fun <T> insertByChapter(
        existing: List<T>,
        incoming: List<T>,
        chapterOrder: List<String>,
        chapterIdOf: (T) -> String,
    ): List<T> {
        if (incoming.isEmpty()) return existing
        val targetChapter = chapterIdOf(incoming.first())
        val targetIndex = chapterOrder.indexOf(targetChapter)
        if (targetIndex < 0) return existing
        if (existing.any { chapterIdOf(it) == targetChapter }) return existing

        val orderIndex = chapterOrder.withIndex().associate { (index, id) -> id to index }
        val insertAt = existing.indexOfFirst { item ->
            (orderIndex[chapterIdOf(item)] ?: Int.MAX_VALUE) > targetIndex
        }
        return if (insertAt < 0) {
            existing + incoming
        } else {
            existing.subList(0, insertAt) + incoming + existing.subList(insertAt, existing.size)
        }
    }
}
