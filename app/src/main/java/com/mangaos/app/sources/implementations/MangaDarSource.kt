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
import com.mangaos.app.sources.network.toInstantMillis
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
            updatedAt = document.metaContent("article:modified_time")?.toInstantOrNull()
                ?: document.metaContent("og:updated_time")?.toInstantOrNull(),
            latestChapter = CHAPTER_LABEL.find(document.text())?.groupValues?.getOrNull(1),
        )
    }

    /**
     * Three shapes have served this site's chapter list over time: an Alpine `rows:` array, a
     * JSON blob, and plain links. All three are read, in that order, so a redesign on the site
     * cannot leave a series with "no chapters" while its chapter links are still on the page.
     */
    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val document = http.document(mangaUrl(mangaId))
        embeddedRows(document)?.takeIf { it.isNotEmpty() }?.let { return it }
        jsonChapters(document, mangaId)?.takeIf { it.isNotEmpty() }?.let { return it }
        linkedChapters(document, mangaId)
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
        throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
    }

    /**
     * The Alpine payload has carried the list under `rows` and under `chapters` over time, and
     * it holds either compact `[id, number, path, seconds]` tuples or objects. Everything is
     * read, on every element carrying `x-data`, so a rename on the site cannot empty a series.
     */
    private fun embeddedRows(document: Document): List<SourceChapter>? {
        val blobs = document.select("[x-data]").map { it.attr("x-data") } +
            document.select("[data-rows]").map { it.attr("data-rows") }
        for (data in blobs) {
            if (data.isBlank()) continue
            val key = CHAPTER_KEYS.firstOrNull { data.contains(it) } ?: continue
            val keyStart = data.indexOf(key)
            val bracketStart = data.indexOf("[", keyStart)
            val bracketEnd = matchingBracket(data, bracketStart)
            if (bracketStart < 0 || bracketEnd < 0) continue
            val rows = runCatching {
                SourceHttp.JSON.parseToJsonElement(data.substring(bracketStart, bracketEnd + 1)) as JsonArray
            }.getOrNull() ?: continue
            val chapters = rows.mapNotNull { element -> rowToChapter(element) }
                .distinctBy { it.id }
            if (chapters.isNotEmpty()) return chapters
        }
        return null
    }

    private fun rowToChapter(element: kotlinx.serialization.json.JsonElement): SourceChapter? =
        when (element) {
            is JsonArray -> {
                if (element.size < 3) return null
                val number = (element[1] as? JsonPrimitive)?.content ?: return null
                val path = (element[2] as? JsonPrimitive)?.content ?: return null
                val seconds = (element.getOrNull(3) as? JsonPrimitive)?.content?.toLongOrNull()
                chapterOf(number, path, seconds?.takeIf { it > 0L }?.times(1000L))
            }
            is kotlinx.serialization.json.JsonObject -> {
                val number = (element["number"] as? JsonPrimitive)?.content
                    ?: (element["chapter_number"] as? JsonPrimitive)?.content
                    ?: (element["name"] as? JsonPrimitive)?.content
                    ?: return null
                val path = (element["url"] as? JsonPrimitive)?.content
                    ?: (element["link"] as? JsonPrimitive)?.content
                    ?: (element["slug"] as? JsonPrimitive)?.content
                    ?: (element["path"] as? JsonPrimitive)?.content
                    ?: return null
                val date = (element["created_at"] as? JsonPrimitive)?.content
                    ?: (element["createdAt"] as? JsonPrimitive)?.content
                    ?: (element["date"] as? JsonPrimitive)?.content
                    ?: (element["time"] as? JsonPrimitive)?.content
                chapterOf(number, path, date?.toInstantOrNull())
            }
            else -> null
        }

    private fun chapterOf(number: String, path: String, publishedAt: Long?): SourceChapter? {
        val clean = path.removePrefix(baseUrl).trim('/')
        if (clean.isBlank()) return null
        val label = number.removeSuffix(".0").ifBlank { number }
        return SourceChapter(
            id = clean,
            number = label,
            title = "الفصل $label",
            publishedAt = publishedAt,
        )
    }

    /** Any embedded JSON that carries an array of chapter objects (`number` + a link/id). */
    private fun jsonChapters(document: Document, mangaId: String): List<SourceChapter>? {
        val blobs = document.select("script").map { it.data() }
            .filter { it.contains("chapter", ignoreCase = true) } +
            document.select("[x-data]").map { it.attr("x-data") }
                .filter { it.contains("chapter", ignoreCase = true) }
        for (blob in blobs) {
            val root = runCatching { SourceHttp.JSON.parseToJsonElement(blob) }.getOrNull() ?: continue
            val found = mutableListOf<SourceChapter>()
            collectChapters(root, mangaId, found, depth = 0)
            found.distinctBy { it.id }.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private fun collectChapters(
        element: kotlinx.serialization.json.JsonElement,
        mangaId: String,
        into: MutableList<SourceChapter>,
        depth: Int,
    ) {
        if (depth > 8) return
        when (element) {
            is JsonArray -> element.forEach { collectChapters(it, mangaId, into, depth + 1) }
            is kotlinx.serialization.json.JsonObject -> {
                val number = (element["number"] as? JsonPrimitive)?.content
                    ?: (element["chapter_number"] as? JsonPrimitive)?.content
                val path = (element["slug"] as? JsonPrimitive)?.content
                    ?: (element["url"] as? JsonPrimitive)?.content
                    ?: (element["link"] as? JsonPrimitive)?.content
                    ?: (element["path"] as? JsonPrimitive)?.content
                if (number != null && !path.isNullOrBlank()) {
                    val id = path.removePrefix(baseUrl).trim('/')
                    if (id.isNotBlank()) {
                        into += SourceChapter(
                            id = id,
                            number = number,
                            title = "الفصل $number",
                            publishedAt = (element["created_at"] as? JsonPrimitive)?.content
                                ?.toInstantOrNull()
                                ?: (element["createdAt"] as? JsonPrimitive)?.content?.toInstantOrNull(),
                        )
                    }
                } else {
                    element.values.forEach { collectChapters(it, mangaId, into, depth + 1) }
                }
            }
            else -> Unit
        }
    }

    /** The last resort: the chapter links visible on the series page. */
    private fun linkedChapters(document: Document, mangaId: String): List<SourceChapter> {
        val slug = mangaId.trim('/').substringAfterLast("/")
        return document.select("a[href]")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                val looksLikeChapter = href.contains("/chapter", ignoreCase = true) ||
                    href.contains("الفصل") ||
                    (slug.isNotBlank() && href.contains(slug) &&
                        CHAPTER_NUMBER.containsMatchIn(href.substringAfter(slug)))
                if (!looksLikeChapter) return@mapNotNull null
                val text = anchor.text().trim()
                val number = CHAPTER_NUMBER.find(text)?.value
                    ?: CHAPTER_NUMBER.find(href.substringAfterLast("/"))?.value
                    ?: return@mapNotNull null
                val id = href.removePrefix(baseUrl).trim('/')
                if (id.isBlank()) return@mapNotNull null
                SourceChapter(id = id, number = number, title = "الفصل $number")
            }
            .distinctBy { it.id }
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = if (chapterId.startsWith("http")) chapterId else "$baseUrl/${chapterId.trim('/')}/"
        val document = http.document(url)
        val images = document
            .select(
                ".reader-page img, .reading-content img, .wp-manga-chapter-img, " +
                    ".chapter-images img, #images img, article img, main img, img[data-src], img[src]",
            )
            .mapNotNull { image ->
                val value = listOf("data-src", "data-lazy-src", "src")
                    .map { attribute -> image.absUrl(attribute) }
                    .firstOrNull { candidate ->
                        candidate.isNotBlank() && !candidate.startsWith("data:") &&
                            !LOGO_PATTERN.containsMatchIn(candidate)
                    }
                value
            }
            .distinct()
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = mangaUrl(mangaId)

    private fun mangaUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl/manga/${mangaId.trim('/')}"

    private fun String.toInstantOrNull(): Long? = this.toInstantMillis()

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

    private companion object {
        val CHAPTER_NUMBER = Regex("""(\d+(?:\.\d+)?)""")
        val CHAPTER_KEYS = listOf("chapters", "chapterList", "rows")
        val CHAPTER_LABEL = Regex("""(?:الفصل|chapter)\s*#?(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val LOGO_PATTERN = Regex("""(logo|favicon|avatar|banner|icon)""", RegexOption.IGNORE_CASE)
    }
}
