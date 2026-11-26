package com.mangaos.app.data.repository

import android.util.Log
import com.mangaos.app.data.extension.MihonRuntime
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.repository.ExtensionRepository
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.SourceUnavailableException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Runs the real, installed Mihon sources. Every call goes through [MihonRuntime], which owns
 * the extension class loaders and the shared network stack.
 *
 * A failure in one source never hides the others: aggregate calls collect results per source
 * and report an error only when every source failed. Individual calls wrap extension
 * exceptions in [SourceUnavailableException] so screens can distinguish offline, network,
 * unsupported and incompatible sources.
 */
class ExtensionMangaRepository(
    private val extensions: ExtensionRepository,
) : MangaRepository {

    override suspend fun latest(sourceId: String, page: Int): List<Manga> = call(sourceId) { source ->
        source.getLatestUpdates(page).mangas.mapNotNull { manga ->
            manga.toDomain(source).takeIf { !it.isExplicit() }
        }
    }

    override suspend fun popular(sourceId: String, page: Int): List<Manga> = call(sourceId) { source ->
        source.getPopularManga(page).mangas.mapNotNull { manga ->
            manga.toDomain(source).takeIf { !it.isExplicit() }
        }
    }

    override suspend fun search(sourceId: String, query: String, page: Int): List<Manga> =
        call(sourceId) { source ->
            source.getSearchManga(page, query, source.filters()).mangas.mapNotNull { manga ->
                manga.toDomain(source).takeIf { !it.isExplicit() }
            }
        }

    override suspend fun catalogue(): List<Manga> {
        // The Manga tab is allowed to be empty; having no installed sources is a state, not an
        // error, and must never surface as an exception to the screen.
        if (extensions.sources.value.isEmpty()) return emptyList()
        return aggregate { source -> source.getPopularManga(1) }
    }

    override suspend fun searchAll(query: String, page: Int): List<Manga> {
        if (extensions.sources.value.isEmpty()) return emptyList()
        return aggregate { source -> source.getSearchManga(page, query, source.filters()) }
    }

    override suspend fun byId(sourceId: String, mangaId: String): Manga? = call(sourceId) { source ->
        val placeholder = SManga.create().apply {
            url = mangaId
            title = ""
        }
        val updated = source.getMangaUpdate(placeholder, emptyList(), true, false).manga
        if (updated.title.isBlank() && updated.url.isBlank()) {
            null
        } else {
            updated.toDomain(source).takeIf { !it.isExplicit() }
        }
    }

    override suspend fun chapters(sourceId: String, mangaId: String): List<Chapter> =
        call(sourceId) { source ->
            val placeholder = SManga.create().apply {
                url = mangaId
                title = ""
            }
            source.getMangaUpdate(placeholder, emptyList(), false, true)
                .chapters.map { it.toDomain(mangaId) }
        }

    override suspend fun pages(sourceId: String, mangaId: String, chapterId: String): List<String> =
        call(sourceId) { source ->
            val placeholder = SChapter.create().apply { url = chapterId }
            val baseUrl = (source as? HttpSource)?.baseUrl.orEmpty()
            source.getPageList(placeholder)
                .sortedBy { it.index }
                .mapNotNull { page ->
                    val resolved = page.imageUrl?.takeIf { it.isNotBlank() }
                        ?: runCatching { (source as? HttpSource)?.getImageUrl(page) }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                        ?: page.url.takeIf { it.isNotBlank() }
                    resolved?.let { absolute(it, baseUrl) }
                }
        }

    override fun imageHeaders(sourceId: String): Map<String, String> = MihonRuntime.headers(sourceId)

    private suspend fun <T> call(sourceId: String, block: suspend (Source) -> T): T {
        val source = MihonRuntime.source(sourceId)
            ?: throw SourceUnavailableException(
                ExtensionFailure.UNSUPPORTED,
                "Source $sourceId is not loaded. Install or re-enable its extension.",
            )
        return try {
            MihonRuntime.withSourceLock(sourceId) { block(source) }
        } catch (error: SourceUnavailableException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w("MangaSources", "Source $sourceId failed", error)
            throw SourceUnavailableException(error.toFailure(), error.message)
        }
    }

    /**
     * Queries every installed source concurrently. Result order follows the source registry;
     * sources that fail are skipped, and the call only fails when nothing succeeded.
     */
    private suspend fun aggregate(block: suspend (Source) -> MangasPage): List<Manga> {
        val infos = extensions.sources.value
            .filterNot { it.isNsfw }
            .distinctBy { it.id }
        if (infos.isEmpty()) return emptyList()

        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val semaphore = Semaphore(MAX_CONCURRENT_SOURCES)
        val pages = coroutineScope {
            infos.map { info ->
                async(Dispatchers.IO) {
                    try {
                        semaphore.withPermit {
                            val source = MihonRuntime.source(info.id) ?: return@withPermit emptyList()
                            withTimeout(SOURCE_TIMEOUT_MS) {
                                MihonRuntime.withSourceLock(info.id) { block(source) }
                            }.mangas.mapNotNull { manga ->
                                manga.toDomain(source).takeIf {
                                    it.id.isNotBlank() && !it.isExplicit()
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        failures += error
                        emptyList()
                    }
                }
            }.awaitAll()
        }

        val merged = pages.flatten().distinctBy { it.key }
        if (merged.isEmpty()) {
            val first = failures.firstOrNull()
            if (first != null) {
                throw SourceUnavailableException(first.toFailure(), first.message)
            }
        }
        return merged
    }

    private fun Source.filters(): FilterList =
        runCatching { getFilterList() }.getOrNull() ?: FilterList()

    private fun SManga.toDomain(source: Source): Manga {
        val baseUrl = (source as? HttpSource)?.baseUrl.orEmpty()
        return Manga(
            id = url,
            sourceId = source.id.toString(),
            title = title.ifBlank { url },
            description = description?.takeIf { it.isNotBlank() },
            genres = runCatching { getGenres() }.getOrNull().orEmpty(),
            status = status.toMangaStatus(),
            author = author?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            coverUrl = thumbnail_url?.let { absolute(it, baseUrl) },
            url = absolute(url, baseUrl),
            sourceName = source.name,
        )
    }

    /** Conservative per-work guard for MIXED extensions that also publish adult titles. */
    private fun Manga.isExplicit(): Boolean = genres.any { genre ->
        genre.trim().lowercase(Locale.ROOT) in EXPLICIT_GENRES
    }

    private fun SChapter.toDomain(mangaId: String): Chapter {
        val parsedNumber = chapter_number.takeIf { it >= 0f }?.let(::formatNumber)
            ?: NUMBER_PATTERN.find(name)?.groupValues?.getOrNull(1)
        val resolvedNumber = parsedNumber?.takeIf { it.isNotBlank() }
            ?: name.takeIf { it.isNotBlank() }
            ?: url
        return Chapter(
            id = url,
            mangaId = mangaId,
            number = resolvedNumber,
            title = scanlator?.takeIf { it.isNotBlank() },
            publishedAt = date_upload.takeIf { it > 0L },
        )
    }

    private fun Int.toMangaStatus(): MangaStatus = when (this) {
        SManga.ONGOING -> MangaStatus.ONGOING
        SManga.COMPLETED, SManga.PUBLISHING_FINISHED, SManga.LICENSED -> MangaStatus.COMPLETED
        SManga.CANCELLED -> MangaStatus.CANCELLED
        SManga.ON_HIATUS -> MangaStatus.HIATUS
        else -> MangaStatus.UNKNOWN
    }

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

    private fun absolute(value: String, baseUrl: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            baseUrl.isBlank() -> trimmed
            trimmed.startsWith("/") -> baseUrl.trimEnd('/') + trimmed
            else -> baseUrl.trimEnd('/') + "/" + trimmed
        }
    }

    private fun Throwable.toFailure(): ExtensionFailure = when (this) {
        is UnknownHostException, is ConnectException -> ExtensionFailure.OFFLINE
        is SocketTimeoutException, is InterruptedIOException -> ExtensionFailure.NETWORK
        is NoClassDefFoundError, is ClassNotFoundException -> ExtensionFailure.INCOMPATIBLE
        is UnsupportedOperationException -> ExtensionFailure.UNSUPPORTED
        is IOException -> ExtensionFailure.NETWORK
        else -> ExtensionFailure.NETWORK
    }

    private companion object {
        private const val SOURCE_TIMEOUT_MS = 30_000L
        private const val MAX_CONCURRENT_SOURCES = 6
        private val NUMBER_PATTERN = Regex("""(\d+(?:\.\d+)?)""")
        private val EXPLICIT_GENRES = setOf(
            "hentai",
            "erotica",
            "pornographic",
            "porn",
            "adult",
            "18+",
            "xxx",
            "smut",
        )
    }
}
