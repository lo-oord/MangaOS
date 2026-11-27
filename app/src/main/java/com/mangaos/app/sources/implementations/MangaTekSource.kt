package com.mangaos.app.sources.implementations

import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.core.SourceChapter
import com.mangaos.app.sources.core.SourceException
import com.mangaos.app.sources.core.SourceManga
import com.mangaos.app.sources.core.SourcePage
import com.mangaos.app.sources.implementations.mangatek.MangaTekOverlay
import com.mangaos.app.sources.network.SourceHttp
import com.mangaos.app.sources.network.array
import com.mangaos.app.sources.network.coverImage
import com.mangaos.app.sources.network.firstText
import com.mangaos.app.sources.network.int
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.raw
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.toInstantMillis
import java.net.URLEncoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/**
 * MangaTek (mangatek.com), an Astro site: the listing is HTML, while a series page and the
 * reader embed their data in `props` attributes.
 *
 * A MangaTek page may carry its translated text separately from the image. Those bubbles are
 * kept with their page (see [MangaTekOverlay]) instead of being dropped, so the reader shows
 * the full page. A chapter whose page list is only completed by the site's reader script (it
 * answers `pending` entries) is refused with a clear message rather than shown half empty.
 */
class MangaTekSource : MangaSource {

    override val id: String = "mangatek"
    override val name: String = "MangaTek"
    override val lang: String = "ar"
    override val baseUrl: String = "https://mangatek.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> =
        listing("$baseUrl/manga-list?sort=views&page=$page")

    override suspend fun latest(page: Int): List<SourceManga> =
        listing("$baseUrl/manga-list?page=$page")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
            .addQueryParameter("search", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return listing(url.toString())
    }

    private suspend fun listing(url: String): List<SourceManga> {
        val document = http.document(url)
        return document.select(".flex-grow .grid a").mapNotNull { element ->
            val title = element.selectFirst("h3")?.attr("title")?.trim()
                ?: element.selectFirst("h3")?.text()?.trim()
                ?: return@mapNotNull null
            val href = element.absUrl("href").ifBlank { element.attr("href") }
            val slug = href.trim('/').substringAfterLast("/")
            if (slug.isBlank()) return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = element.selectFirst("img")?.coverImage(),
                url = "$baseUrl/manga/$slug",
            )
        }.distinctBy { it.id }
    }

    override suspend fun details(mangaId: String): SourceManga? {
        val slug = mangaId.trim('/').substringAfterLast("/")
        val document = http.document("$baseUrl/manga/$slug")
        val series = astroProp(document, "manga")?.obj("manga") ?: return null
        return series.toManga(slug)
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val slug = mangaId.trim('/').substringAfterLast("/")
        val document = http.document("$baseUrl/manga/$slug")
        val series = astroProp(document, "manga")?.obj("manga")
            ?: throw SourceException(SourceFailure.PARSING, "The series data is missing")
        val chapters = series.array("MangaChapters").orEmpty().mapNotNull { element ->
            val chapter = element.obj() ?: return@mapNotNull null
            val number = chapter.raw("chapter_number") ?: return@mapNotNull null
            val title = chapter.string("title")
            val isMachineTranslated = chapter.int("isAI") == 1
            SourceChapter(
                id = number,
                number = number,
                title = buildString {
                    append(title?.takeIf { it.isNotBlank() && it != "Chapter $number" } ?: "Chapter $number")
                    if (isMachineTranslated) append(" (مترجم)")
                },
                publishedAt = (chapter.string("created_at") ?: chapter.string("createdAt")).toInstantMillis(),
            )
        }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val slug = mangaId.trim('/').substringAfterLast("/")
        val document = http.document("$baseUrl/reader/$slug/$chapterId")
        val props = astroProp(document, "imageUrls") ?: throw SourceException(
            SourceFailure.PARSING,
            "The reader data is missing for chapter $chapterId",
        )

        val embedded = props.array("imageUrls").orEmpty().map { element ->
            when (element) {
                is JsonPrimitive -> ReaderImage(element.content, pending = false, trap = false)
                is JsonObject -> ReaderImage(
                    url = element.string("url").orEmpty(),
                    pending = element.boolValue("pending"),
                    trap = element.boolValue("isTrap"),
                )
                else -> ReaderImage("", pending = false, trap = false)
            }
        }
        if (embedded.any { it.pending }) {
            // The remaining pages are minted by the reader's own script (a signed request the
            // site builds in the browser); inventing an order here would lose pages.
            throw SourceException(
                SourceFailure.EMPTY,
                "MangaTek builds this chapter's pages inside its reader; they cannot be listed here",
            )
        }
        val imageUrls = embedded.filterNot { it.trap }.map { it.url }.filter { it.isNotBlank() }
        if (imageUrls.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")

        val overlays = overlaysFor(document, props)
        val offset = props.int("overlayPageOffset") ?: 0
        val byPage = overlays.associate { (offset + it.pageNumber - 1) to it.bubbles }

        return imageUrls.mapIndexed { index, imageUrl ->
            val bubbles = byPage[index]
            if (bubbles.isNullOrEmpty()) {
                SourcePage(imageUrl)
            } else {
                val json = MangaTekOverlay.encodeBubbles(bubbles)
                val encoded = URLEncoder.encode(json, "UTF-8")
                SourcePage("$imageUrl#${MangaTekOverlay.FRAGMENT_PREFIX}$encoded")
            }
        }
    }

    /** The page's translated bubbles: an embedded blob, or the reader's overlay answer. */
    private fun overlaysFor(document: Document, props: JsonObject): List<com.mangaos.app.sources.implementations.mangatek.PageOverlay> {
        val blob = props.string("overlayBlob")
        if (blob != null) {
            val pages = runCatching { MangaTekOverlay.decryptEmbedded(blob) }.getOrNull()
            if (!pages.isNullOrEmpty()) return pages
        }
        if (props.string("textMode") == "overlay") {
            // The site answers this chapter's translation only to a signed reader request.
            throw SourceException(
                SourceFailure.EMPTY,
                "MangaTek serves this chapter's translated text to its own reader only",
            )
        }
        return emptyList()
    }

    override fun imageHeaders(): Map<String, String> = mapOf(
        "Referer" to "$baseUrl/",
        "Accept" to "image/avif,image/webp,image/*,*/*;q=0.8",
    )

    override fun webUrl(mangaId: String): String = "$baseUrl/manga/${mangaId.trim('/')}"

    /**
     * Astro serialises props as `[{"title":[…]},…]` with two-element wrappers per value; the
     * reader's props are unwrapped the same way the extension does it.
     */
    private fun astroProp(document: Document, key: String): JsonObject? {
        val raw = document.selectFirst("[props*=$key]")?.attr("props") ?: return null
        val element = runCatching {
            SourceHttp.JSON.parseToJsonElement(raw)
        }.getOrNull() ?: return null
        return unwrap(element) as? JsonObject
    }

    private fun unwrap(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> {
            if (element.size == 2 && element[0] is JsonPrimitive) {
                unwrap(element[1])
            } else {
                JsonArray(element.map { unwrap(it) })
            }
        }
        is JsonObject -> JsonObject(element.mapValues { unwrap(it.value) })
        else -> element
    }

    private fun JsonObject.boolValue(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.toManga(slug: String): SourceManga? {
        val title = string("title") ?: return null
        val tags = array("Tags").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.string("name")
        }
        val author = string("author")?.takeUnless { it.equals("unknown", true) }
        return SourceManga(
            id = slug,
            title = title,
            coverUrl = string("cover_image"),
            url = "$baseUrl/manga/$slug",
            description = string("description"),
            genres = tags,
            status = when (string("status")?.lowercase()) {
                "ongoing" -> MangaStatus.ONGOING
                "completed" -> MangaStatus.COMPLETED
                "hiatus" -> MangaStatus.HIATUS
                "cancelled", "dropped" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            },
            author = author,
            updatedAt = firstTextOrNull("updated_at"),
        )
    }

    private fun JsonObject.firstTextOrNull(key: String): Long? = string(key).toInstantMillis()

    private data class ReaderImage(val url: String, val pending: Boolean, val trap: Boolean)
}
