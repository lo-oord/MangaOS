package com.mangaos.app.data.repository

import com.mangaos.app.data.local.DemoCatalogue
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.repository.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class InMemoryLibraryRepository(
    scope: CoroutineScope,
    seed: Set<String> = DemoCatalogue.seededLibrary,
) : LibraryRepository {

    private val _ids = MutableStateFlow(seed)
    override val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    override val works: StateFlow<List<Manga>> = _ids
        .map { ids -> DemoCatalogue.works.filter { it.id in ids } }
        .stateIn(scope, SharingStarted.Eagerly, DemoCatalogue.works.filter { it.id in seed })

    override fun toggle(manga: Manga) {
        _ids.update { current ->
            if (manga.id in current) current - manga.id else current + manga.id
        }
    }

    override fun remove(mangaId: String) {
        _ids.update { it - mangaId }
    }
}
