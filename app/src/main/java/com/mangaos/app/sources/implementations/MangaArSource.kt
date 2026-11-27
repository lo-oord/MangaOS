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
import com.mangaos.app.sources.network.double
import com.mangaos.app.sources.network.int
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * MangaAR (mangaar.com) through the public API of its own website. The site mixes its own
 * Arabic translations with chapters it proxies from MangaDex, and the two have different page
 * endpoints: the chapter id records which one it is so the reader asks the right one.
 */
class MangaArSource : MangaSource {

    override val id: String = "mangaar"
    override val name: String = "MangaAr"
    override val lang: String = "ar"
    override val baseUrl: String = "https://mangaar.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(
        userAgent = SourceHttp.ANDROID_USER_AGENT,
        defaultHeaders = mapOf("Accept" to "application/json"),
    )

    override suspend fun popular(page: Int): List<SourceManga> {
        val payload = http.json("$API/public/trending").obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaAR trending payload is missing")
        val popular = payload.array("popular").orEmpty()
        val trending = payload.array("trending").orEmpty()
        return (popular + trending).mapNotNull { element -> element.obj()?.toListManga() }
            .distinctBy { it.id }
    }

    override suspend fun latest(page: Int): List<SourceManga> {
        val payload = http.json("$API/public/recent-chapters?limit=30&mode=diverse").obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaAR latest payload is missing")
        return payload.array("chapters").orEmpty().mapNotNull { element ->
            val chapter = element.obj() ?: return@mapNotNull null
            val mangaId = chapter.int("manga_id") ?: return@mapNotNull null
            val title = chapter.string("manga_title") ?: return@mapNotNull null
            SourceManga(
                id = mangaId.toString(),
                title = title,
                coverUrl = resolve(chapter.string("cover_url")),
                url = "$baseUrl/manga/$mangaId",
            )
        }.distinctBy { it.id }
    }

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$API/public/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "36")
            .addQueryParameter("q", query.trim())
            .build()
        val payload = http.json(url.toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaAR search payload is missing")
        return payload.array("manga").orEmpty().mapNotNull { element ->
            element.obj()?.toListManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val payload = http.json("$API/public/manga/${mangaId.trim('/')}").obj() ?: return null
        return payload.toDetails()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val id = mangaId.trim('/')
        val payload = http.json("$API/public/manga/$id/chapters").obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaAR chapter payload is missing")
        val chapters = payload.array("chapters").orEmpty().mapNotNull { element ->
            val chapter = element.obj() ?: return@mapNotNull null
            val episode = chapter.double("episode_number") ?: return@mapNotNull null
            val number = formatNumber(episode)
            val badge = chapter.string("badge_type")
            val mangadexId = chapter.string("mangadex_chapter_id")
            val proxied = badge != null && badge != "translated" && mangadexId != null
            val title = chapter.string("title")?.takeUnless { it == "الفصل $number" }
            SourceChapter(
                // kind|number|mangadex id: enough to choose the right page endpoint later.
                id = "${if (proxied) "proxy" else "local"}|$number|${mangadexId ?: "-"}",
                number = number,
                title = title,
                publishedAt = (chapter.string("publish_date") ?: chapter.string("release_date")).toInstantMillis(),
            )
        }.sortedByDescending { it.number.toFloatOrNull() ?: -1f }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val parts = chapterId.split("|")
        if (parts.size < 3) throw SourceException(SourceFailure.PARSING, "Open the chapter list again")
        val kind = parts[0]
        val number = parts[1]
        val mangadexId = parts[2]
        val url = if (kind == "proxy" && mangadexId != "-") {
            "$API/public/chapter-by-id/$mangadexId/pages"
        } else {
            "$API/chapters/by-manga/${mangaId.trim('/')}/$number/pages"
        }
        val payload = http.json(url).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaAR page payload is missing")
        val pages = payload.array("pages").orEmpty().mapNotNull { element ->
            (element as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("http")) it else resolve(it) }
        }
        if (pages.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return pages.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/manga/${mangaId.trim('/')}"

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toDetails()

    private fun JsonObject.toListManga(): SourceManga? {
        val id = int("id") ?: int("manga_id") ?: return null
        val title = string("title") ?: string("manga_title") ?: return null
        return SourceManga(
            id = id.toString(),
            title = title,
            coverUrl = resolve(string("cover_url")),
            url = "$baseUrl/manga/$id",
            genres = stringList("genre"),
            description = string("description_ar"),
        )
    }

    private fun JsonObject.toDetails(): SourceManga? {
        val id = int("id") ?: return null
        val title = string("title") ?: return null
        val type = string("type")
        val genres = buildList {
            when (type) {
                "manga" -> add("مانغا")
                "manhwa" -> add("مانهوا")
                "manhua" -> add("مانها")
            }
            addAll(stringList("genre"))
        }.distinct()
        val description = buildString {
            string("description_ar")?.takeIf { it.isNotBlank() }?.let { append(it.trim()) }
            string("title_en")?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("English title: $it")
            }
        }.takeIf { it.isNotBlank() }
        return SourceManga(
            id = id.toString(),
            title = title,
            coverUrl = resolve(string("cover_url")),
            url = "$baseUrl/manga/$id",
            description = description,
            genres = genres,
            status = when (string("status_ar")?.trim()) {
                "مستمر" -> MangaStatus.ONGOING
                "مكتمل" -> MangaStatus.COMPLETED
                "متوقف" -> MangaStatus.HIATUS
                "ملغي" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            },
            author = string("author"),
            artist = string("artist"),
            updatedAt = string("updated_at").toInstantMillis(),
        )
    }

    private fun resolve(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http") -> path
        else -> try {
            baseUrl.toHttpUrl().resolve(path)?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        const val API = "https://mangaar.com/api"
    }
}
