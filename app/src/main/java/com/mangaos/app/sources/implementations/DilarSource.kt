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
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Dilar (dilar.tube), through the JSON API its website uses for the catalogue, the series and
 * the chapter list. Novels are filtered out and the site's own "reader" endpoint is the only
 * way to a chapter's pages: it decrypts its image list in the browser, so [pages] reports that
 * instead of returning a broken list.
 */
class DilarSource : MangaSource {

    override val id: String = "dilar"
    override val name: String = "Dilar"
    override val lang: String = "ar"
    override val baseUrl: String = "https://dilar.tube"
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
        val payload = http.json("$baseUrl/api/rankings").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Dilar rankings payload is missing")
        return payload.array("topSeries").orEmpty().mapNotNull { element ->
            element.obj()?.takeUnless { it.isNovel() }?.toManga()
        }
    }

    override suspend fun latest(page: Int): List<SourceManga> {
        val payload = http.json("$baseUrl/api/series?page=$page").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Dilar series payload is missing")
        return payload.array("series").orEmpty().mapNotNull { element ->
            element.obj()?.takeUnless { it.isNovel() }?.toManga()
        }
    }

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val body = buildString {
            append("{\"query\":").append(quote(query.trim()))
            append(",\"page\":").append(page)
            append(",\"seriesType\":{\"include\":[],\"exclude\":[\"$NOVEL_TYPE\"]}")
            append(",\"categories\":{\"include\":[],\"exclude\":[]}")
            append(",\"oneshot\":false")
            append(",\"storyStatus\":{\"include\":[],\"exclude\":[]}")
            append(",\"translationStatus\":{\"include\":[],\"exclude\":[]}")
            append(",\"over17\":null}")
        }
        val payload = http.postJson("$baseUrl/api/search/filter", body).obj()
            ?: throw SourceException(SourceFailure.PARSING, "Dilar search payload is missing")
        return payload.array("rows").orEmpty().mapNotNull { element ->
            element.obj()?.takeUnless { it.isNovel() }?.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val id = mangaId.trim('/').substringBefore("/")
        val payload = http.json("$baseUrl/api/series/$id").obj() ?: return null
        return payload.takeUnless { it.isNovel() }?.toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val id = mangaId.trim('/').substringBefore("/")
        val payload = http.json("$baseUrl/api/series/$id/chapters").obj()
            ?: throw SourceException(SourceFailure.PARSING, "Dilar chapter payload is missing")
        val chapters = ArrayList<SourceChapter>()
        payload.array("chapters").orEmpty().forEach { element ->
            val chapter = element.obj()
            if (chapter != null) {
                val number = chapter.raw("chapter")?.removeSuffix(".00")
                if (number != null) {
                    val title = chapter.string("title")?.takeIf { it.isNotBlank() }
                    chapter.array("releases").orEmpty().forEach { releaseElement ->
                        val release = releaseElement.obj()
                        val releaseId = release?.string("id")
                        if (release != null && releaseId != null) {
                            chapters += SourceChapter(
                                // <number>#<release id>: the page endpoint is addressed by the release.
                                id = "$number#$releaseId",
                                number = number,
                                title = buildString {
                                    append(number)
                                    title?.let { append(" - ").append(it) }
                                },
                                publishedAt = (
                                    release.string("created_at") ?: release.string("createdAt")
                                    ).toInstantMillis(),
                            )
                        }
                    }
                }
            }
        }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters.sortedByDescending { it.number.toFloatOrNull() ?: -1f }
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        throw SourceException(
            SourceFailure.UNSUPPORTED,
            "Dilar decrypts its reader's image list in the browser; MangaOS cannot read these pages natively",
        )
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = "$baseUrl/series/${mangaId.trim('/')}"

    /** Exposed for unit tests: one recorded API object, no network. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga()

    private fun JsonObject.isNovel(): Boolean =
        obj("seriesType")?.string("title") == "رواية"

    private fun JsonObject.toManga(): SourceManga? {
        val id = raw("id") ?: return null
        val title = string("title") ?: return null
        val cover = string("cover")
        val staff = array("staff").orEmpty().mapNotNull { it.obj() }
        fun names(role: String) = staff.filter { it.obj("Staff")?.string("role") == role }
            .mapNotNull { it.string("name") }
            .distinct()
        return SourceManga(
            id = id,
            title = title,
            coverUrl = cover?.let { coverOf(id, it) },
            url = "$baseUrl/series/$id",
            description = string("summary"),
            genres = array("categories").orEmpty().mapNotNull { it.obj()?.string("name") },
            status = when (string("translation_status")) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "dropped" -> MangaStatus.CANCELLED
                "hiatus" -> MangaStatus.HIATUS
                else -> MangaStatus.UNKNOWN
            },
            author = names("Author").joinToString(", ").takeIf { it.isNotBlank() },
            artist = names("Artist").joinToString(", ").takeIf { it.isNotBlank() },
        )
    }

    /** Covers are served from a resized file the site keeps beside the original. */
    private fun coverOf(id: String, cover: String): String {
        val name = cover.substringBeforeLast(".").substringAfterLast("/")
        val extension = cover.substringAfterLast(".", "webp")
        return "$baseUrl/uploads/manga/cover/$id/large_$name.$extension"
    }

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
        const val NOVEL_TYPE = "99"
    }
}
