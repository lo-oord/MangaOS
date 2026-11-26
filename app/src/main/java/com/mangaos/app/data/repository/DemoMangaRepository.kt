package com.mangaos.app.data.repository

import com.mangaos.app.data.local.DemoCatalogue
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.repository.MangaRepository
import kotlinx.coroutines.delay

/**
 * Sample catalogue. The short delay keeps the loading / skeleton states visible, exactly
 * like a real source would. Swapping this class for a network implementation does not
 * require any change in the presentation layer.
 */
class DemoMangaRepository : MangaRepository {

    private val latestCache: List<Manga> by lazy {
        DemoCatalogue.works.sortedByDescending { it.updatedAt }
    }

    private var warmedUp = false

    override suspend fun latest(): List<Manga> {
        warmUpOnce()
        return latestCache
    }

    override suspend fun catalogue(): List<Manga> {
        warmUpOnce()
        return DemoCatalogue.works
    }

    override suspend fun byId(id: String): Manga? = DemoCatalogue.mangaById(id)

    override suspend fun chapters(mangaId: String): List<Chapter> = DemoCatalogue.chaptersFor(mangaId)

    private suspend fun warmUpOnce() {
        if (!warmedUp) {
            delay(280)
            warmedUp = true
        }
    }
}
