package com.mangaos.app.sources.implementations

import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.core.SourceChapter
import com.mangaos.app.sources.core.SourceException
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.sources.core.SourceManga
import com.mangaos.app.sources.core.SourcePage
import com.mangaos.app.sources.network.SourceHttp
import com.mangaos.app.sources.network.array
import com.mangaos.app.sources.network.double
import com.mangaos.app.sources.network.long
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Asura Scans through its public JSON API (`api.asurascans.com`). The website itself is behind
 * a browser challenge, but the API that feeds it answers plain requests and exposes every list
 * this app needs, so the source never scrapes the reader page.
 *
 * Works: latest updates, popular listing, search, details, chapter list and page images.
 */
class AsuraScansSource : MangaSource {

    override val id: String = "asura"
    override val name: String = "Asura Scans"
    override val lang: String = "en"
    override val baseUrl: String = "https://asurascans.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> =
        seriesList("$API/api/series?page=$page")

    override suspend fun latest(page: Int): List<SourceManga> =
        seriesList("$API/api/series?page=$page&order=updated")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        val url = "$API/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return seriesList(url.toString())
    }

    private suspend fun seriesList(url: String): List<SourceManga> {
        val payload = http.json(url).obj()
            ?: throw SourceException(SourceFailure.PARSING, "Asura list payload is not an object")
        return payload.array("data").orEmpty().mapNotNull { element ->
            element.obj()?.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val payload = http.json("$API/api/series/$mangaId").obj() ?: return null
        val series = payload.obj("series") ?: payload
        return series.toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val payload = http.json("$API/api/series/$mangaId/chapters").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Asura chapter payload is not an object")
        return payload.array("data").orEmpty().mapNotNull { element ->
            val item = element.obj() ?: return@mapNotNull null
            val number = item.string("number") ?: return@mapNotNull null
            SourceChapter(
                id = number,
                number = number,
                title = item.string("title"),
                publishedAt = item.string("published_at").toIsoInstant(),
            )
        }
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val payload = http.json("$API/api/series/$mangaId/chapters/$chapterId").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Asura page payload is not an object")
        val chapter = payload.obj("data")?.obj("chapter")
            ?: throw SourceException(SourceFailure.PARSING, "Asura chapter payload is missing")
        val pages = chapter.array("pages").orEmpty().mapNotNull { element ->
            element.obj()?.string("url")?.takeIf { it.startsWith("http") }
        }
        if (pages.isEmpty()) {
            val gate = payload.obj("data")?.string("access_gate")
            throw SourceException(
                SourceFailure.EMPTY,
                gate?.takeIf { it.isNotBlank() } ?: "This chapter has no pages",
            )
        }
        return pages.map(::SourcePage)
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/comics/$mangaId"

    /** Exposed for unit tests: parsing a recorded API object, without any network call. */
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
            status = string("status").toStatus(),
            rating = double("rating"),
            updatedAt = (string("last_chapter_at") ?: string("updated_at")).toIsoInstant(),
        )
    }

    /** The API returns HTML fragments in `description`; strip tags so the app shows text. */
    private fun JsonObject.description(): String? {
        val raw = string("description") ?: return null
        return raw.replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String?.toStatus(): MangaStatus = when (this?.lowercase()) {
        "ongoing" -> MangaStatus.ONGOING
        "completed" -> MangaStatus.COMPLETED
        "hiatus" -> MangaStatus.HIATUS
        "dropped", "cancelled" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private fun String?.toIsoInstant(): Long? = this?.let { value ->
        runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(value).toEpochMilli() }
            .getOrNull()
    }

    private companion object {
        const val API = "https://api.asurascans.com"
    }
}
