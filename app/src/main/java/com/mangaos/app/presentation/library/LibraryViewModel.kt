package com.mangaos.app.presentation.library

import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel : ViewModel() {
    val works: StateFlow<List<Manga>> = ServiceLocator.library.works

    fun remove(mangaId: String) = ServiceLocator.library.remove(mangaId)
}
