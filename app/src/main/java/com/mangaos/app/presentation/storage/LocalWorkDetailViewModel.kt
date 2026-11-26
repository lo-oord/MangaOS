package com.mangaos.app.presentation.storage

import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.StoredWork
import kotlinx.coroutines.flow.StateFlow

class LocalWorkDetailViewModel : ViewModel() {

    private val storage = ServiceLocator.localStorage

    val works: StateFlow<List<StoredWork>> = storage.works

    fun deleteChapter(mangaKey: String, chapterId: String): Boolean =
        storage.deleteChapter(mangaKey, chapterId)

    fun deleteWork(mangaKey: String): Boolean = storage.deleteWork(mangaKey)
}
