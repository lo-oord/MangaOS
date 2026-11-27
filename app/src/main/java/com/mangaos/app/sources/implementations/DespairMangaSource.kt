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
 * Despair Manga (despair-world.com). The site reads its catalogue from a Supabase REST
 * endpoint that is served from its own domain, with the public anon key the website ships.
 *
 * Pages of a chapter are stored as a list of image URLs on the chapter row itself.
 */
class DespairMangaSource : MangaSource {

    override val id: String = "despairmanga"
    override val name: String = "Despair Manga"
    override val lang: String = "ar"
    override val baseUrl: String = "https://despair-world.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    private val apiHeaders = mapOf(
        "apikey" to ANON_KEY,
        "Authorization" to "Bearer $ANON_KEY",
        "Accept" to "application/json",
    )

    override suspend fun popular(page: Int): List<SourceManga> = works(page, "views_count.desc")

    override suspend fun latest(page: Int): List<SourceManga> = works(page, "updated_at.desc")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val term = query.trim().replace("\\", "\\\\").replace("\"", "\\\"")
        return works(page, "views_count.desc") {
            addQueryParameter("or", "(title_ar.ilike.\"*$term*\",title_en.ilike.\"*$term*\")")
        }
    }

    private suspend fun works(
        page: Int,
        order: String,
        filter: okhttp3.HttpUrl.Builder.() -> Unit = {},
    ): List<SourceManga> {
        val url = "$baseUrl/supabase-api/rest/v1/manga_works".toHttpUrl().newBuilder()
            .addQueryParameter("select", WORK_COLUMNS)
            .apply(filter)
            .addQueryParameter("order", order)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1).coerceAtLeast(0) * PAGE_SIZE).toString())
            .build()
        val payload = http.json(url.toString(), apiHeaders).array().orEmpty()
        return payload.mapNotNull { element -> element.obj()?.toManga() }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val slug = slugOf(mangaId)
        val url = "$baseUrl/supabase-api/rest/v1/manga_works".toHttpUrl().newBuilder()
            .addQueryParameter("select", WORK_COLUMNS)
            .addQueryParameter("slug", "eq.$slug")
            .addQueryParameter("limit", "1")
            .build()
        return http.json(url.toString(), apiHeaders).array().orEmpty()
            .firstOrNull()?.obj()?.toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val work = details(mangaId) ?: throw SourceException(SourceFailure.PARSING, "Series not found")
        val workId = work.id.substringAfterLast("#", "")
        if (workId.isBlank()) throw SourceException(SourceFailure.PARSING, "Open the series again")
        val url = "$baseUrl/supabase-api/rest/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,chapter_number,title,is_free,created_at")
            .addQueryParameter("work_id", "eq.$workId")
            .addQueryParameter("order", "chapter_number.desc,id.desc")
            .build()
        val chapters = http.json(url.toString(), apiHeaders).array().orEmpty().mapNotNull { element ->
            val chapter = element.obj() ?: return@mapNotNull null
            // Supabase ids are opaque strings on this deployment ("wp_2721").
            val id = chapter.raw("id") ?: return@mapNotNull null
            val number = chapter.raw("chapter_number") ?: return@mapNotNull null
            SourceChapter(
                id = id,
                number = number,
                title = chapter.string("title"),
                publishedAt = chapter.string("created_at").toInstantMillis(),
            )
        }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = "$baseUrl/supabase-api/rest/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "pages")
            .addQueryParameter("id", "eq.$chapterId")
            .build()
        val images = http.json(url.toString(), apiHeaders).array().orEmpty()
            .firstOrNull()?.obj()?.array("pages").orEmpty()
            .mapNotNull { element -> (element as? kotlinx.serialization.json.JsonPrimitive)?.content }
            .filter { it.isNotBlank() }
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/series/${slugOf(mangaId)}"

    private fun slugOf(mangaId: String): String =
        mangaId.substringBeforeLast("#").substringAfterLast("/")

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga()

    private fun JsonObject.toManga(): SourceManga? {
        val slug = string("slug") ?: return null
        val id = raw("id")
        val title = string("title_ar") ?: string("title_en") ?: return null
        val alt = string("title_en")?.takeIf { it.isNotBlank() && it != title }
        val genres = stringList("genres")
        return SourceManga(
            id = if (id != null) "$slug#$id" else slug,
            title = title,
            coverUrl = string("cover_url"),
            url = "$baseUrl/series/$slug",
            description = buildString {
                string("synopsis")?.takeIf { it.isNotBlank() }?.let { append(it.trim()) }
                alt?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("English title: $it")
                }
            }.takeIf { it.isNotBlank() },
            genres = genres,
            status = when (string("status")?.lowercase()) {
                "ongoing", "مستمر" -> MangaStatus.ONGOING
                "completed", "مكتمل" -> MangaStatus.COMPLETED
                "hiatus", "متوقف" -> MangaStatus.HIATUS
                "cancelled", "canceled", "ملغي" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            },
            author = string("author_or_studio"),
            updatedAt = string("updated_at").toInstantMillis(),
        )
    }

    private companion object {
        const val PAGE_SIZE = 24
        const val WORK_COLUMNS =
            "id,slug,title_ar,title_en,cover_url,genres,synopsis,status,author_or_studio,updated_at"

        /**
         * The public anon key the site ships to its own browser client. It grants read-only
         * access to the public catalogue; it is not a user credential.
         */
        const val ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJyb2xlIjoiYW5vbiIsImlzcyI6InN1cGFiYXNlIiw" +
                "iaWF0IjoxNzkwMzczMDE4LCJleHAiOjE5NDgwNTMwMTh9.xYgAlC_9SuUCdzdff6wBYGWzYFmAI9apP_mk4DCPaVI"
    }
}
