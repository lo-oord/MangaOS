package com.mangaos.app.domain.model

import com.mangaos.app.core.util.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRulesTest {

    @Test
    fun `the same source id in two sources is two different works`() {
        val first = manga(sourceId = "source.one", id = "same")
        val second = manga(sourceId = "source.two", id = "same")

        assertEquals("source.one/same", first.key)
        assertEquals("source.two/same", second.key)
        assertTrue(first.key != second.key)
    }

    @Test
    fun `view mode defaults to grid and survives a storage round trip`() {
        assertEquals(MangaViewMode.GRID, MangaViewMode.fromStorage(null))
        assertEquals(MangaViewMode.GRID, MangaViewMode.fromStorage("grid"))
        assertEquals(MangaViewMode.LIST, MangaViewMode.fromStorage("list"))
        assertEquals(MangaViewMode.LIST, MangaViewMode.GRID.toggled())
        assertEquals(MangaViewMode.GRID, MangaViewMode.LIST.toggled())
    }

    @Test
    fun `the all filter lists every source language`() {
        assertTrue(LanguageFilter.ALL.matches(listOf("ar")))
        assertTrue(LanguageFilter.ALL.matches(listOf("en")))
        assertTrue(LanguageFilter.ALL.matches(listOf("ja")))
        assertTrue(LanguageFilter.ALL.matches(listOf("all")))
        assertTrue(LanguageFilter.ALL.matches(listOf("fr")))
        assertTrue(LanguageFilter.ALL.matches(listOf("pt-BR", "es")))
    }

    @Test
    fun `a language filter accepts multi language sources and its own language`() {
        assertTrue(LanguageFilter.ARABIC.matches(listOf("all")))
        assertTrue(LanguageFilter.ARABIC.matches(listOf("ar", "en")))
        assertFalse(LanguageFilter.ARABIC.matches(listOf("en")))
        assertTrue(LanguageFilter.JAPANESE.matches(listOf("ja")))
        assertTrue(LanguageFilter.ARABIC.matches(listOf("ar-EG")))
        assertFalse(LanguageFilter.JAPANESE.matches(listOf("en")))
    }

    @Test
    fun `minimum age matches the supported policy`() {
        assertEquals(13, Validators.MIN_AGE)
        assertTrue(Validators.isEmailValid("reader@example.com"))
        assertFalse(Validators.isPasswordValid("short"))
    }

    private fun manga(sourceId: String, id: String) = Manga(
        id = id,
        sourceId = sourceId,
        title = "Work",
    )
}
