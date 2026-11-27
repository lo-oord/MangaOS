package com.mangaos.app.sources.implementations

import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.core.SourceChapter
import com.mangaos.app.sources.core.SourceException
import com.mangaos.app.sources.core.SourceFailure
import com.mangaos.app.sources.core.SourceManga
import com.mangaos.app.sources.core.SourcePage
import com.mangaos.app.sources.network.SourceHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * MangaClub is a plain server-rendered site, so this source parses HTML with Jsoup. It has no
 * chapter-update feed, therefore it declares POPULAR and SEARCH only instead of pretending to
 * provide latest updates.
 *
 * Work ids are the site slugs; chapter ids are the chapter URL path, so the chapter page can be
 * requested again without keeping a second index.
 */
class MangaClubSource : MangaSource {

    override val id: String = "mangaclub"
    override val name: String = "Manga Club"
    override val lang: String = "en"
    override val baseUrl: String = "https://www.mangaclub.net"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> =
        parseCards(fetch("$baseUrl/en/?page=$page"))

    override suspend fun latest(page: Int): List<SourceManga> =
        throw SourceException(SourceFailure.UNSUPPORTED, "Manga Club has no update feed")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        val url = "$baseUrl/en/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return parseCards(fetch(url.toString()))
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val document = fetch("$baseUrl/en/manga/$mangaId.html")
        val title = document.selectFirst("h1.series-title")?.text()?.trim()
            ?: return null
        val description = document.selectFirst(".series-description")?.text()?.trim()
        val cover = document.selectFirst(".series-header-thumbnail img")
            ?.let { it.attr("abs:src").ifBlank { it.attr("src") } }
        val genres = document.select("a[href*=/genre/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("all", true) }
            .distinct()
        val statusText = document.selectFirst(".series-status, .series-meta .status")?.text()
        val chapters = document.select("article.chapter-item")
        return SourceManga(
            id = mangaId,
            title = title,
            coverUrl = cover,
            url = "$baseUrl/en/manga/$mangaId.html",
            description = description,
            genres = genres,
            status = statusText.toStatus(),
            latestChapter = chapters.firstOrNull()?.let { chapterNumber(it)?.first },
            latestChapterId = chapters.firstOrNull()?.let { chapterId(it) },
        )
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val document = fetch("$baseUrl/en/manga/$mangaId.html")
        return document.select("article.chapter-item").mapNotNull { item ->
            val id = chapterId(item) ?: return@mapNotNull null
            val (number, title) = chapterNumber(item) ?: return@mapNotNull null
            SourceChapter(id = id, number = number, title = title)
        }
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val document = fetch("$baseUrl/en/$chapterId.html")
        val images = document.select("img")
            .mapNotNull { image -> imageUrl(image) }
            .filter { "/cdn/" in it }
            .distinct()
        if (images.isEmpty()) {
            throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        }
        return images.map(::SourcePage)
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/en/manga/$mangaId.html"

    private suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        try {
            Jsoup.parse(http.text(url), url)
        } catch (error: SourceException) {
            throw error
        } catch (error: Exception) {
            throw SourceException(SourceFailure.PARSING, "Could not parse $url", error)
        }
    }

    internal fun parseCards(document: Document): List<SourceManga> {
        val cards = document.select("div.series-card").mapNotNull { card ->
            val link = card.selectFirst("a.series-card-link[href]") ?: return@mapNotNull null
            val id = slugOf(link.attr("href")) ?: return@mapNotNull null
            val title = card.selectFirst(".series-card-title")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val cover = card.selectFirst(".series-card-thumb")
                ?.attr("style")
                ?.let(COVER_PATTERN::find)
                ?.groupValues
                ?.getOrNull(1)
            SourceManga(
                id = id,
                title = title,
                coverUrl = cover,
                url = "$baseUrl/en/manga/$id.html",
            )
        }
        if (cards.isNotEmpty()) return cards.distinctBy { it.id }

        // Search results use the same card markup in some layouts and a plain list in others.
        return document.select("a[href*=/en/manga/]").mapNotNull { link ->
            val id = slugOf(link.attr("href")) ?: return@mapNotNull null
            val title = link.selectFirst(".series-card-title")?.text()?.trim()
                ?: link.text().trim().takeIf { it.isNotBlank() && it.length > 1 }
                ?: return@mapNotNull null
            SourceManga(id = id, title = title, url = "$baseUrl/en/manga/$id.html")
        }.distinctBy { it.id }
    }

    private fun imageUrl(image: Element): String? {
        val candidate = image.attr("abs:data-src").ifBlank { image.attr("abs:src") }
            .ifBlank { image.attr("data-src") }
            .ifBlank { image.attr("src") }
            .trim()
        if (candidate.isBlank()) return null
        return when {
            candidate.startsWith("http") -> candidate
            candidate.startsWith("//") -> "https:$candidate"
            candidate.startsWith("/") -> baseUrl + candidate
            else -> return null
        }
    }

    private fun chapterId(item: Element): String? {
        val href = item.selectFirst("a.chapter-link[href]")?.attr("href") ?: return null
        val path = href.substringAfter("$baseUrl/en/", href)
            .removeSuffix(".html")
            .removeSuffix("/")
            .trim()
        return path.takeIf { it.isNotBlank() && "chapter" in it }
    }

    /** @return number and optional subtitle, taken from the chapter heading or the URL. */
    internal fun chapterNumber(item: Element): Pair<String, String?>? {
        val heading = item.selectFirst(".chapter-title")?.text()?.trim()
        val fromHeading = heading?.let(NUMBER_PATTERN::find)?.groupValues?.getOrNull(1)
        val fromId = item.selectFirst("a.chapter-link[href]")?.attr("href")
            ?.let(URL_NUMBER_PATTERN::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace('-', '.')
        val number = fromHeading ?: fromId ?: return null
        val title = heading?.substringAfter("Chapter", "")?.trim()
            ?.trimStart(':', '.', '-', ' ')
            ?.removePrefix(number)
            ?.trim()
            ?.trimStart(':', '.', '-', ' ')
            ?.takeIf { it.isNotBlank() }
        return number to title
    }

    private fun slugOf(href: String): String? = href
        .substringAfter("/en/manga/", "")
        .substringBefore('?')
        .substringBefore('#')
        .removeSuffix(".html")
        .trim('/')
        .takeIf { it.isNotBlank() && "/" !in it }

    private fun String?.toStatus(): MangaStatus = when {
        this == null -> MangaStatus.UNKNOWN
        contains("ongoing", true) -> MangaStatus.ONGOING
        contains("completed", true) -> MangaStatus.COMPLETED
        contains("hiatus", true) -> MangaStatus.HIATUS
        contains("cancel", true) || contains("dropped", true) -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private companion object {
        val COVER_PATTERN = Regex("""url\(['"]?([^'")]+)['"]?\)""")
        val NUMBER_PATTERN = Regex("""[Cc]hapter\s+([0-9]+(?:\.[0-9]+)?)""")
        val URL_NUMBER_PATTERN = Regex("""-chapter-([0-9]+(?:-[0-9]+)?)\.html""")
    }
}
