package com.mangaos.app.data.repository

import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.repository.ExtensionRepository
import com.mangaos.app.domain.repository.MangaRepository
import com.mangaos.app.domain.repository.SourceUnavailableException

/**
 * The single place where catalogue browsing would run extension code.
 *
 * Keiyoushi extensions are compiled against the Mihon extension API where every shared
 * library (OkHttp, Jsoup, the source classes) is `compileOnly`: the host has to provide
 * the implementations, not just the interfaces. MangaOS ships the catalogue and the
 * installer, but not that runtime, so this repository reports the gap instead of
 * returning invented works. Screens turn [SourceUnavailableException] into a real error
 * state with retry, never into sample data.
 */
class ExtensionMangaRepository(
    private val extensions: ExtensionRepository,
) : MangaRepository {

    override suspend fun latest(sourceId: String, page: Int): List<Manga> = unavailable()

    override suspend fun popular(sourceId: String, page: Int): List<Manga> = unavailable()

    override suspend fun search(sourceId: String, query: String, page: Int): List<Manga> = unavailable()

    override suspend fun catalogue(): List<Manga> = emptyList()

    override suspend fun byId(sourceId: String, mangaId: String): Manga? = unavailable()

    override suspend fun chapters(sourceId: String, mangaId: String): List<Chapter> = unavailable()

    override suspend fun pages(sourceId: String, mangaId: String, chapterId: String): List<String> = unavailable()

    private fun unavailable(): Nothing = throw SourceUnavailableException(
        failure = ExtensionFailure.UNSUPPORTED,
        message = if (extensions.runtimeAvailable) {
            "The source could not be loaded"
        } else {
            "This build cannot execute extension code yet"
        },
    )
}
