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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

/**
 * Team X (olympustaff.com), an Arabic series site. The chapter list is paginated by the site;
 * every page of it is requested and merged so a series with hundreds of chapters is complete.
 */
class TeamXSource : MangaSource {

    override val id: String = "teamx"
    override val name: String = "السيرفر الاحتياطي"
    override val lang: String = "ar"
    override val baseUrl: String = "https://olympustaff.com"
    override val capabilities: Set<SourceCapability> = setOf(
        SourceCapability.LATEST,
        SourceCapability.POPULAR,
        SourceCapability.SEARCH,
    )

    private val http = SourceHttp(SourceHttp.ANDROID_USER_AGENT)

    override suspend fun popular(page: Int): List<SourceManga> =
        cards(http.document("$baseUrl/series/${pageSuffix(page)}"))

    override suspend fun latest(page: Int): List<SourceManga> {
        val document = http.document("$baseUrl/${pageSuffix(page)}")
        return document.select("div.last-chapter div.box").mapNotNull { element ->
            val link = element.selectFirst("div.info a[href]") ?: return@mapNotNull null
            val title = link.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val slug = href.trim('/').substringAfterLast("/")
            if (slug.isBlank()) return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = element.selectFirst("div.imgu img")?.coverImage()
                    ?.replace("thumbnail_", ""),
                url = href.takeIf { it.startsWith("http") } ?: "$baseUrl/series/$slug",
            )
        }
    }

    override suspend fun search(query: String, page: Int): List<SourceManga> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query.trim())
            .build()
        val document = http.document(url.toString())
        return document.select("div.tx-grid a.tx-card").mapNotNull { element ->
            val title = element.firstText("h3") ?: return@mapNotNull null
            val href = element.absUrl("href").ifBlank { element.attr("href") }
            val slug = href.trim('/').substringAfterLast("/")
            if (slug.isBlank()) return@mapNotNull null
            SourceManga(
                id = slug,
                title = title,
                coverUrl = element.selectFirst("img")?.coverImage()?.replace("thumbnail_", ""),
                url = href.takeIf { it.startsWith("http") } ?: "$baseUrl/series/$slug",
            )
        }
    }

    private fun cards(document: Document): List<SourceManga> =
        document.select("div.listupd div.bsx").mapNotNull { element ->
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
                url = href.takeIf { it.startsWith("http") } ?: "$baseUrl/series/$slug",
            )
        }.distinctBy { it.id }

    override suspend fun details(mangaId: String): SourceManga? {
        val document = http.document(mangaUrl(mangaId))
        val title = document.firstText("div.author-info-title h1") ?: return null
        // The sidebar lists label/value pairs: "الحالة:", "متروك", "الرسام:", …
        val info = document.select(".full-list-info > small").map { it.text().trim() }
        fun valueAfter(label: String): String? {
            val index = info.indexOfFirst { it.trimEnd(':', '：') == label }
            return info.getOrNull(index + 1)?.trim()?.takeIf { it.isNotEmpty() && it != "غير معروف" }
        }
        val statusText = valueAfter("الحالة")
        val author = valueAfter("الرسام")
        return SourceManga(
            id = mangaId.trim('/'),
            title = title,
            coverUrl = document.selectFirst("div.text-right img")?.coverImage()
                ?: document.selectFirst("div.thumb img, img[itemprop=image]")?.coverImage()
                ?: document.coverImage(),
            url = mangaUrl(mangaId),
            description = document.select("div.review-content").text().trim().takeIf { it.isNotBlank() },
            genres = document.select("div.review-author-info a").map { it.text().trim() }
                .filter { it.isNotEmpty() },
            status = when (statusText?.trim()) {
                "مستمرة", "قادم قريبًا", "مستمر" -> MangaStatus.ONGOING
                "مكتمل", "مكتملة" -> MangaStatus.COMPLETED
                "متوقف" -> MangaStatus.HIATUS
                "متروك", "ملغي" -> MangaStatus.CANCELLED
                else -> MangaStatus.UNKNOWN
            },
            author = author,
        )
    }

    override suspend fun chapters(mangaId: String): List<SourceChapter> = coroutineScope {
        val document = http.document(mangaUrl(mangaId))
        val firstPage = chaptersOf(document)
        val lastPage = document.select("ul.pagination a.page-link")
            .mapNotNull { it.text().trim().toIntOrNull() }
            .maxOrNull() ?: 1
        if (lastPage <= 1) return@coroutineScope firstPage
        val base = mangaUrl(mangaId)
        val rest = (2..lastPage).map { page ->
            async { chaptersOf(http.document("$base?page=$page")) }
        }.awaitAll()
        (firstPage + rest.flatten()).distinctBy { it.id }
    }

    private fun chaptersOf(document: Document): List<SourceChapter> =
        document.select("div.chapter-card").mapNotNull { element ->
            if (element.selectFirst("span.locked") != null) return@mapNotNull null
            val link = element.selectFirst("a[href]") ?: return@mapNotNull null
            val number = element.attr("data-number").trim()
            val rawTitle = element.firstText("div.chapter-info div.chapter-title")
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val path = href.removePrefix(baseUrl).ifBlank { href }
            SourceChapter(
                id = path.trim('/'),
                number = number.ifBlank { path },
                title = buildString {
                    append("الفصل ")
                    append(number)
                    if (!rawTitle.isNullOrEmpty() &&
                        rawTitle != number &&
                        rawTitle != "الفصل $number" &&
                        rawTitle != "الفصل رقم $number"
                    ) {
                        append(" - ").append(rawTitle)
                    }
                },
                publishedAt = element.attr("data-date").trim().toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.times(1000L),
            )
        }

    override suspend fun pages(mangaId: String, chapterId: String): List<SourcePage> {
        val url = if (chapterId.startsWith("http")) chapterId else "$baseUrl/${chapterId.trim('/')}"
        val document = http.document(url)
        val images = document.select("div.image_list canvas[data-src], div.image_list img[src]")
            .mapNotNull { element ->
                val value = when {
                    element.hasAttr("src") && element.attr("src").isNotBlank() -> element.absUrl("src")
                    else -> element.absUrl("data-src")
                }
                value.takeIf { it.isNotBlank() && !it.startsWith("data:") }
            }
        if (images.isEmpty()) throw SourceException(SourceFailure.EMPTY, "This chapter has no pages")
        return images.map { SourcePage(it) }
    }

    override fun imageHeaders(): Map<String, String> = mapOf("Referer" to "$baseUrl/")

    override fun webUrl(mangaId: String): String = mangaUrl(mangaId)

    private fun mangaUrl(mangaId: String): String =
        if (mangaId.startsWith("http")) mangaId else "$baseUrl/series/${mangaId.trim('/')}"

    private fun pageSuffix(page: Int): String = if (page > 1) "?page=$page" else ""
}
