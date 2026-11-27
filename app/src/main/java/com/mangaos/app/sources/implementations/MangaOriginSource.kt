package com.mangaos.app.sources.implementations

import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.core.SourceChapter
import com.mangaos.app.sources.core.SourceException
import com.mangaos.app.sources.core.SourceManga
import com.mangaos.app.sources.core.SourcePage
import com.mangaos.app.sources.network.SourceHttp
import com.mangaos.app.sources.network.firstText
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.toInstantMillis
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

/**
 * Manga Origin, a Blogger site: the team blog posts a series page and the reader blog
 * (`mangaoriginread.blogspot.com`) posts the chapters under a label. Both are read through
 * Blogger's JSON feeds, so nothing depends on the blog's markup.
 *
 * The chapter label is discovered from the series post's own labels, then cached: a label that
 * the reader blog answers is the right one, and only a series with no chapters has none.
 */
class MangaOriginSource : MangaSource {

    override val id: String = "mangaorigin"
    override val name: String = "Manga Origin"
    override val lang: String = "ar"
    override val baseUrl: String = "https://teammangaorigin.blogspot.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    private val labelCache = ConcurrentHashMap<String, String>()

    override suspend fun popular(page: Int): List<SourceManga> = series(page, "")

    override suspend fun latest(page: Int): List<SourceManga> =
        throw SourceException(SourceFailure.UNSUPPORTED, "Manga Origin does not publish a latest feed")

    override suspend fun search(query: String, page: Int): List<SourceManga> = series(page, query.trim())

    /** Series posts are found by a sentence only they carry, plus the user's words. */
    private suspend fun series(page: Int, query: String): List<SourceManga> {
        val search = buildString {
            append("\"$SERIES_SENTENCE\"")
            if (query.isNotBlank()) append(" ").append(query)
        }
        val url = feed(baseUrl, "summary")
            .addQueryParameter("q", search)
            .addQueryParameter("start-index", ((page - 1) * PAGE_SIZE + 1).toString())
            .addQueryParameter("max-results", (PAGE_SIZE + 1).toString())
            .build()
        val entries = entries(http.json(url.toString()))
        return entries.take(PAGE_SIZE)
            .filter { it.isSeries() }
            .mapNotNull { it.toManga() }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val entry = entry(baseUrl, mangaId, "default") ?: return null
        return entry.toManga(details = true)
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val label = readerLabel(mangaId) ?: throw SourceException(
            SourceFailure.EMPTY,
            "This series has no chapters on the reader blog yet",
        )
        val found = ArrayList<SourceChapter>()
        var start = 1
        repeat(MAX_CHAPTER_PAGES) {
            val url = feed(READER, "summary")
                .addPathSegment("-")
                .addPathSegment(label)
                .addQueryParameter("start-index", start.toString())
                .addQueryParameter("max-results", CHAPTER_PAGE_SIZE.toString())
                .build()
            val batch = entries(http.json(url.toString()))
            batch.forEach { entry ->
                val path = entry.path ?: return@forEach
                found += SourceChapter(
                    id = path,
                    number = entry.chapterNumber(),
                    title = entry.title(),
                    publishedAt = entry.publishedAt(),
                )
            }
            if (batch.size < CHAPTER_PAGE_SIZE) return found
            start += CHAPTER_PAGE_SIZE
        }
        return found
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val entry = entry(READER, chapterId, "default")
            ?: throw SourceException(SourceFailure.PARSING, "This chapter is not on the reader blog")
        val html = entry.content ?: entry.summary
            ?: throw SourceException(SourceFailure.PARSING, "This chapter has no page")
        val images = Jsoup.parseBodyFragment(html, READER).select("img")
            .mapNotNull { image ->
                image.absUrl("src").takeIf { it.isNotBlank() }
            }
            .distinct()
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf(
        "Referer" to "$READER/",
    )

    override fun webUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl$mangaId"

    /** The reader label is one of the series post's labels; the reader blog answers only it. */
    private suspend fun readerLabel(seriesPath: String): String? {
        labelCache[seriesPath]?.let { return it }
        val entry = entry(baseUrl, seriesPath, "default") ?: return null
        for (label in entry.labels) {
            val url = feed(READER, "summary")
                .addPathSegment("-")
                .addPathSegment(label)
                .addQueryParameter("max-results", "1")
                .build()
            val found = runCatching { entries(http.json(url.toString())) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) {
                labelCache[seriesPath] = label
                return label
            }
        }
        return null
    }

    private suspend fun entry(blog: String, path: String, projection: String): BloggerEntry? {
        val url = feed(blog, projection)
            .addEncodedQueryParameter("path", path)
            .build()
        val payload = runCatching { http.json(url.toString()) }.getOrNull() ?: return null
        return entries(payload).firstOrNull()
    }

    private fun feed(blog: String, projection: String) = "$blog/feeds/posts/$projection"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("alt", "json")

    private fun entries(payload: kotlinx.serialization.json.JsonElement): List<BloggerEntry> {
        val feed = payload.objField("feed") ?: return emptyList()
        val raw = feed["entry"] ?: return emptyList()
        val array = when (raw) {
            is JsonArray -> raw
            is JsonObject -> JsonArray(listOf(raw))
            else -> return emptyList()
        }
        return array.mapNotNull { element -> (element as? JsonObject)?.toEntry() }
    }

    private fun JsonObject.toEntry(): BloggerEntry? {
        val title = objField("title")?.string("\$t") ?: return null
        val links = (this["link"] as? JsonArray).orEmpty()
        val path = links.mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("rel") == "alternate" }
            ?.string("href")
            ?.substringAfter("blogspot.com", "")
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val labels = (this["category"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.string("term")
        }
        return BloggerEntry(
            title = title,
            path = path,
            summary = objField("summary")?.string("\$t"),
            content = objField("content")?.string("\$t"),
            labels = labels,
            published = objField("published")?.string("\$t").toInstantMillis(),
        )
    }

    private fun kotlinx.serialization.json.JsonElement.objField(key: String): JsonObject? =
        (this as? JsonObject)?.get(key) as? JsonObject

    private fun BloggerEntry.toManga(details: Boolean = false): SourceManga? {
        val entryPath = this.path ?: return null
        val html = content ?: summary.orEmpty()
        val document = Jsoup.parseBodyFragment(html, baseUrl)
        val cover = document.select("img").firstOrNull()?.absUrl("src")?.takeIf { it.isNotBlank() }
        val description = document.firstText("p")?.takeIf { details }
        return SourceManga(
            id = entryPath,
            title = title,
            coverUrl = cover,
            url = "$baseUrl$entryPath",
            description = description,
            genres = labels,
            latestChapter = null,
            updatedAt = published,
        )
    }

    private fun BloggerEntry.chapterNumber(): String =
        Regex("""(?i)(?:chapter|فصل)\s*(\d+(?:\.\d+)*)""").find(title)?.groupValues?.get(1)
            ?: Regex("""\d+(?:\.\d+)*""").find(title)?.value
            ?: title

    private fun BloggerEntry.isSeries(): Boolean = (content ?: summary).orEmpty().contains(SERIES_SENTENCE)

    private data class BloggerEntry(
        val title: String,
        val path: String?,
        val summary: String?,
        val content: String?,
        val labels: List<String>,
        val published: Long?,
    ) {
        fun title(): String = title
        fun publishedAt(): Long? = published
    }

    private companion object {
        const val READER = "https://mangaoriginread.blogspot.com"
        const val SERIES_SENTENCE = "جميع الفصول"
        const val PAGE_SIZE = 25
        const val CHAPTER_PAGE_SIZE = 50
        const val MAX_CHAPTER_PAGES = 20
    }
}
