package com.mangaos.app.presentation.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppSettings
import com.mangaos.app.domain.model.LibraryFilter
import com.mangaos.app.domain.model.LibrarySort
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.ReadingProgress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel : ViewModel() {
    val works: StateFlow<List<Manga>> = ServiceLocator.library.works

    val progress: StateFlow<Map<String, ReadingProgress>> = ServiceLocator.library.progress

    val settings: StateFlow<AppSettings> = ServiceLocator.settings.appSettings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    /** Applies the persisted sort and reading filter without touching the stored library. */
    fun visible(
        works: List<Manga>,
        query: String,
        progress: Map<String, ReadingProgress>,
        settings: AppSettings,
    ): List<Manga> {
        val filtered = works.filter { manga ->
            val matchesQuery = com.mangaos.app.core.util.SearchText.matches(manga.title, query) ||
                com.mangaos.app.core.util.SearchText.matches(manga.author, query)
            val matchesStatus = when (settings.libraryFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.READING -> progress[manga.key] != null
                LibraryFilter.UNREAD -> progress[manga.key] == null
            }
            matchesQuery && matchesStatus
        }
        return when (settings.librarySort) {
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            LibrarySort.LAST_UPDATED -> filtered.sortedByDescending { it.updatedAt ?: 0L }
            LibrarySort.LAST_READ -> filtered.sortedByDescending { progress[it.key]?.updatedAt ?: 0L }
        }
    }

    var query by mutableStateOf("")
        private set

    fun onQueryChange(value: String) {
        query = value
    }

    fun remove(mangaKey: String) = ServiceLocator.library.remove(mangaKey)
}
