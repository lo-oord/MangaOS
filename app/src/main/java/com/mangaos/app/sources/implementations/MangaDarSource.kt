package com.mangaos.app.sources.implementations

import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.core.SourceChapter
import com.mangaos.app.sources.core.SourceException
import com.mangaos.app.sources.core.SourceManga
import com.mangaos.app.sources.core.SourcePage
import com.mangaos.app.sources.network.SourceHttp
import com.mangaos.app.sources.network.array
import com.mangaos.app.sources.network.coverImage
import com.mangaos.app.sources.network.firstText
import com.mangaos.app.sources.network.metaContent
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/**
 * MangaDar (mangadar.com). The catalogue is HTML; the site's own search is the WordPress
 * AJAX action `mangaverse_search`, which answers JSON. The chapter list is embedded in the
 * series page as a compact `rows:` array inside an Alpine `x-data` attribute.
 */
class MangaDarSource : MangaSource {

    override val id: String = "mangadar"
    override val name: String = "Manga Dar"
    override val lang: String = "ar"
    override val baseUrl: String = "https://mangadar.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> =
        cards(http.document("$baseUrl/manga/page/$page/"))

    override suspend fun latest(page: Int): List<SourceManga> {
        val path = if (page > 1) "manga/page/$page/" else "manga/"
        return cards(http.document("$baseUrl/$path?sort=new"))
    }

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "mangaverse_search")
            .addQueryParameter("q", query.trim())
            .build()
        val payload = http.json(url.toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaDar search payload is missing")
        return payload.array("data").orEmpty().mapNotNull { element ->
            val item = element.obj() ?: return@mapNotNull null
            val href = item.string("url") ?: return@mapNotNull null
            val slug = href.trim('/').substringAfterLast("/")
            val title = item.string("title") ?: return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = item.string("cover"),
                url = href,
            )
        }
    }

    private fun cards(document: Document): List<SourceManga> =
        document.select("a[href*=/manga/]").mapNotNull { element ->
            val href = element.absUrl("href").ifBlank { element.attr("href") }
            val segments = href.trim('/').split("/")
            // A card links to /manga/<slug>; pagination links are skipped.
            if (segments.size < 2 || segments[segments.size - 2] != "manga") return@mapNotNull null
            val slug = segments.last()
            if (slug.isBlank() || slug == "page") return@mapNotNull null
            val image = element.selectFirst("img")
            val title = image?.attr("alt")?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: element.firstText("h3", "h4", ".font-semibold")
                ?: return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = image?.coverImage(),
                url = "$baseUrl/manga/$slug",
            )
        }.distinctBy { it.id }

    override suspend fun details(mangaId: String): SourceManga? {
        val document = http.document(mangaUrl(mangaId))
        val title = document.firstText("h1") ?: return null
        var author: String? = null
        var artist: String? = null
        document.select("aside div:has(> span)").forEach { row ->
            val spans = row.select("span")
            if (spans.size >= 2) {
                val label = spans[0].text()
                val value = spans[1].text().trim()
                when {
                    label.contains("المؤلف") -> author = value.takeIf { it.isNotBlank() }
                    label.contains("الرسام") -> artist = value.takeIf { it.isNotBlank() }
                }
            }
        }
        val text = document.text()
        return SourceManga(
            id = mangaId.trim('/'),
            title = title,
            coverUrl = document.metaContent("og:image") ?: document.coverImage(),
            url = mangaUrl(mangaId),
            description = document.firstText("p.text-neutral-400")
                ?: document.metaContent("description"),
            genres = document.select("a[href*=/genre/]").map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            status = when {
                text.contains("مكتمل") -> MangaStatus.COMPLETED
                text.contains("مستمر") -> MangaStatus.ONGOING
                text.contains("متوقف") -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            author = author,
            artist = artist,
        )
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val document = http.document(mangaUrl(mangaId))
        for (container in document.select("div[x-data]")) {
            val data = container.attr("x-data")
            val rowsStart = data.indexOf("rows:")
            if (rowsStart < 0) continue
            val bracketStart = data.indexOf("[", rowsStart)
            val bracketEnd = matchingBracket(data, bracketStart)
            if (bracketStart < 0 || bracketEnd < 0) continue
            val rows = runCatching {
                SourceHttp.JSON.parseToJsonElement(data.substring(bracketStart, bracketEnd + 1)) as JsonArray
            }.getOrNull() ?: continue
            val chapters = rows.mapNotNull { element ->
                val row = element as? JsonArray ?: return@mapNotNull null
                if (row.size < 4) return@mapNotNull null
                val number = (row[1] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val path = (row[2] as? JsonPrimitive)?.content ?: return@mapNotNull null
                val seconds = (row[3] as? JsonPrimitive)?.content?.toLongOrNull()
                if (path.isBlank()) return@mapNotNull null
                SourceChapter(
                    id = path.removePrefix(baseUrl).trim('/'),
                    number = number,
                    title = "الفصل $number",
                    publishedAt = seconds?.takeIf { it > 0L }?.times(1000L),
                )
            }
            if (chapters.isNotEmpty()) return chapters
        }
        throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = if (chapterId.startsWith("http")) chapterId else "$baseUrl/${chapterId.trim('/')}/"
        val document = http.document(url)
        val images = document.select(".reader-page img").mapNotNull { image ->
            val value = when {
                image.attr("src").isNotBlank() -> image.absUrl("src")
                else -> image.absUrl("data-src")
            }
            value.takeIf { it.isNotBlank() }
        }
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = mangaUrl(mangaId)

    private fun mangaUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl/manga/${mangaId.trim('/')}"

    private fun matchingBracket(text: String, start: Int): Int {
        if (start < 0 || start >= text.length) return -1
        var depth = 0
        for (index in start until text.length) {
            when (text[index]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }
}
