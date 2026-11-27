package com.mangaos.app.data.repository

import android.util.Log
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.SourceRepository
import com.mangaos.app.domain.repository.SourceUnavailableException
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.core.SourceCapability
import com.mangaos.app.sources.core.SourceChapter
import com.mangaos.app.sources.core.SourceException
import com.mangaos.app.sources.core.SourceManga
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Runs the bundled sources.
 *
 * Each call is isolated: a source that is slow, broken or offline never hides the others, and
 * a disabled source is refused for new requests while the data it already produced stays in
 * the library, history and downloads. Nothing is cached here and nothing is invented; a
 * failure is reported as a failure.
 */
class NativeMangaRepository(
    private val sources: SourceRepository,
) : MangaRepository {

    override suspend fun latest(sourceId: String, page: Int): List<Manga> =
        requireCapability(sourceId, SourceCapability.LATEST) { source ->
            source.latest(page)
        }

    override suspend fun popular(sourceId: String, page: Int): List<Manga> =
        requireCapability(sourceId, SourceCapability.POPULAR) { source ->
            source.popular(page)
        }

    override suspend fun search(sourceId: String, query: String, page: Int): List<Manga> =
        requireCapability(sourceId, SourceCapability.SEARCH) { source ->
            source.search(query, page)
        }

    private suspend fun requireCapability(
        sourceId: String,
        capability: SourceCapability,
        block: suspend (MangaSource) -> List<SourceManga>,
    ): List<Manga> {
        val source = requireSource(sourceId)
        if (capability !in source.capabilities) {
            throw SourceUnavailableException(
                SourceFailure.UNSUPPORTED,
                "${source.name} does not support ${capability.name.lowercase()}",
            )
        }
        return call(sourceId, block = block).mapNotNull { it.toDomain(source) }
    }

    override suspend fun catalogue(): List<Manga> = aggregate { source ->
        if (SourceCapability.POPULAR !in source.capabilities) emptyList() else source.popular(1)
    }

    override suspend fun searchAll(query: String, page: Int): List<Manga> = aggregate { source ->
        if (SourceCapability.SEARCH !in source.capabilities) {
            emptyList()
        } else {
            source.search(query, page)
        }
    }

    override suspend fun byId(sourceId: String, mangaId: String): Manga? {
        val source = requireSource(sourceId)
        val manga = call(sourceId) { it.details(mangaId) } ?: return null
        return manga.toDomain(source)
    }

    override suspend fun chapters(sourceId: String, mangaId: String): List<Chapter> {
        val source = requireSource(sourceId)
        return call(sourceId) { it.chapters(mangaId) }.map { it.toDomain(mangaId) }
    }

    override suspend fun latestChapter(sourceId: String, mangaId: String): Chapter? {
        val source = requireSource(sourceId)
        val all = call(sourceId) { it.chapters(mangaId) }
        val newest = all.maxWithOrNull(compareBy<SourceChapter> { it.numberValue() }
            .thenBy { it.publishedAt ?: 0L })
            ?: return null
        return newest.toDomain(mangaId)
    }

    override suspend fun pages(sourceId: String, mangaId: String, chapterId: String): List<String> {
        requireSource(sourceId)
        return call(sourceId) { it.pages(mangaId, chapterId) }.map { it.url }
            .filter { it.isNotBlank() }
    }

    override fun imageHeaders(sourceId: String): Map<String, String> =
        sources.source(sourceId)?.imageHeaders().orEmpty()

    private fun requireSource(sourceId: String): MangaSource {
        val source = sources.source(sourceId)
            ?: throw SourceUnavailableException(SourceFailure.UNSUPPORTED, "Unknown source $sourceId")
        if (!sources.isEnabled(sourceId)) {
            throw SourceUnavailableException(
                SourceFailure.UNSUPPORTED,
                "Source ${source.name} is disabled",
            )
        }
        return source
    }

    private suspend fun <T> call(sourceId: String, block: suspend (MangaSource) -> T): T {
        val source = requireSource(sourceId)
        return try {
            val result = withTimeout(SOURCE_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { block(source) }
            }
            sources.reportFailure(sourceId, null)
            result
        } catch (error: TimeoutCancellationException) {
            val failure = SourceUnavailableException(SourceFailure.NETWORK, "Source timed out")
            Log.w(TAG, "Source $sourceId timed out", error)
            sources.reportFailure(sourceId, failure.message)
            throw failure
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceException) {
            val failure = SourceUnavailableException(error.failure, error.message)
            Log.w(TAG, "Source $sourceId failed", error)
            sources.reportFailure(sourceId, error.message)
            throw failure
        } catch (error: Throwable) {
            val mapped = error.toFailure()
            val failure = SourceUnavailableException(mapped, error.message)
            Log.w(TAG, "Source $sourceId failed", error)
            sources.reportFailure(sourceId, error.message)
            throw failure
        }
    }

    /**
     * Queries every enabled source concurrently. Result order follows the registry, a failing
     * source is skipped, and the call fails only when nothing succeeded.
     */
    private suspend fun aggregate(block: suspend (MangaSource) -> List<SourceManga>): List<Manga> {
        val infos = sources.enabledSources.value.distinctBy { it.id }
        if (infos.isEmpty()) return emptyList()

        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val semaphore = Semaphore(MAX_CONCURRENT_SOURCES)
        val pages = coroutineScope {
            infos.map { info ->
                async(Dispatchers.IO) {
                    try {
                        semaphore.withPermit {
                            val source = sources.source(info.id) ?: return@withPermit emptyList()
                            val found = withTimeout(SOURCE_TIMEOUT_MS) { block(source) }
                            found.mapNotNull { it.toDomain(source) }
                        }
                    } catch (error: TimeoutCancellationException) {
                        failures += error
                        sources.reportFailure(info.id, "Source timed out")
                        emptyList()
                    } catch (error: CancellationException) {
                        if (!currentCoroutineContext().isActive) throw error
                        failures += error
                        emptyList()
                    } catch (error: Throwable) {
                        failures += error
                        sources.reportFailure(info.id, error.message)
                        emptyList()
                    }
                }
            }.awaitAll()
        }

        val merged = pages.flatten().distinctBy { it.key }
        if (merged.isEmpty()) {
            val first = failures.firstOrNull()
            if (first != null) {
                throw SourceUnavailableException(
                    (first as? SourceUnavailableException)?.failure ?: first.toFailure(),
                    first.message,
                )
            }
        }
        return merged
    }

    private fun SourceManga.toDomain(source: MangaSource): Manga? {
        if (id.isBlank() || title.isBlank()) return null
        val manga = Manga(
            id = id,
            sourceId = source.id,
            title = title,
            description = description?.takeIf { it.isNotBlank() },
            genres = genres,
            status = status,
            rating = rating,
            author = author?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            coverUrl = coverUrl?.takeIf { it.isNotBlank() },
            latestChapter = latestChapter?.takeIf { it.isNotBlank() },
            latestChapterId = latestChapterId?.takeIf { it.isNotBlank() },
            updatedAt = updatedAt,
            url = url ?: source.webUrl(id),
            sourceName = source.name,
        )
        return manga.takeIf { !it.isExplicit() }
    }

    private fun SourceChapter.toDomain(mangaId: String): Chapter = Chapter(
        id = id,
        mangaId = mangaId,
        number = number.ifBlank { id },
        title = title?.takeIf { it.isNotBlank() && it != number },
        publishedAt = publishedAt,
    )

    /** Conservative guard for sources that also publish adult titles. */
    private fun Manga.isExplicit(): Boolean = genres.any { genre ->
        genre.trim().lowercase(Locale.ROOT) in EXPLICIT_GENRES
    }

    private fun SourceChapter.numberValue(): Float =
        NUMBER_PATTERN.find(number)?.value?.toFloatOrNull() ?: -1f

    private fun Throwable.toFailure(): SourceFailure = when (this) {
        is UnknownHostException, is ConnectException -> SourceFailure.OFFLINE
        is SocketTimeoutException, is InterruptedIOException -> SourceFailure.NETWORK
        is SourceException -> failure
        is IOException -> SourceFailure.NETWORK
        else -> SourceFailure.NETWORK
    }

    private companion object {
        private const val TAG = "MangaSources"
        private const val SOURCE_TIMEOUT_MS = 25_000L
        private const val MAX_CONCURRENT_SOURCES = 4
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
