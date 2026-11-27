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
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * MangaTime (mangatime.org), a tRPC site: the lists, the details, the chapter list and the
 * page images all come from `api/trpc/<procedure>?batch=1&input=<envelope>` procedures, so the
 * app never parses this site's HTML. A chapter the site reports as locked is refused with its
 * own answer instead of being shown empty.
 */
class MangaTimeSource : MangaSource {

    override val id: String = "mangatime"
    override val name: String = "Manga Time"
    override val lang: String = "ar"
    override val baseUrl: String = "https://mangatime.org"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(
        userAgent = SourceHttp.ANDROID_USER_AGENT,
        defaultHeaders = mapOf("Accept" to "application/json"),
    )

    override suspend fun popular(page: Int): List<SourceManga> = searchSeries(page, "popularity")

    override suspend fun latest(page: Int): List<SourceManga> = searchSeries(page, "recent")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        return searchSeries(page, "relevance", query.trim())
    }

    private suspend fun searchSeries(page: Int, sortBy: String, query: String? = null): List<SourceManga> {
        val input = buildString {
            append("{\"page\":").append(page)
            append(",\"limit\":").append(PAGE_SIZE)
            append(",\"sortBy\":\"").append(sortBy).append("\"")
            append(",\"sortOrder\":\"desc\"")
            if (query != null) append(",\"query\":").append(quote(query))
            append('}')
        }
        val payload = trpc("search.searchSeries", input)
        return payload.array("results").orEmpty().mapNotNull { element ->
            element.obj()?.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val slug = slugOf(mangaId)
        val payload = trpc("content.getSeriesBySlug", "{\"slug\":${quote(slug)}}")
        return payload.toManga(slug)
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val id = mangaId.substringAfterLast("#", "")
        if (id.isBlank()) throw SourceException(SourceFailure.PARSING, "Open the series again")
        val payload = trpc("content.getChapters", "{\"seriesId\":${quote(id)},\"limit\":-1}")
        val chapters = payload.array("chapters").orEmpty().mapNotNull { element ->
            val chapter = element.obj() ?: return@mapNotNull null
            val number = chapter.raw("number") ?: return@mapNotNull null
            val title = chapter.string("title")
            SourceChapter(
                id = number,
                number = number,
                title = title?.takeUnless { it.equals("Chapter $number", ignoreCase = true) },
                publishedAt = (chapter.string("publishedAt") ?: chapter.string("createdAt"))
                    .toInstantMillis(),
            )
        }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val slug = slugOf(mangaId)
        val number = chapterId.toIntOrNull()
            ?: throw SourceException(SourceFailure.PARSING, "Open the chapter list again")
        val payload = trpc(
            "content.getChapterPages",
            "{\"seriesSlug\":${quote(slug)},\"chapterNumber\":$number}",
        )
        if (payload.bool("isUnlocked") == false) {
            throw SourceException(SourceFailure.EMPTY, "This chapter is locked on the site")
        }
        val pages = payload.array("pages").orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { it.isNotBlank() }
        if (pages.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return pages.map { SourcePage(absolute(it)) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/series/${slugOf(mangaId)}"

    private fun slugOf(mangaId: String): String =
        mangaId.substringBefore("#").substringAfterLast("/")

    private fun absolute(path: String): String =
        if (path.startsWith("http")) path.replace(" ", "%20")
        else "$baseUrl${path.replace(" ", "%20")}"

    /**
     * tRPC answers `[{"result":{"data":{"json":…}}}]`, and its input envelope is always
     * `{"0":{"json":…}}`; both are produced here so every procedure reads the same way.
     */
    private suspend fun trpc(procedure: String, inputJson: String): JsonObject {
        val url = "$baseUrl/api/trpc/$procedure".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", "{\"0\":{\"json\":$inputJson}}")
            .build()
        val first = http.json(url.toString()).array()?.firstOrNull()
            ?: throw SourceException(SourceFailure.PARSING, "MangaTime answered an empty envelope")
        return first.obj()?.obj("result")?.obj("data")?.obj("json")
            ?: throw SourceException(SourceFailure.PARSING, "MangaTime answered without data")
    }

    private fun JsonObject.toManga(slugFallback: String? = null): SourceManga? {
        val slug = string("slug") ?: slugFallback ?: return null
        val title = string("title") ?: string("name") ?: return null
        val id = string("id")
        val cover = string("coverUrl")
        return SourceManga(
            id = if (id != null) "$slug#$id" else slug,
            title = title,
            coverUrl = cover?.let { absolute(it) },
            url = "$baseUrl/series/$slug",
            description = string("description"),
            genres = stringList("genres"),
            status = when (string("status")?.lowercase()) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "hiatus" -> MangaStatus.HIATUS
                "cancelled", "canceled" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            },
            rating = double("rating"),
            author = string("author"),
            artist = string("artist"),
            updatedAt = (string("updatedAt") ?: string("lastChapterAddedAt")).toInstantMillis(),
        )
    }

    /** Exposed for unit tests: one recorded tRPC data object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga()

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(character)
            }
        }
        append('"')
    }

    private companion object {
        const val PAGE_SIZE = 24
    }
}
