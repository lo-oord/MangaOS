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
import org.jsoup.Jsoup

/**
 * Azora (azorafly.com), an Iken-based Arabic site: list, search, details and the chapter's
 * images all come from its `api.azorafly.com` endpoints. Novels are hidden, and chapters the
 * site locks are marked with a lock and skipped unless the site marks them readable.
 */
class AzoraSource : MangaSource {

    override val id: String = "azora"
    override val name: String = "Azora"
    override val lang: String = "ar"
    override val baseUrl: String = "https://azorafly.com"
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
        query(page, orderBy = "totalViews")

    override suspend fun latest(page: Int): List<SourceManga> =
        query(page, orderBy = "lastChapterAddedAt")

    override suspend fun search(query: String, page: Int): List<SourceManga> =
        query(page, orderBy = "totalViews", search = query.trim())

    /**
     * A page can be emptied by the novel filter. The next pages are read in that case (at most
     * a few), so an adult-heavy stretch never looks like the end of the catalogue.
     */
    private suspend fun query(page: Int, orderBy: String, search: String = ""): List<SourceManga> {
        var current = page
        repeat(PAGES_PER_CALL) {
            val url = "$API/api/query".toHttpUrl().newBuilder()
                .addQueryParameter("perPage", PER_PAGE.toString())
                .addQueryParameter("searchTerm", search)
                .addQueryParameter("orderBy", orderBy)
                .addQueryParameter("orderDirection", "desc")
                .addQueryParameter("page", current.toString())
                .build()
            val payload = http.json(url.toString()).obj()
                ?: throw SourceException(SourceFailure.PARSING, "Azora list payload is not an object")
            val posts = payload.array("posts").orEmpty()
            val mangas = posts.mapNotNull { element ->
                element.obj()?.takeUnless { it.isNovel() }?.toManga()
            }
            if (mangas.isNotEmpty() || posts.isEmpty()) return mangas
            current++
        }
        return emptyList()
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val slug = slugOf(mangaId)
        val payload = http.json("$API/api/post?postSlug=$slug").obj() ?: return null
        val post = payload.obj("post") ?: return null
        if (post.isNovel()) return null
        return post.toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val slug = slugOf(mangaId)
        val payload = http.json("$API/api/post?postSlug=$slug").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Azora details payload is missing")
        val post = payload.obj("post")
            ?: throw SourceException(SourceFailure.PARSING, "Azora series is missing")
        val postId = post.int("id")
            ?: throw SourceException(SourceFailure.PARSING, "Azora series has no id")
        // The series page carries the chapter list under its chapters endpoint, not in itself.
        val chapterPayload = http.json("$API/api/chapters?postId=$postId").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Azora chapter payload is missing")
        val chapterPost = chapterPayload.obj("post")
            ?: throw SourceException(SourceFailure.PARSING, "Azora chapter list is missing")
        val chapters = chapterPost.array("chapters").orEmpty().mapNotNull { element ->
            val chapter = element.obj() ?: return@mapNotNull null
            val id = chapter.int("id") ?: return@mapNotNull null
            val number = chapter.raw("number") ?: return@mapNotNull null
            SourceChapter(
                id = id.toString(),
                number = number,
                title = chapter.string("title"),
                publishedAt = chapter.string("createdAt").toInstantMillis(),
            )
        }
        if (chapters.isEmpty()) {
            throw SourceException(SourceFailure.EMPTY, "Azora lists no chapter for $slug")
        }
        return chapters
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val payload = http.json("$API/api/chapter?chapterId=$chapterId").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Azora chapter payload is missing")
        val chapter = payload.obj("chapter")
            ?: throw SourceException(SourceFailure.PARSING, "Azora chapter is missing")
        if (chapter.array("images").orEmpty().isEmpty()) {
            throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        }
        return chapter.array("images").orEmpty()
            .mapNotNull { element ->
                val image = element.obj() ?: return@mapNotNull null
                val url = image.string("url") ?: return@mapNotNull null
                val order = image.int("order") ?: Int.MAX_VALUE
                order to url
            }
            .sortedBy { it.first }
            .map { SourcePage(it.second) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/series/${slugOf(mangaId)}"

    /** The app's source id for a series carries the numeric post id after `#`. */
    private fun slugOf(mangaId: String): String =
        mangaId.substringBeforeLast("#").substringAfterLast("/").ifBlank { mangaId }

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject, slug: String): SourceManga? =
        json.toManga().takeIf { slug.isNotBlank() }

    private fun JsonObject.isNovel(): Boolean =
        string("isNovel")?.toBooleanStrictOrNull() == true ||
            string("seriesType")?.equals("novel", ignoreCase = true) == true

    private fun JsonObject.toManga(): SourceManga? {
        val slug = string("slug") ?: return null
        val title = string("postTitle") ?: string("title") ?: return null
        val id = int("id")
        val genres = stringList("genres").ifEmpty {
            array("genres").orEmpty().mapNotNull { element -> element.obj()?.string("name") }
        }
        // The list payload already carries the newest chapters, so the feed never has to ask
        // for a series' full chapter list just to print the number and the date on its card.
        val newest = newestChapter()
        return SourceManga(
            id = if (id != null) "$slug#$id" else slug,
            title = title,
            coverUrl = string("featuredImage"),
            url = "$baseUrl/series/$slug",
            description = description(),
            genres = genres,
            status = status(),
            author = string("author"),
            artist = string("artist"),
            latestChapter = newest?.raw("number"),
            latestChapterId = newest?.int("id")?.toString(),
            updatedAt = newest?.string("createdAt").toInstantMillis()
                ?: string("updatedAt").toInstantMillis(),
        )
    }

    /** The chapter with the highest number in the embedded `chapters` array, when present. */
    private fun JsonObject.newestChapter(): JsonObject? =
        array("chapters").orEmpty()
            .mapNotNull { element -> element.obj() }
            .maxWithOrNull(
                compareBy<JsonObject> { it.raw("number")?.toFloatOrNull() ?: -1f }
                    .thenBy { it.string("createdAt").toInstantMillis() ?: 0L },
            )

    private fun JsonObject.description(): String? {
        val content = string("postContent") ?: string("description")
        val cleaned = content?.let { Jsoup.parse(it.replace("\n", "<br>")).text() }
        val alt = string("alternativeTitles")
        val parts = listOfNotNull(
            cleaned?.takeIf { it.isNotBlank() },
            alt?.takeIf { it.isNotBlank() }?.let { "Alternative Names: $it" },
        )
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun JsonObject.status(): MangaStatus = when (string("seriesStatus")?.uppercase()) {
        "ONGOING", "COMING_SOON", "MASS_RELEASED" -> MangaStatus.ONGOING
        "COMPLETED" -> MangaStatus.COMPLETED
        "CANCELLED", "DROPPED" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    private companion object {
        const val API = "https://api.azorafly.com"
        const val PER_PAGE = 24

        /** Pages read in one call while the novel filter keeps emptying them. */
        const val PAGES_PER_CALL = 4
    }
}
