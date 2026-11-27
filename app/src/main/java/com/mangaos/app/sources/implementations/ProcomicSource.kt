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
import com.mangaos.app.sources.network.int
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * ProComic (procomic.pro) through its public content API. Novels are filtered out because the
 * app cannot read them, and locked chapters are listed but marked.
 *
 * The site renders chapter images in a scripted reader that only runs in its own page (the
 * image slots are produced inside the browser), so a chapter's pages cannot be read over plain
 * HTTP. [pages] reports that instead of returning a broken list.
 */
class ProcomicSource : MangaSource {

    override val id: String = "procomic"
    override val name: String = "ProComic"
    override val lang: String = "ar"
    override val baseUrl: String = "https://procomic.pro"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(
        userAgent = SourceHttp.ANDROID_USER_AGENT,
        defaultHeaders = mapOf("Accept" to "application/json"),
    )

    override suspend fun popular(page: Int): List<SourceManga> = listing(page, "popular")

    override suspend fun latest(page: Int): List<SourceManga> = listing(page, "latest_chapter")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        return listing(page, "popular", query.trim())
    }

    private suspend fun listing(page: Int, sort: String, search: String? = null): List<SourceManga> {
        val url = "$baseUrl/api/public/content".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("page", page.toString())
            .apply { if (search != null) addQueryParameter("search", search) }
            .build()
        val payload = http.json(url.toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "ProComic list payload is missing")
        return payload.array("data").orEmpty().mapNotNull { element ->
            element.obj()?.takeIf { it.isReadable() }?.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val payload = http.json("$baseUrl/api/public/content/${mangaId.trim('/')}").obj() ?: return null
        return payload.takeIf { it.isReadable() }?.toManga(details = true)
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val id = mangaId.trim('/')
        val type = details(id)?.let { readType(it) } ?: "manga"
        val url = "$baseUrl/api/public/$type/$id/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "100")
            .addQueryParameter("order", "desc")
            .addQueryParameter("page", "1")
            .build()
        val first = http.json(url.toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "ProComic chapter payload is missing")
        val total = first.int("total") ?: 0
        val pages = if (total > 100) (total + 99) / 100 else 1
        val chapters = ArrayList<SourceChapter>()
        (1..pages.coerceAtMost(MAX_CHAPTER_PAGES)).forEach { page ->
            val pageUrl = url.newBuilder().setQueryParameter("page", page.toString()).build()
            val payload = if (page == 1) first else http.json(pageUrl.toString()).obj()
            payload?.array("data").orEmpty().forEach { element ->
                element.obj()?.toChapter()?.let(chapters::add)
            }
        }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        // The chapter images exist only inside the site's scripted reader.
        throw SourceException(
            SourceFailure.UNSUPPORTED,
            "ProComic serves chapter pages inside its scripted reader; MangaOS cannot read them natively",
        )
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/ar/series/${mangaId.trim('/')}"

    /** The chapter list lives under the content type (`manhua`, `manga`, `manhwa`). */
    private fun readType(manga: SourceManga): String = when {
        manga.genres.any { it.equals("manhua", true) } -> "manhua"
        manga.genres.any { it.equals("manhwa", true) } -> "manhwa"
        else -> "manga"
    }

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga(details = true)

    private fun JsonObject.isReadable(): Boolean =
        string("type") != "novel" && bool("isBlockedSeries") != true

    private fun JsonObject.toManga(details: Boolean = false): SourceManga? {
        val id = int("id") ?: return null
        val title = string("title") ?: return null
        val meta = obj("metadata")
        val type = string("type")
        val cover = string("coverImage") ?: meta?.string("coverImage") ?: string("thumbnail")
        val genres = buildList {
            type?.replaceFirstChar { it.titlecase() }?.let(::add)
            meta?.string("origin")?.replaceFirstChar { it.titlecase() }?.let(::add)
            meta?.string("year")?.let(::add)
            meta?.stringList("genres")?.let(::addAll)
            meta?.stringList("tags")?.let(::addAll)
        }.distinct()
        return SourceManga(
            id = id.toString(),
            title = title,
            coverUrl = cover?.let { resolveCover(it) },
            url = "$baseUrl/ar/series/${string("slug") ?: id}-$id",
            description = buildString {
                string("description")?.trim()?.takeIf { it.isNotBlank() }?.let(::append)
                if (details) {
                    val alternatives = buildList {
                        meta?.string("originalTitle")?.let(::add)
                        meta?.stringList("altTitles")?.let(::addAll)
                    }.distinct()
                    if (alternatives.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("عناوين بديلة\n")
                        alternatives.forEach { append("- $it\n") }
                    }
                }
            }.trim().takeIf { it.isNotBlank() },
            genres = genres,
            status = when (string("progress")?.trim()) {
                "مستمر" -> MangaStatus.ONGOING
                "مكتمل" -> MangaStatus.COMPLETED
                "متوقف", "متوقف من الراو" -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            author = meta?.string("author"),
            artist = meta?.string("artist"),
            updatedAt = (string("updated_at") ?: string("updatedAt")).toInstantMillis(),
        )
    }

    private fun JsonObject.toChapter(): SourceChapter? {
        val id = int("id") ?: return null
        val number = raw("chapter_number") ?: return null
        val language = string("language")
        // The site publishes several languages per series; only the Arabic release is listed.
        if (language != null && !language.equals("AR", ignoreCase = true)) return null
        val locked = bool("lockedForever") == true ||
            bool("lockedByCoins") == true ||
            bool("lockedByShortlink") == true ||
            bool("lockedByExclusive") == true
        val title = string("title")?.takeUnless { it == number }
        return SourceChapter(
            id = id.toString(),
            number = number,
            title = buildString {
                if (locked) append("🔒 ")
                append(title ?: "الفصل $number")
            },
            publishedAt = (string("published_at") ?: string("publishedAt")).toInstantMillis(),
        )
    }

    private fun resolveCover(cover: String): String = when {
        cover.startsWith("http") -> cover
        else -> try {
            baseUrl.toHttpUrl().resolve(cover)?.toString() ?: cover
        } catch (_: Exception) {
            cover
        }
    }

    private companion object {
        const val PAGE_SIZE = 12
        const val MAX_CHAPTER_PAGES = 20
    }
}
