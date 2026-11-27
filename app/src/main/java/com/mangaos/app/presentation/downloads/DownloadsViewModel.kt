package com.mangaos.app.presentation.downloads

import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Download
import kotlinx.coroutines.flow.StateFlow

/** One flat downloads list: no success/failure sections and no filter chips. */
class DownloadsViewModel : ViewModel() {

    private val repository = ServiceLocator.downloads
    val downloads: StateFlow<List<Download>> = repository.downloads

    fun pause(id: String) = repository.pause(id)
    fun resume(id: String) = repository.resume(id)
    fun retry(id: String) = repository.retry(id)
    fun cancel(id: String) = repository.cancel(id)

    /** Record only: downloaded chapters stay in the local library. */
    fun removeRecord(id: String) = repository.removeRecord(id)

    /** Explicit deletion of the downloaded chapter files and their local records. */
    fun deleteLocal(id: String) = repository.deleteLocal(id)

    fun deleteChapter(mangaKey: String, chapterId: String) =
        repository.deleteLocalChapter(mangaKey, chapterId)
}
