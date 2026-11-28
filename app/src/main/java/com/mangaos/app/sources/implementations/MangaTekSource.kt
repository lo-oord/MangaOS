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
                genres = cardGenres(element),
                status = cardStatus(element),
                // The listing prints the newest chapter on the card. It is read so the Latest
                // feed can order works whose card carries no date, instead of inventing one.
                latestChapter = cardChapterNumber(element.text()),
            )
        }.distinctBy { it.id }
    }

    /** Card chips, when the listing prints them: genre links and the state badge. */
    private fun cardGenres(element: org.jsoup.nodes.Element): List<String> =
        element.select("a[href*=genre], a[href*=tag], .genre, .badge")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() && it.length <= 24 && statusOf(it) == MangaStatus.UNKNOWN }
            .distinct()

    private fun cardStatus(element: org.jsoup.nodes.Element): MangaStatus {
        val badge = statusOf(element.select(".badge, .status").text().trim())
        if (badge != MangaStatus.UNKNOWN) return badge
        val text = element.text()
        val label = listOf("مستمر", "مكتمل", "متوقف", "ملغي").firstOrNull { text.contains(it) }
        return statusOf(label.orEmpty())
    }

    /** "الفصل 12.5" / "Chapter 12" as the card prints it; nothing is returned when absent. */
    private fun cardChapterNumber(text: String): String? =
        CHAPTER_PATTERN.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    private fun statusOf(value: String): MangaStatus = when (value.trim().lowercase()) {
        "مستمر", "ongoing" -> MangaStatus.ONGOING
        "مكتمل", "completed" -> MangaStatus.COMPLETED
        "متوقف", "hiatus" -> MangaStatus.HIATUS
        "ملغي", "cancelled", "dropped" -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
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
            val createdAt = (chapter.string("created_at") ?: chapter.string("createdAt")).orEmpty()
            // The same chapter number can appear more than once (the original and the machine
            // translated release). A number alone is therefore not an identity: the creation
            // time keeps every release distinct, so page counts, progress and the reader's
            // chapter index never point at another release with the same number.
            SourceChapter(
                id = if (createdAt.isBlank()) number else "$number@$createdAt",
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
        // The id carries the release's creation time (see [chapters]); the reader URL only
        // needs the chapter number.
        val number = chapterId.substringBefore("@")
        val document = http.document("$baseUrl/reader/$slug/$number")
        val props = astroProp(document, "imageUrls")

        val embedded = props?.array("imageUrls").orEmpty().map { element ->
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
        // A decoy page (the site's trap) is dropped. A page the site marks as pending still
        // carries a usable URL, and dropping it used to shorten a chapter to nothing; it is
        // kept so the whole chapter is readable.
        var imageUrls = embedded
            .filterNot { it.trap }
            .map { resolveUrl(it.url) }
            .filter { it.isNotBlank() }
            .distinct()
        // The reader markup is the authority on how many pages the chapter has: some chapters
        // publish no props at all, and others publish a props list that stops before pages the
        // markup still renders (lazily loaded images). When the markup holds more distinct pages
        // than the props and contains all of them, its order is used so no page is dropped.
        val fromMarkup = domPages(document)
        if (imageUrls.isEmpty()) {
            imageUrls = fromMarkup
        } else if (fromMarkup.size > imageUrls.size && imageUrls.all { it in fromMarkup }) {
            imageUrls = fromMarkup
        }
        if (imageUrls.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")

        // A chapter whose translated text lives in a separate layer that only the site's own
        // reader can unlock is shown with its original image, never refused: the artwork is
        // the chapter, and a missing text layer must not hide it.
        val overlays = if (props == null) emptyList() else {
            runCatching { overlaysFor(document, props) }.getOrDefault(emptyList())
        }
        val offset = props?.int("overlayPageOffset") ?: 0
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
        // `textMode == "overlay"` means the translation is answered only to the site's signed
        // reader. The page images are still the real chapter, so no overlay is painted and the
        // page is shown as published.
        return emptyList()
    }

    /**
     * Pages rendered into the reader markup. A page image may be deferred: the real address then
     * lives in `data-src`, `data-original`, `data-lazy-src` or the first `srcset` candidate, while
     * `src` is a placeholder. Reading only `src` used to collect a chapter with missing pages.
     */
    private fun domPages(document: Document): List<String> =
        document.select("main img, [data-reader] img, .reader img, img[src], img[data-src]")
            .mapNotNull { image ->
                val candidates = listOf(
                    image.absUrl("src"),
                    image.attr("src"),
                    image.absUrl("data-src"),
                    image.attr("data-src"),
                    image.absUrl("data-original"),
                    image.attr("data-original"),
                    image.absUrl("data-lazy-src"),
                    image.attr("data-lazy-src"),
                    image.attr("srcset").substringBefore(',').substringBefore(' ').trim(),
                )
                candidates.asSequence()
                    .map { resolveUrl(it) }
                    .firstOrNull { url ->
                        url.startsWith("http") &&
                            !DOM_LOGO.containsMatchIn(url) &&
                            !PLACEHOLDER_IMAGE.containsMatchIn(url)
                    }
            }
            .distinct()

    override fun imageHeaders(): Map<String, String> = mapOf(
        "Referer" to "$baseUrl/",
        "Origin" to baseUrl,
        "Accept" to "image/avif,image/webp,image/*,*/*;q=0.8",
    )

    /** Pages come as absolute URLs, but a leading `/` or a protocol-relative host is resolved. */
    private fun resolveUrl(url: String): String = when {
        url.isBlank() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

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

    private companion object {
        val DOM_LOGO = Regex("""(logo|favicon|avatar|icon|banner|sponsor)""", RegexOption.IGNORE_CASE)

        /** Spinner and skeleton resources that are never a chapter page. */
        val PLACEHOLDER_IMAGE = Regex(
            """(placeholder|spinner|loading|blank|1x1|transparent)""",
            RegexOption.IGNORE_CASE,
        )

        val CHAPTER_PATTERN = Regex(
            """(?:الفصل|فصل|Chapter|chapter|CH\.?|ch\.?)\s*#?\s*(\d+(?:\.\d+)?)""",
        )
    }
}
