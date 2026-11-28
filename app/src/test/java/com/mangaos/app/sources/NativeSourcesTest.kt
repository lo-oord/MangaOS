package com.mangaos.app.sources

import com.mangaos.app.sources.implementations.MangaTekSource
import com.mangaos.app.sources.implementations.mangatek.MangaTekOverlay
import com.mangaos.app.sources.network.parseEnglishDate
import com.mangaos.app.sources.network.toEpochFromSecondsOrMillis
import com.mangaos.app.sources.network.toInstantMillis
import com.mangaos.app.sources.registry.NativeSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pieces of the bundled sources that can be checked without a device or a network. */
class NativeSourcesTest {

    @Test
    fun `iso timestamps are parsed, bare dates are not turned into now`() {
        assertEquals(1700000000000L, "2023-11-14T22:13:20Z".toInstantMillis())
        assertEquals(1700000000000L, "2023-11-14T22:13:20.000Z".toInstantMillis())
        assertEquals(1700000000000L, "2023-11-14T22:13:20+00:00".toInstantMillis())
        assertEquals(1704067200000L, "2024-01-01".toInstantMillis())
        assertNull("".toInstantMillis())
        assertNull("not a date".toInstantMillis())
    }

    @Test
    fun `english chapter dates and unix timestamps are read as published`() {
        assertNotNull("November 24, 2026".parseEnglishDate())
        assertNull("Chapter 12".parseEnglishDate())
        assertEquals(1660680000000L, "1660680000".toEpochFromSecondsOrMillis())
        assertEquals(1660680000000L, "1660680000000".toEpochFromSecondsOrMillis())
        assertNull("0".toEpochFromSecondsOrMillis())
    }

    @Test
    fun `mangatek overlays keep every bubble of every page`() {
        val payload = """
            {"pages":[
              {"page_number":1,"overlays":[
                 {"text":"مرحبا","x":10,"y":20,"w":100,"h":50,"angle":0,
                  "font_size_px":18,"line_height":1.2,"stroke_width_px":2},
                 {"text":"عالم","x":30,"y":80,"w":90,"h":40}
              ]},
              {"page_number":3,"overlays":[{"text":"الثالثة","x":1,"y":2,"w":3,"h":4}]}
            ]}
        """.trimIndent()
        val pages = MangaTekOverlay.parsePages(payload)
        assertEquals(2, pages.size)
        assertEquals(1, pages[0].pageNumber)
        assertEquals(2, pages[0].bubbles.size)
        assertEquals("مرحبا", pages[0].bubbles[0].text)
        assertEquals(18f, pages[0].bubbles[0].fontSizePx, 0.001f)
        assertEquals(3, pages[1].pageNumber)
        assertEquals(1, pages[1].bubbles.size)
    }

    @Test
    fun `a page's bubbles survive the encoding the reader and downloads share`() {
        val pages = MangaTekOverlay.parsePages(
            """{"pages":[{"page_number":1,"overlays":[{"text":"نص \"مقتبس\"","x":1,"y":2,"w":3,"h":4,"angle":5}]}]}""",
        )
        val encoded = MangaTekOverlay.encodeBubbles(pages.single().bubbles)
        val decoded = MangaTekOverlay.decodeBubbles(encoded)
        assertEquals(1, decoded.size)
        assertEquals("نص \"مقتبس\"", decoded[0].text)
        assertEquals(5f, decoded[0].angle, 0.001f)
    }

    @Test
    fun `exactly one primary and one backup server ship in the app`() {
        val definitions = NativeSourceRegistry.definitions
        assertEquals(2, definitions.size)
        assertEquals(listOf("mangatek", "teamx"), definitions.map { it.id })
        definitions.forEach { definition ->
            val source = definition.factory()
            assertEquals(definition.id, source.id)
            assertEquals(definition.name, source.name)
            assertEquals(definition.lang, source.lang)
            assertEquals(definition.baseUrl, source.baseUrl)
            assertTrue(
                "${definition.id} must declare at least search",
                definition.capabilities.isNotEmpty(),
            )
            assertNull("${definition.id} must not ship brand artwork", definition.iconRes)
        }
    }

    @Test
    fun `the registry ships Arabic servers only and never two of the same id`() {
        val definitions = NativeSourceRegistry.definitions
        assertTrue(definitions.all { it.lang == "ar" })
        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertTrue(definitions.none { it.id == "thunderscans" || it.id == "asurascans" })
        assertTrue(NativeSourceRegistry.definition("mangatek")!!.factory() is MangaTekSource)
    }
}
