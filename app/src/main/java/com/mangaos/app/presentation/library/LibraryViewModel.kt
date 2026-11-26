package com.mangaos.app.presentation.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Manga
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModel : ViewModel() {
    val works: StateFlow<List<Manga>> = ServiceLocator.library.works

    var query by mutableStateOf("")
        private set

    fun onQueryChange(value: String) {
        query = value
    }

    fun remove(mangaKey: String) = ServiceLocator.library.remove(mangaKey)
}
