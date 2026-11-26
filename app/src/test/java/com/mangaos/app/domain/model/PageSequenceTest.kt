package com.mangaos.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSequenceTest {

    private data class Page(val chapter: String, val index: Int)

    private val order = listOf("c1", "c2", "c3", "c4", "c5")

    private fun pages(chapter: String, count: Int) = (0 until count).map { Page(chapter, it) }

    private fun insert(existing: List<Page>, incoming: List<Page>) = PageSequence.insertByChapter(
        existing = existing,
        incoming = incoming,
        chapterOrder = order,
        chapterIdOf = Page::chapter,
    )

    @Test
    fun `next chapter is appended after the current one`() {
        val result = insert(pages("c2", 2), pages("c3", 2))
        assertEquals(listOf("c2", "c2", "c3", "c3"), result.map { it.chapter })
    }

    @Test
    fun `previous chapter is inserted before the current one`() {
        val result = insert(pages("c3", 3), pages("c2", 2))
        assertEquals(listOf("c2", "c2", "c3", "c3", "c3"), result.map { it.chapter })
    }

    @Test
    fun `a gap is filled between two loaded chapters`() {
        val existing = pages("c1", 1) + pages("c3", 1)
        val result = insert(existing, pages("c2", 2))
        assertEquals(listOf("c1", "c2", "c2", "c3"), result.map { it.chapter })
    }

    @Test
    fun `page order inside a chapter is preserved`() {
        val result = insert(pages("c2", 1), pages("c1", 3))
        assertEquals(listOf(0, 1, 2), result.take(3).map { it.index })
    }

    @Test
    fun `a chapter is never inserted twice`() {
        val existing = pages("c2", 2)
        val result = insert(existing, pages("c2", 2))
        assertEquals(existing, result)
    }

    @Test
    fun `unknown chapters are ignored`() {
        val existing = pages("c2", 2)
        val result = insert(existing, pages("other", 2))
        assertEquals(existing, result)
    }

    @Test
    fun `backwards then forwards keeps one ordered sequence`() {
        var sequence = pages("c3", 2)
        sequence = insert(sequence, pages("c2", 2))
        sequence = insert(sequence, pages("c1", 2))
        sequence = insert(sequence, pages("c4", 2))
        assertEquals(
            listOf("c1", "c1", "c2", "c2", "c3", "c3", "c4", "c4"),
            sequence.map { it.chapter },
        )
    }
}
