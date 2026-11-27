package com.mangaos.app.sources

import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.sources.implementations.MangaDexSource
import com.mangaos.app.sources.network.SourceHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing of recorded MangaDex API objects. No network: the payloads below are trimmed copies
 * of real responses, so the mapping is checked against the API's actual shape.
 */
class MangaDexSourceTest {

    private val source = MangaDexSource("ar")

    @Test
    fun `maps a manga with its cover, genres and status`() {
        val manga = source.mapManga(json(MANGA_JSON))
        assertNotNull(manga)
        assertEquals("Kage no Jitsuryokusha ni Naritakute!", manga!!.title)
        assertEquals(
            "https://uploads.mangadex.org/covers/f9c1d1c6-2a4b-4c5f-9d4e-111111111111/cover.jpg.512.jpg",
            manga.coverUrl,
        )
        assertEquals(MangaStatus.ONGOING, manga.status)
        assertEquals(listOf("Action", "Fantasy"), manga.genres)
        assertTrue(manga.description!!.startsWith("Shadowbrokers"))
    }

    @Test
    fun `chapter mapping keeps external chapters out`() {
        val hosted = source.mapChapter(json(CHAPTER_JSON))
        assertNotNull(hosted)
        assertEquals("54", hosted!!.number)
        assertEquals(1789050400000L, hosted.publishedAt)

        assertNull(source.mapChapter(json(EXTERNAL_CHAPTER_JSON)))
    }

    private fun json(raw: String): JsonObject = SourceHttp.JSON.parseToJsonElement(raw) as JsonObject

    private companion object {
        const val MANGA_JSON = """
        {
          "id": "f9c1d1c6-2a4b-4c5f-9d4e-111111111111",
          "type": "manga",
          "attributes": {
            "title": {"en": "The Eminence in Shadow", "ar": "Kage no Jitsuryokusha ni Naritakute!"},
            "altTitles": [{"ja": "陰の実力者になりたくて！"}],
            "description": {"en": "Shadowbrokers are those who hide in the shadows."},
            "status": "ongoing",
            "lastChapter": "56",
            "updatedAt": "2026-11-20T10:00:00+00:00",
            "availableTranslatedLanguages": ["en", "ar"],
            "tags": [
              {"attributes": {"name": {"en": "Action"}}},
              {"attributes": {"name": {"en": "Fantasy"}}}
            ]
          },
          "relationships": [
            {"id": "author-1", "type": "author"},
            {"id": "cover-1", "type": "cover_art",
             "attributes": {"fileName": "cover.jpg"}}
          ]
        }
        """

        const val CHAPTER_JSON = """
        {
          "id": "chapter-1",
          "type": "chapter",
          "attributes": {
            "chapter": "54",
            "title": "The Shadow Garden",
            "translatedLanguage": "ar",
            "externalUrl": null,
            "pages": 35,
            "publishAt": "2026-09-10T14:26:40+00:00"
          }
        }
        """

        const val EXTERNAL_CHAPTER_JSON = """
        {
          "id": "chapter-2",
          "type": "chapter",
          "attributes": {
            "chapter": "0",
            "translatedLanguage": "en",
            "externalUrl": "https://example.com/official",
            "pages": 0
          }
        }
        """
    }
}
