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
import com.mangaos.app.sources.network.coverImage
import com.mangaos.app.sources.network.firstText
import com.mangaos.app.sources.network.obj
import com.mangaos.app.sources.network.parseEnglishDate
import com.mangaos.app.sources.network.string
import com.mangaos.app.sources.network.stringList
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/**
 * MangaTik (mangatik.com). The catalogue is HTML, while each series page publishes its own
 * JSON-LD block: that block is the authoritative source for the title, description, cover,
 * genres and author, so it is used instead of guessing from the markup.
 */
class MangaTikSource : MangaSource {

    override val id: String = "mangatik"
    override val name: String = "Manga Tik"
    override val lang: String = "ar"
    override val baseUrl: String = "https://mangatik.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> = cards(page, "views")

    override suspend fun latest(page: Int): List<SourceManga> {
        val url = "$baseUrl/latest".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return cards(http.document(url.toString()))
    }

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return cards(http.document(url.toString()))
    }

    private suspend fun cards(page: Int, sort: String): List<SourceManga> {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .build()
        return cards(http.document(url.toString()))
    }

    private fun cards(document: Document): List<SourceManga> =
        document.select("article.manga-card").mapNotNull { article ->
            val link = article.selectFirst(".manga-title > a[href]") ?: return@mapNotNull null
            val title = link.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val slug = href.trim('/').substringAfterLast("/")
            if (slug.isBlank()) return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = article.selectFirst("img.manga-poster")?.coverImage(),
                url = "$baseUrl/manga/$slug",
            )
        }.distinctBy { it.id }

    override suspend fun details(mangaId: String): SourceManga? {
        val document = http.document(mangaUrl(mangaId))
        return parseDetails(document, mangaId)
    }

    private fun parseDetails(document: Document, mangaId: String): SourceManga? {
        val raw = document.selectFirst("script[type=application/ld+json]")?.data()
            ?: return null
        val json = runCatching { SourceHttp.JSON.parseToJsonElement(raw).obj() }.getOrNull()
            ?: return null
        val title = json.string("name") ?: return null
        val author = json.obj("author")?.string("name")
            ?.takeUnless { it.isBlank() || it.equals("unknown", true) || it.equals("updating", true) }
        val badge = document.select("span.badge").map { it.text().trim() }.firstNotNullOfOrNull { text ->
            when (text) {
                "مستمر" -> MangaStatus.ONGOING
                "مكتمل" -> MangaStatus.COMPLETED
                "متوقف" -> MangaStatus.HIATUS
                else -> null
            }
        } ?: MangaStatus.UNKNOWN
        val alt = json.string("alternateName")?.takeIf { it.isNotBlank() }
        return SourceManga(
            id = mangaId.trim('/'),
            title = title,
            coverUrl = json.obj("image")?.string("url") ?: document.coverImage(),
            url = mangaUrl(mangaId),
            description = buildString {
                json.string("description")?.takeIf { it.isNotBlank() }?.let { append(it.trim()) }
                alt?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("عناوين بديلة: $it")
                }
            }.takeIf { it.isNotBlank() },
            genres = json.stringList("genre"),
            status = badge,
            author = author,
        )
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val document = http.document(mangaUrl(mangaId))
        val chapters = document.select("a.chapter-item-card").mapNotNull { link ->
            // The attribute carries "115.0" for whole numbers; the list never shows the ".0".
            val number = link.attr("data-chapter").trim()
                .removeSuffix(".0")
                .ifBlank { link.attr("data-chapter").trim() }
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val path = href.removePrefix(baseUrl).trim('/').ifBlank { href }
            val spans = link.select("span").eachText()
            val label = spans.firstOrNull { it.trim().startsWith("الفصل") }?.trim()
                ?: "الفصل $number"
            SourceChapter(
                id = path,
                number = number.ifBlank { path },
                title = label,
                publishedAt = spans.lastOrNull()?.trim()?.parseEnglishDate()
                    ?: spans.lastOrNull()?.trim()?.toDateTimeMillis(),
            )
        }
        if (chapters.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This series has no chapters")
        return chapters
    }

    /** The site prints chapter dates as `yyyy-MM-dd`. */
    private fun String.toDateTimeMillis(): Long? {
        val match = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(this) ?: return null
        return runCatching {
            java.time.LocalDate.of(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[3].toInt(),
            ).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = if (chapterId.startsWith("http")) chapterId else "$baseUrl/${chapterId.trim('/')}/"
        val document = http.document(url)
        val images = document.select("img.webtoon-image").mapNotNull { image ->
            image.coverImage()?.takeIf { it.isNotBlank() }
        }
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = mangaUrl(mangaId)

    private fun mangaUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl/manga/${mangaId.trim('/')}"

    /** Exposed for unit tests: one recorded JSON-LD object, no network. */
    internal fun mapDetails(json: JsonObject): String? = json.string("name")
}
