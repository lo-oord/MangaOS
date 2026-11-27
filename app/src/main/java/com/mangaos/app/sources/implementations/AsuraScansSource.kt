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
import com.mangaos.app.sources.network.bool
import com.mangaos.app.sources.network.double
import com.mangaos.app.sources.network.int
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Asura Scans through the public JSON API that feeds asurascans.com (`api.asurascans.com`).
 *
 * The website is an Astro app whose props are the same payloads, but the API answers plain
 * requests with the listings, the details, the chapter list and the page images, so nothing
 * is scraped from the reader page. Premium chapters are hidden instead of being offered and
 * then failing on their gate.
 */
class AsuraScansSource : MangaSource {

    override val id: String = "asurascans"
    override val name: String = "Asura Scans"
    override val lang: String = "en"
    override val baseUrl: String = "https://asurascans.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(
        userAgent = SourceHttp.ANDROID_USER_AGENT,
        defaultHeaders = mapOf("Accept" to "application/json"),
    )

    override suspend fun popular(page: Int): List<SourceManga> = series(page, "popular")

    override suspend fun latest(page: Int): List<SourceManga> = series(page, "latest")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        return series(page, "popular", query.trim())
    }

    private suspend fun series(page: Int, order: String, query: String? = null): List<SourceManga> {
        val url = "$API/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1).coerceAtLeast(0) * PAGE_SIZE).toString())
            .addQueryParameter("order", order)
            .apply { if (query != null) addQueryParameter("search", query) }
            .build()
        val payload = http.json(url.toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "Asura list payload is not an object")
        return payload.array("data").orEmpty().mapNotNull { element ->
            element.obj()?.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val url = "$API/api/series/${mangaId.trim('/')}"
        val payload = http.json(url).obj() ?: return null
        // The API may answer with the series at the top level or nested under `series`.
        return (payload.obj("series") ?: payload).toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val slug = mangaId.trim('/')
        val payload = http.json("$API/api/series/$slug/chapters").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Asura chapter payload is not an object")
        return payload.array("data").orEmpty().mapNotNull { element ->
            val item = element.obj() ?: return@mapNotNull null
            val number = item.string("number") ?: return@mapNotNull null
            SourceChapter(
                // The API addresses a chapter by its number inside its series.
                id = number,
                number = number,
                title = item.string("title"),
                publishedAt = (item.string("published_at") ?: item.string("created_at")).toInstantMillis(),
            )
        }
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val slug = mangaId.trim('/')
        val payload = http.json("$API/api/series/$slug/chapters/$chapterId").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Asura page payload is not an object")
        val data = payload.obj("data")
            ?: throw SourceException(SourceFailure.PARSING, "Asura chapter payload is missing")
        val pages = data.obj("chapter")?.array("pages").orEmpty().mapNotNull { element ->
            element.obj()?.string("url")?.takeIf { it.startsWith("http") }
        }
        if (pages.isEmpty()) {
            val gate = data.string("access_gate")?.takeIf { it.isNotBlank() }
            throw SourceException(
                SourceFailure.EMPTY,
                gate ?: "This chapter has no pages",
            )
        }
        return pages.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/comics/${mangaId.trim('/')}"

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga()

    private fun JsonObject.toManga(): SourceManga? {
        val slug = string("slug") ?: return null
        val title = string("title") ?: return null
        return SourceManga(
            id = slug,
            title = title,
            coverUrl = string("cover") ?: string("cover_url"),
            url = "$baseUrl/comics/$slug",
            description = description(),
            genres = stringList("genres"),
            status = status(),
            rating = double("rating"),
            author = string("author"),
            artist = string("artist"),
            updatedAt = (string("last_chapter_at") ?: string("updated_at")).toInstantMillis(),
        )
    }

    /** The API returns HTML fragments in `description`; strip tags so the app shows text. */
    private fun JsonObject.description(): String? {
        val raw = string("description") ?: return null
        return raw.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun JsonObject.status(): MangaStatus = when (string("status")?.lowercase()) {
        "ongoing" -> MangaStatus.ONGOING
        "completed" -> MangaStatus.COMPLETED
        "hiatus" -> MangaStatus.HIATUS
        "dropped", "cancelled", "canceled" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private companion object {
        const val API = "https://api.asurascans.com"
        const val PAGE_SIZE = 20
    }
}
