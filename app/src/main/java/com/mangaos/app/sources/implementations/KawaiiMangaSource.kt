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
import java.util.Calendar
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/**
 * Mangakawaii (mangakawaii.fr), a French site. Its pages list, its chapters list and its
 * reader are plain HTML; the chapter list is paginated by the site, so every page is followed
 * until one comes back empty.
 */
class KawaiiMangaSource : MangaSource {

    override val id: String = "mangakawaii"
    override val name: String = "Kawaii Manga"
    override val lang: String = "fr"
    override val baseUrl: String = "https://www.mangakawaii.fr"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> = list(page, "views", null)

    override suspend fun latest(page: Int): List<SourceManga> = list(page, "last_updated", "desc")

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl/recherche".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return cards(http.document(url.toString()))
    }

    private suspend fun list(page: Int, sort: String, dir: String?): List<SourceManga> {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .apply { if (dir != null) addQueryParameter("dir", dir) }
            .addQueryParameter("page", page.toString())
            .build()
        return cards(http.document(url.toString()))
    }

    private fun cards(document: Document): List<SourceManga> =
        document.select("""a.mk-card[href*="/manga/"]""").mapNotNull { element ->
            val image = element.selectFirst("img")
            val title = element.firstText("p.mk-display") ?: image?.attr("alt")?.trim()
            if (title.isNullOrBlank()) return@mapNotNull null
            val href = element.absUrl("href").ifBlank { element.attr("href") }
            val slug = href.trim('/').substringAfterLast("/")
            SourceManga(
                id = slug,
                title = title,
                coverUrl = image?.coverImage(),
                url = href.takeIf { it.startsWith("http") } ?: "$baseUrl/manga/$slug",
            )
        }.distinctBy { it.id }

    override suspend fun details(mangaId: String): SourceManga? {
        val url = mangaUrl(mangaId)
        val document = http.document(url)
        return parseDetails(document, mangaId)
    }

    private fun parseDetails(document: Document, mangaId: String): SourceManga? {
        val title = document.firstText("h1") ?: return null
        val authors = mutableListOf<String>()
        val artists = mutableListOf<String>()
        var inArtists = false
        document.selectFirst("p:has(i.fa-pen-nib)")?.children()?.forEach { node ->
            when {
                node.tagName() == "i" && node.hasClass("fa-paintbrush") -> inArtists = true
                node.tagName() == "a" -> (if (inArtists) artists else authors).add(node.text().trim())
            }
        }
        val description = document.firstText("[x-ref=desc]")
        val altNames = document.select("details p").getOrNull(1)?.text()?.trim()
        val statusText = document.select("details p").firstOrNull()?.text()
            ?.substringAfterLast("·")?.trim()?.lowercase(Locale.ROOT)
        return SourceManga(
            id = mangaId,
            title = title,
            coverUrl = document.selectFirst("img.shadow-2xl")?.coverImage(),
            url = mangaUrl(mangaId),
            description = buildString {
                description?.let { append(it) }
                altNames?.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Names: $it")
                }
            }.takeIf { it.isNotBlank() },
            genres = document.select("a[href*=genres]").map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            status = when (statusText) {
                "en cours" -> MangaStatus.ONGOING
                "terminé", "termine" -> MangaStatus.COMPLETED
                else -> MangaStatus.UNKNOWN
            },
            author = authors.distinct().joinToString(", ").takeIf { it.isNotBlank() },
            artist = artists.distinct().joinToString(", ").takeIf { it.isNotBlank() },
        )
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> {
        val document = http.document(mangaUrl(mangaId))
        val chaptersUrl = document.selectFirst("[data-url*=chapitres]")?.attr("abs:data-url")
            ?.takeIf { it.isNotBlank() }
            ?: "$baseUrl/manga/${mangaId.trim('/')}/chapitres"
        val chapters = ArrayList<SourceChapter>()
        collectChapters(document.select("div.ch-row"), chapters)
        var page = if (chapters.isEmpty()) 1 else 2
        val base = chaptersUrl.toHttpUrl()
        repeat(MAX_CHAPTER_PAGES) {
            val pageUrl = base.newBuilder().addQueryParameter("page", page.toString()).build()
            val pageRows = http.document(pageUrl.toString()).select("div.ch-row")
            if (pageRows.isEmpty()) return chapters
            collectChapters(pageRows, chapters)
            page++
        }
        return chapters
    }

    private fun collectChapters(rows: List<org.jsoup.nodes.Element>, out: MutableList<SourceChapter>) {
        rows.forEach { element ->
            val link = element.selectFirst("a.ch-num") ?: return@forEach
            val name = link.text().trim()
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            // The whole chapter path travels as the id: the reader page lives under it.
            val id = href.removePrefix(baseUrl).trim('/').ifBlank { name }
            out += SourceChapter(
                id = id,
                number = Regex("""Ch\.\s*(\d+(?:\.\d+)*)""").find(name)?.groupValues?.get(1)
                    ?: Regex("""\d+(?:\.\d+)*""").find(name)?.value
                    ?: name,
                title = name,
                publishedAt = parseRelativeDate(element.selectFirst("p")?.text().orEmpty()),
            )
        }
    }

    /** French relative dates ("il y a 3 jours") become real timestamps. */
    private fun parseRelativeDate(text: String): Long? {
        val clean = text.trim().lowercase(Locale.ROOT)
        if (clean.contains("instant") || clean.contains("à l'instant")) return System.currentTimeMillis()
        val match = Regex("""il y a (\d+)\s*([a-zéû.]+)""").find(clean) ?: return null
        val value = match.groupValues[1].toIntOrNull() ?: return null
        return Calendar.getInstance().apply {
            when (match.groupValues[2].trimEnd('.')) {
                "s", "sec", "seconde", "secondes" -> add(Calendar.SECOND, -value)
                "min", "minute", "minutes" -> add(Calendar.MINUTE, -value)
                "h", "heure", "heures" -> add(Calendar.HOUR_OF_DAY, -value)
                "j", "jour", "jours" -> add(Calendar.DATE, -value)
                "sem", "semaine", "semaines" -> add(Calendar.DATE, -value * 7)
                "mois" -> add(Calendar.MONTH, -value)
                "an", "ans", "année", "années", "annee", "annees" -> add(Calendar.YEAR, -value)
                else -> return null
            }
        }.timeInMillis
    }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val path = if (chapterId.startsWith("http")) chapterId else "$baseUrl/${chapterId.trim('/')}"
        val document = http.document(path)
        var state = document.selectFirst("[x-data*=imgs]")?.attr("x-data").orEmpty()
        var previous: String
        do {
            previous = state
            state = state
                .replace("\\\\", "\\")
                .replace("\\u0022", "\"")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
        } while (state != previous)
        val imagesJson = Regex(""""imgs":\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(state)?.groupValues?.get(1).orEmpty()
        val urls = runCatching {
            SourceHttp.JSON.parseToJsonElement("[$imagesJson]") as JsonArray
        }.getOrNull().orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content }
            .filter { "__mk_trap__" !in it }
            .distinct()
        if (urls.isNotEmpty()) return urls.map { SourcePage(it) }

        val inline = document.select("img[id^=pg-]").mapNotNull { image ->
            image.absUrl("src").takeIf { it.isNotBlank() }
        }
        if (inline.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return inline.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = mangaUrl(mangaId)

    private fun mangaUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl/manga/${mangaId.trim('/')}"

    private companion object {
        const val MAX_CHAPTER_PAGES = 30
    }
}
