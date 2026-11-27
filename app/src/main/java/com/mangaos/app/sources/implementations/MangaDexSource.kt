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
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * MangaDex over its public JSON API. One instance per reading language, because MangaDex is a
 * single multilingual catalogue and the app lists sources by language.
 *
 * Works: popular, latest uploads, search, details, chapter feed and the at-home image server.
 * Externally hosted chapters are skipped: they have no pages MangaDex can serve.
 */
class MangaDexSource(language: String) : MangaSource {

    override val id: String = "mangadex-$language"
    override val name: String = when (language) {
        "ar" -> "MangaDex (العربية)"
        "ja" -> "MangaDex (日本語)"
        else -> "MangaDex (English)"
    }
    override val lang: String = language
    override val baseUrl: String = "https://mangadex.org"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    // The MangaDex API answers a client user agent; a browser user agent gets a bot challenge.
    private val http = SourceHttp(
        userAgent = SourceHttp.APP_USER_AGENT,
        defaultHeaders = mapOf("Accept" to "application/json"),
    )

    override suspend fun popular(page: Int): List<SourceManga> = list(page) { builder ->
        builder.addQueryParameter("order[followedCount]", "desc")
    }

    override suspend fun latest(page: Int): List<SourceManga> = list(page) { builder ->
        builder.addQueryParameter("order[latestUploadedChapter]", "desc")
    }

    override suspend fun search(query: String, page: Int): List<SourceManga> = list(page) { builder ->
        builder.addQueryParameter("title", query.trim())
        builder.addQueryParameter("order[relevance]", "desc")
    }

    private suspend fun list(page: Int, tune: (HttpUrl.Builder) -> Unit): List<SourceManga> {
        val builder = API.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1).coerceAtLeast(0) * PAGE_SIZE).toString())
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("includes[]", "cover_art")
        tune(builder)
        val payload = http.json(builder.build().toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaDex list payload is not an object")
        return payload.array("data").orEmpty().mapNotNull { element ->
            val json = element.obj() ?: return@mapNotNull null
            // MangaDex has one catalogue for every language. Listing a work with no chapter in
            // this source's language would open an empty chapter list, so filter it out.
            val languages = json.obj("attributes")?.array("availableTranslatedLanguages")
                .orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (languages.isNotEmpty() && lang !in languages) return@mapNotNull null
            json.toManga()
        }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val url = API.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(mangaId)
            .addQueryParameter("includes[]", "cover_art")
            .build()
        return http.json(url.toString()).obj()?.obj("data")?.toManga()
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val found = ArrayList<SourceChapter>()
        var offset = 0
        repeat(MAX_CHAPTER_PAGES) {
            val url = API.toHttpUrl().newBuilder()
                .addPathSegment("manga")
                .addPathSegment(mangaId)
                .addPathSegment("feed")
                .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("translatedLanguage[]", lang)
                .addQueryParameter("order[volume]", "asc")
                .addQueryParameter("order[chapter]", "asc")
                .addQueryParameter("contentRating[]", "safe")
                .build()
            val payload = http.json(url.toString()).obj() ?: return found
            val batch = payload.array("data").orEmpty()
            batch.forEach { element -> element.obj()?.toChapter()?.let(found::add) }
            if (batch.size < CHAPTER_PAGE_SIZE) return found
            offset += CHAPTER_PAGE_SIZE
        }
        return found
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = API.toHttpUrl().newBuilder()
            .addPathSegment("at-home")
            .addPathSegment("server")
            .addPathSegment(chapterId)
            .build()
        val payload = http.json(url.toString()).obj()
            ?: throw SourceException(SourceFailure.PARSING, "MangaDex page payload is not an object")
        val base = payload.string("baseUrl")
            ?: throw SourceException(SourceFailure.PARSING, "MangaDex page base URL is missing")
        val chapter = payload.obj("chapter")
        val hash = chapter.string("hash")
            ?: throw SourceException(SourceFailure.PARSING, "MangaDex chapter hash is missing")
        val names = chapter.array("data").orEmpty().mapNotNull { element ->
            (element as? JsonPrimitive)?.contentOrNull
        }
        if (names.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return names.map { file -> SourcePage("$base/data/$hash/$file") }
    }

    override fun webUrl(mangaId: String): String = "$baseUrl/manga/$mangaId"

    /** Exposed for unit tests: parsing a recorded API object, without any network call. */
    internal fun mapManga(json: JsonObject): SourceManga? = json.toManga()

    internal fun mapChapter(json: JsonObject): SourceChapter? = json.toChapter()

    private fun JsonObject.toManga(): SourceManga? {
        val mangaId = string("id") ?: return null
        val attributes = obj("attributes") ?: return null
        val titles = attributes.obj("title")
        val alts = attributes.array("altTitles").orEmpty().mapNotNull { it.obj() }
        val title = titles?.localized(lang)
            ?: titles?.localized("en")
            ?: titles?.localized("ja")
            ?: alts.firstNotNullOfOrNull { it.localized(lang) ?: it.localized("en") }
            ?: return null
        val description = attributes.obj("description")?.localized(lang)
            ?: attributes.obj("description")?.localized("en")
        val tags = attributes.array("tags").orEmpty().mapNotNull { element ->
            val name = element.obj()?.obj("attributes")?.obj("name")
            name?.localized(lang) ?: name?.localized("en")
        }
        return SourceManga(
            id = mangaId,
            title = title,
            coverUrl = coverUrl(mangaId),
            url = "$baseUrl/manga/$mangaId",
            description = description,
            genres = tags,
            status = attributes.string("status").toStatus(),
            rating = attributes.double("rating"),
            latestChapter = attributes.string("lastChapter"),
            updatedAt = attributes.string("updatedAt").toIsoInstant(),
        )
    }

    private fun JsonObject.coverUrl(mangaId: String): String? {
        val relationships = array("relationships").orEmpty()
        val fileName = relationships
            .firstOrNull { it.obj()?.string("type") == "cover_art" }
            ?.obj()
            ?.obj("attributes")
            ?.string("fileName")
            ?: return null
        return "$UPLOADS/covers/$mangaId/$fileName.512.jpg"
    }

    private fun JsonObject.toChapter(): SourceChapter? {
        val chapterId = string("id") ?: return null
        val attributes = obj("attributes") ?: return null
        // Externally hosted chapters have no images on MangaDex; listing them would offer a
        // chapter the reader can never open.
        if (attributes.string("externalUrl") != null) return null
        val number = attributes.string("chapter")
        val title = attributes.string("title")
        return SourceChapter(
            id = chapterId,
            number = number ?: title ?: chapterId.take(8),
            title = title?.takeIf { it != number },
            publishedAt = attributes.string("publishAt").toIsoInstant(),
        )
    }

    /** `{"en": "...", "ar": "..."}`: the source's language first, then any available value. */
    private fun JsonObject.localized(language: String): String? {
        (this[language] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return values.firstNotNullOfOrNull { value ->
            (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }

    private fun String?.toStatus(): MangaStatus = when (this?.lowercase()) {
        "ongoing" -> MangaStatus.ONGOING
        "completed" -> MangaStatus.COMPLETED
        "hiatus" -> MangaStatus.HIATUS
        "cancelled" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    /** MangaDex timestamps use an explicit offset (`+00:00`), not always a trailing `Z`. */
    private fun String?.toIsoInstant(): Long? = this?.let { value ->
        runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(value).toEpochMilli() }
            .getOrNull()
    }

    private companion object {
        const val API = "https://api.mangadex.org"
        const val UPLOADS = "https://uploads.mangadex.org"
        const val PAGE_SIZE = 24
        const val CHAPTER_PAGE_SIZE = 500
        const val MAX_CHAPTER_PAGES = 6
    }
}

