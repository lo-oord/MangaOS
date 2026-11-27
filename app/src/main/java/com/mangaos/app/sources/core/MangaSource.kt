package com.mangaos.app.sources.core

import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.SourceFailure

/**
 * One work as returned by a native source. [id] is the source's own stable identifier: a
 * MangaDex UUID, an Asura slug or a MangaClub slug. It is only unique together with the
 * source id, which is why the app always keys a work as `sourceId/id`.
 */
data class SourceManga(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val url: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val rating: Double? = null,
    val author: String? = null,
    val artist: String? = null,
    val latestChapter: String? = null,
    val latestChapterId: String? = null,
    val updatedAt: Long? = null,
)

data class SourceChapter(
    val id: String,
    val number: String,
    val title: String? = null,
    val publishedAt: Long? = null,
)

data class SourcePage(val url: String)

/** Which browse operations a source really implements. Never claim one it cannot do. */
enum class SourceCapability { LATEST, POPULAR, SEARCH }

/**
 * The single contract every bundled source implements.
 *
 * It only unifies how the app talks to a source. Each implementation keeps its own site
 * structure, request shape, pagination and parser: nothing forces the sites to look alike.
 */
interface MangaSource {
    val id: String
    val name: String
    val lang: String
    val baseUrl: String
    val capabilities: Set<SourceCapability>

    /** Site listing used by the catalogue tab. */
    suspend fun popular(page: Int): List<SourceManga>

    /** Newest chapters/works, used by the Latest tab. */
    suspend fun latest(page: Int): List<SourceManga>

    suspend fun search(query: String, page: Int): List<SourceManga>

    suspend fun details(mangaId: String): SourceManga?

    suspend fun chapters(mangaId: String): List<SourceChapter>

    suspend fun pages(mangaId: String, chapterId: String): List<SourcePage>

    /** Headers the site needs for its own images (Referer/User-Agent). */
    fun imageHeaders(): Map<String, String> = emptyMap()

    /** Human-readable page for one work, when the source has a public URL. */
    fun webUrl(mangaId: String): String? = null
}

/** A failure owned by the source; screens translate it into their own state. */
class SourceException(
    val failure: SourceFailure,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: failure.name, cause)
