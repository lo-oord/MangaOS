package com.mangaos.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaMetaTest {

    @Test
    fun `a real rating keeps the source value`() {
        assertEquals("8.5", ratingLabel(8.5))
        assertEquals("10.0", ratingLabel(10.0))
        assertEquals("0.7", ratingLabel(0.7))
    }

    @Test
    fun `missing or impossible ratings are not shown`() {
        assertNull(ratingLabel(null))
        assertNull(ratingLabel(0.0))
        assertNull(ratingLabel(-1.0))
        assertNull(ratingLabel(42.0))
        assertNull(ratingLabel(Double.NaN))
    }

    @Test
    fun `genres are trimmed deduplicated and capped`() {
        assertEquals(
            listOf("Action", "Romance"),
            displayGenres(listOf(" Action ", "action", "", "Romance", "Drama"), limit = 2),
        )
        assertEquals(emptyList<String>(), displayGenres(emptyList()))
        assertEquals(listOf("Drama"), displayGenres(listOf("Drama"), limit = 5))
    }
}
