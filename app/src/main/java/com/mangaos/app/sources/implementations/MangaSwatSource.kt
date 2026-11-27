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
import com.mangaos.app.sources.network.int
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * MangaSwat (meshmanga.com) through the mobile API its app uses (`appswat.com`). The website
 * redirects its page images to a banner, so both the catalogue and the images are read from
 * the API host, which is what the site's own apps do.
 */
class MangaSwatSource : MangaSource {

    override val id: String = "mangaswat"
    override val name: String = "MangaSwat"
    override val lang: String = "ar"
    override val baseUrl: String = "https://meshmanga.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(
        userAgent = SourceHttp.ANDROID_USER_AGENT,
        defaultHeaders = mapOf("Accept" to "application/json"),
    )

    override suspend fun popular(page: Int): List<SourceManga> =
        series(page, "series/") { addQueryParameter("order_by", "-followers_count") }

    override suspend fun latest(page: Int): List<SourceManga> =
        series(page, "series/releases/") { }

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        return series(page, "series/") { addQueryParameter("search", query.trim()) }
    }

    private suspend fun series(
        page: Int,
        path: String,
        params: okhttp3.HttpUrl.Builder.() -> Unit,
    ): List<SourceManga> {
        val url = api(path).apply(params)
            .addQueryParameter("page_size", "100")
            .addQueryParameter("page", page.toString())
            .build()
        val payload = http.json(url).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaSwat list payload is not an object")
        return payload.array("results").orEmpty().mapNotNull { element ->
            element.obj()?.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val payload = http.json(api("series/${mangaId.trim('/')}/").build()).obj() ?: return null
        return payload.toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val found = ArrayList<SourceChapter>()
        var page = 1
        do {
            val url = api("chapters/").apply {
                addQueryParameter("serie", mangaId.trim('/'))
                addQueryParameter("order_by", "-order")
                addQueryParameter("page_size", "200")
                addQueryParameter("page", page.toString())
            }.build()
            val payload = http.json(url).obj()
                ?: throw SourceException(SourceFailure.PARSING, "MangaSwat chapter payload is missing")
            payload.array("results").orEmpty().forEach { element ->
                element.obj()?.toChapter()?.let(found::add)
            }
            val hasNext = payload.string("next") != null
            page++
            if (!hasNext) break
        } while (page <= MAX_CHAPTER_PAGES)
        if (found.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return found
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val payload = http.json(api("chapters/$chapterId/").build()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaSwat chapter page payload is missing")
        val pages = payload.array("images").orEmpty()
            .mapNotNull { element ->
                val image = element.obj() ?: return@mapNotNull null
                val url = image.string("image") ?: return@mapNotNull null
                (image.int("order") ?: Int.MAX_VALUE) to url
            }
            .sortedBy { it.first }
            .map { SourcePage(it.second) }
        if (pages.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return pages
    }

    override fun imageHeaders(): Map<String, String> = mapOf(
        "Referer" to "https://appswat.com/",
        "Accept" to "image/avif,image/webp,image/*,*/*;q=0.8",
    )

    override fun webUrl(mangaId: String): String = "$baseUrl/series/${mangaId.trim('/')}"

    private fun api(path: String): okhttp3.HttpUrl.Builder = API.newBuilder().addPathSegments(path)

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga()

    private fun JsonObject.toManga(): SourceManga? {
        val id = int("id") ?: int("serie_id") ?: return null
        val title = string("title") ?: return null
        val poster = obj("poster")
        val genres = array("genres").orEmpty().mapNotNull { element ->
            element.obj()?.string("name")
        }
        val statusId = obj("status")?.int("id")
        return SourceManga(
            id = id.toString(),
            title = title,
            coverUrl = poster?.string("medium") ?: poster?.string("thumbnail"),
            url = "$baseUrl/series/$id",
            description = story(),
            genres = genres,
            status = when (statusId) {
                79 -> MangaStatus.ONGOING
                80 -> MangaStatus.COMPLETED
                81 -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            author = obj("author")?.string("name"),
            artist = obj("artist")?.string("name"),
            updatedAt = string("updated_at").toInstantMillis(),
        )
    }

    private fun JsonObject.story(): String? {
        val text = string("story") ?: return null
        val alternative = string("alternative")?.takeIf { it.isNotBlank() }
        val parts = listOfNotNull(
            text.trim().takeIf { it.isNotBlank() },
            alternative?.let { "عناوين بديلة: ${it.replace("|", "، ")}" },
        )
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun JsonObject.toChapter(): SourceChapter? {
        val id = int("id") ?: return null
        val label = raw("chapter") ?: raw("title") ?: return null
        val number = Regex("""\d+(?:\.\d+)?""").find(label)?.value ?: label
        val title = string("title")?.takeIf { it.isNotBlank() && it != label }
        return SourceChapter(
            id = id.toString(),
            number = number,
            title = title,
            publishedAt = (string("created_at") ?: string("published_at")).toInstantMillis(),
        )
    }

    private companion object {
        val API = "https://appswat.com/v2/api/v2/".toHttpUrl()
        const val MAX_CHAPTER_PAGES = 10
    }
}
