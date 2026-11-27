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
import com.mangaos.app.sources.network.coverImage
import com.mangaos.app.sources.network.firstText
import com.mangaos.app.sources.network.parseEnglishDate
import com.mangaos.app.sources.network.toInstantMillis
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Thunder Scans, a WordPress "MangaThemesia" site: the catalogue, the series page and the
 * chapter list are plain HTML, and the reader publishes its pages inside a `ts_reader.run({…})`
 * call, not in the markup. The reader payload is therefore parsed as JSON.
 */
class ThunderScansSource : MangaSource {

    override val id: String = "thunderscans"
    override val name: String = "Thunder Scans"
    override val lang: String = "en"
    override val baseUrl: String = "https://en-thunderscans.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> = listing(page, order = "popular")

    override suspend fun latest(page: Int): List<SourceManga> = listing(page, order = "latest")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query.trim())
            .addQueryParameter("post_type", "wp-manga")
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        return cards(http.document(url.toString()))
    }

    /** Browse pages of the theme: `/comics/?order=popular|latest&page=N`. */
    private suspend fun listing(page: Int, order: String): List<SourceManga> {
        val url = "$baseUrl/comics/".toHttpUrl().newBuilder()
            .addQueryParameter("order", order)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        return cards(http.document(url.toString()))
    }

    private fun cards(document: Document): List<SourceManga> =
        document.select(".listupd .bs .bsx, .listo .bs .bsx, .utao .uta .imgu").mapNotNull { element ->
            val link = element.selectFirst("a[href]") ?: return@mapNotNull null
            val title = link.attr("title").trim().ifEmpty { link.text().trim() }
            if (title.isBlank()) return@mapNotNull null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val slug = href.trim('/').substringAfterLast("/")
            if (slug.isBlank()) return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = element.selectFirst("img")?.coverImage(),
                url = href.takeIf { it.startsWith("http") } ?: "$baseUrl/$slug/",
            )
        }.distinctBy { it.id }

    override suspend fun details(mangaId: String): SourceManga? {
        val document = http.document(mangaUrl(mangaId))
        val details = document.selectFirst(
            "div.bigcontent, div.animefull, div.main-info, div.postbody",
        ) ?: return null
        val title = details.firstText(".entry-title", ".ts-breadcrumb li:last-child span")
            ?: document.firstText("h1.entry-title")
            ?: return null
        val alt = details.selectFirst(".alternative, .alter, .seriestualt, .wd-full")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "-" }
        val author = details.selectFirst(
            ".author-content a, .ts-author a, .auth a, .imptdt:contains(Author) i",
        )?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val statusText = details.select(".post-content_item .summary-content, .imptdt")
            .joinToString(" ") { it.text() }
        val genres = details.select("div.gnr a, .mgen a, .seriestugenre a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return SourceManga(
            id = mangaId.trim('/'),
            title = title,
            coverUrl = details.selectFirst(".infomanga img, .thumb img, div.summary_image img")
                ?.coverImage(),
            url = mangaUrl(mangaId),
            description = buildString {
                details.firstText(".desc, .entry-content[itemprop=description], .summary__content")
                    ?.let { append(it) }
                alt?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Names: $it")
                }
            }.takeIf { it.isNotBlank() },
            genres = genres,
            status = parseStatus(statusText),
            author = author,
        )
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val document = http.document(mangaUrl(mangaId))
        val chapters = document
            .select("div.bxcl li, div.cl li, #chapterlist li, ul li:has(div.chbox):has(div.eph-num)")
            .mapNotNull { element -> element.toChapter() }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    private fun Element.toChapter(): SourceChapter? {
        val link = selectFirst("a[href]") ?: return null
        // Locked chapters have no reader link; they are marked and skipped instead of failing.
        if (link.attr("href").startsWith("#")) return null
        val href = link.absUrl("href").ifBlank { link.attr("href") }
        val chapterPath = href.substringAfter(baseUrl, href).trim('/')
        val label = firstText(".lch a, .chapternum") ?: link.text().trim()
        val number = Regex("""(?i)chapter\s*(\d+(?:\.\d+)*)""").find(label)?.groupValues?.get(1)
            ?: Regex("""\d+(?:\.\d+)*""").find(label)?.value
            ?: chapterPath
        val date = firstText(".chapterdate")?.parseEnglishDate()
        return SourceChapter(
            id = chapterPath.ifBlank { href },
            number = number,
            title = label.takeIf { it.isNotBlank() && it != number },
            publishedAt = date,
        )
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = if (chapterId.startsWith("http")) chapterId else "$baseUrl/${chapterId.trim('/')}/"
        val document = http.document(url)
        val inline = document.select("#readerarea img[src]").mapNotNull { image ->
            image.absUrl("src").takeIf { it.isNotBlank() }
        }
        if (inline.isNotEmpty()) return inline.map { SourcePage(it) }

        // The reader's own payload: ts_reader.run({"sources":[{"images":[…]}], …})
        val script = document.select("script").firstOrNull { it.data().contains("ts_reader.run(") }
            ?.data()
            ?: throw SourceException(SourceFailure.PARSING, "The reader payload is missing")
        val json = script.substringAfter("ts_reader.run(", "").substringBeforeLast(")")
            .trim()
            .takeIf { it.startsWith("{") }
            ?: throw SourceException(SourceFailure.PARSING, "The reader payload is not JSON")
        val payload = http.parseJson(json, url).jsonObject
        val images = payload["sources"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("images")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.takeIf { urlPart -> urlPart.isNotBlank() } }
            .orEmpty()
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it.replace("\\/", "/")) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = mangaUrl(mangaId)

    private fun mangaUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl/comics/${mangaId.trim('/')}/"

    private fun parseStatus(text: String): MangaStatus = when {
        text.contains("ongoing", true) || text.contains("مستمر", true) -> MangaStatus.ONGOING
        text.contains("completed", true) || text.contains("مكتمل", true) -> MangaStatus.COMPLETED
        text.contains("hiatus", true) || text.contains("on hold", true) -> MangaStatus.HIATUS
        text.contains("cancel", true) || text.contains("dropped", true) -> MangaStatus.CANCELLED
        else -> MangaStatus.UNKNOWN
    }

    /** Exposed for unit tests: a recorded reader payload. */
    internal fun parseReaderPayload(json: String): List<String> {
        val payload: JsonObject = http.parseJson(json, baseUrl).jsonObject
        return payload["sources"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("images")?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content }
            .orEmpty()
    }
}
