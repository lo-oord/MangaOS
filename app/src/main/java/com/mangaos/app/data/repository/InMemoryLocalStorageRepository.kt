package com.mangaos.app.data.repository

import com.mangaos.app.data.local.DemoCatalogue
import com.mangaos.app.domain.model.Chapter
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.StoredWork
import com.mangaos.app.domain.repository.LocalStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Owns everything the reader keeps on the device. File management stays here, never inside
 * a composable. Phase 1 keeps the catalogue in memory; the paths already follow the shape a
 * real on-disk store will use.
 */
class InMemoryLocalStorageRepository : LocalStorageRepository {

    private val _works = MutableStateFlow(seed())
    override val works: StateFlow<List<StoredWork>> = _works.asStateFlow()

    private fun seed(): List<StoredWork> = DemoCatalogue.seededStoredWorks.mapNotNull { (mangaId, chapters) ->
        DemoCatalogue.mangaById(mangaId)?.let { StoredWork(it, chapters) }
    }

    override fun saveChapter(manga: Manga, chapter: Chapter) {
        _works.update { current ->
            val existing = current.firstOrNull { it.manga.id == manga.id }
            if (existing == null) {
                current + StoredWork(manga, listOf(chapter))
            } else {
                val chapters = (existing.chapters + chapter)
                    .distinctBy { it.id }
                    .sortedByDescending { it.number.toFloatOrNull() ?: 0f }
                current.map { if (it.manga.id == manga.id) it.copy(chapters = chapters) else it }
            }
        }
    }

    override fun deleteChapter(mangaId: String, chapterId: String) {
        _works.update { current ->
            current.mapNotNull { work ->
                if (work.manga.id != mangaId) {
                    work
                } else {
                    val remaining = work.chapters.filterNot { it.id == chapterId }
                    if (remaining.isEmpty()) null else work.copy(chapters = remaining)
                }
            }
        }
    }

    override fun deleteChapters(mangaId: String, chapterIds: Set<String>) {
        _works.update { current ->
            current.mapNotNull { work ->
                if (work.manga.id != mangaId) {
                    work
                } else {
                    val remaining = work.chapters.filterNot { it.id in chapterIds }
                    if (remaining.isEmpty()) null else work.copy(chapters = remaining)
                }
            }
        }
    }

    override fun deleteWork(mangaId: String) {
        _works.update { current -> current.filterNot { it.manga.id == mangaId } }
    }
}
