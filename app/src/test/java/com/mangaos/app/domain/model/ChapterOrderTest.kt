package com.mangaos.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterOrderTest {

    private fun chapter(number: String, id: String = number, publishedAt: Long? = null) = Chapter(
        id = id,
        mangaId = "m",
        number = number,
        publishedAt = publishedAt,
    )

    private fun numbersOf(chapters: List<Chapter>) = ChapterOrder.ascending(chapters).map { it.number }

    @Test
    fun `numbers are compared numerically not as text`() {
        val source = listOf(chapter("10"), chapter("2"), chapter("1"))
        assertEquals(listOf("1", "2", "10"), numbersOf(source))
    }

    @Test
    fun `newest first source becomes oldest first`() {
        val source = listOf(chapter("3"), chapter("2"), chapter("1"))
        assertEquals(listOf("1", "2", "3"), numbersOf(source))
    }

    @Test
    fun `decimal chapters sit between whole numbers`() {
        val source = listOf(chapter("11"), chapter("10.5"), chapter("10"))
        assertEquals(listOf("10", "10.5", "11"), numbersOf(source))
    }

    @Test
    fun `suffixed chapters stay together and ordered`() {
        val source = listOf(chapter("12"), chapter("12-2"), chapter("12-1"))
        assertEquals(listOf("12", "12-1", "12-2"), numbersOf(source))
    }

    @Test
    fun `labels with a prefix still parse their number`() {
        val source = listOf(chapter("Chapter 10"), chapter("Chapter 9"))
        assertEquals(listOf("Chapter 9", "Chapter 10"), numbersOf(source))
        assertEquals(10.0, ChapterOrder.numericValue("Chapter 10")!!, 0.0)
    }

    @Test
    fun `text only chapters follow numbered ones`() {
        val source = listOf(chapter("Extra"), chapter("5"))
        assertEquals(listOf("5", "Extra"), numbersOf(source))
    }

    @Test
    fun `text only chapters use their date`() {
        val source = listOf(
            chapter("Afterword", id = "b", publishedAt = 200L),
            chapter("Prologue", id = "a", publishedAt = 100L),
        )
        assertEquals(listOf("Prologue", "Afterword"), numbersOf(source))
    }

    @Test
    fun `unknown dates keep the source order`() {
        val source = listOf(chapter("Bonus", id = "b2"), chapter("Omake", id = "b1"))
        assertEquals(listOf("Bonus", "Omake"), numbersOf(source))
    }

    @Test
    fun `repeated numbers stay deterministic`() {
        val source = listOf(chapter("4", id = "z"), chapter("4", id = "a"))
        assertEquals(listOf("a", "z"), ChapterOrder.ascending(source).map { it.id })
    }

    @Test
    fun `previous and next neighbours are the same in both directions`() {
        val ordered = ChapterOrder.ascending(
            listOf(chapter("3"), chapter("1"), chapter("2")),
        )
        val index = ordered.indexOfFirst { it.number == "2" }
        assertEquals("1", ordered[index - 1].number)
        assertEquals("3", ordered[index + 1].number)
    }
}
