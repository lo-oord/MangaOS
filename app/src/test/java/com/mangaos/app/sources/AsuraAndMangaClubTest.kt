package com.mangaos.app.sources

import com.mangaos.app.sources.implementations.AsuraScansSource
import com.mangaos.app.sources.implementations.MangaClubSource
import com.mangaos.app.sources.network.SourceHttp
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing of recorded site payloads for the HTML and JSON sources. No network here. */
class AsuraAndMangaClubTest {

    private val asura = AsuraScansSource()
    private val club = MangaClubSource()

    @Test
    fun `asura series object becomes a work`() {
        val json = SourceHttp.JSON.parseToJsonElement(ASURA_SERIES) as JsonObject
        val manga = asura.mapManga(json)
        assertNotNull(manga)
        assertEquals("lookism", manga!!.id)
        assertEquals("Lookism", manga.title)
        assertEquals("https://cdn.asurascans.com/asura-images/covers/lookism.46ee64.webp", manga.coverUrl)
        assertEquals("ongoing", manga.status.name.lowercase())
        assertTrue(manga.description!!.startsWith("Read the original webcomic"))
        assertTrue(manga.description!!.none { it == '<' })
        assertTrue((manga.updatedAt ?: 0L) > 1_700_000_000_000L)
    }

    @Test
    fun `mangaclub listing cards are parsed without duplicates`() {
        val document = Jsoup.parse(CLUB_LISTING, "https://www.mangaclub.net/en/?page=1")
        val works = club.parseCards(document)
        assertEquals(2, works.size)
        assertEquals("365-days-to-the-wedding", works[0].id)
        assertEquals("365 Days to the Wedding", works[0].title)
        assertEquals(
            "https://www.mangaclub.net/wp-content/uploads/image-3-5.webp",
            works[0].coverUrl,
        )
        assertEquals("blue-lock-episode-nagi", works[1].id)
    }

    @Test
    fun `mangaclub chapter numbers come from the heading or the url`() {
        val document = Jsoup.parse(CLUB_CHAPTERS, "https://www.mangaclub.net/en/manga/x.html")
        val items = document.select("article.chapter-item")
        assertEquals(2, items.size)
        assertEquals("110.5" to null, club.chapterNumber(items[0]))
        assertEquals("109" to null, club.chapterNumber(items[1]))
    }

    private companion object {
        const val ASURA_SERIES = """
        {
          "id": 6149,
          "slug": "lookism",
          "title": "Lookism",
          "description": "<p>Read the original webcomic behind Netflix's new animated series!</p>",
          "cover": "https://cdn.asurascans.com/asura-images/covers/lookism.46ee64.webp",
          "status": "ongoing",
          "type": "manhwa",
          "rating": 8.36,
          "chapter_count": 636,
          "last_chapter_at": "2026-11-26T16:54:43.900599Z",
          "updated_at": "2026-05-28T00:00:00Z"
        }
        """

        const val CLUB_LISTING = """
        <div class="series-grid">
          <div class="series-card">
            <a href="https://www.mangaclub.net/en/manga/365-days-to-the-wedding.html" class="series-card-link">
              <div class="series-card-thumb" style="background-image: url('https://www.mangaclub.net/wp-content/uploads/image-3-5.webp');">
                <div class="series-card-chapters"><i class="fas fa-book-open"></i> 109</div>
              </div>
              <div class="series-card-content"><h3 class="series-card-title">365 Days to the Wedding</h3></div>
            </a>
          </div>
          <div class="series-card">
            <a href="https://www.mangaclub.net/en/manga/blue-lock-episode-nagi.html" class="series-card-link">
              <div class="series-card-thumb" style="background-image: url('https://www.mangaclub.net/wp-content/uploads/blue.jpg');"></div>
              <div class="series-card-content"><h3 class="series-card-title">Blue Lock - Episode Nagi</h3></div>
            </a>
          </div>
        </div>
        """

        const val CLUB_CHAPTERS = """
        <div class="chapter-list-wrapper">
          <div class="chapters-list">
            <article class="chapter-item post-1">
              <a href="https://www.mangaclub.net/en/x/x-chapter-110-5.html" class="chapter-link">
                <div class="chapter-info"><h3 class="chapter-title">X, Chapter 110.5</h3></div>
              </a>
            </article>
            <article class="chapter-item post-2">
              <a href="https://www.mangaclub.net/en/x/x-chapter-109.html" class="chapter-link">
                <div class="chapter-info"><h3 class="chapter-title">X, Chapter 109</h3></div>
              </a>
            </article>
          </div>
        </div>
        """
    }
}
