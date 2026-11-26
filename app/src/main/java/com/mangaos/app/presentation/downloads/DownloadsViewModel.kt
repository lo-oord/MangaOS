package com.mangaos.app.presentation.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Download
import com.mangaos.app.domain.model.DownloadState
import kotlinx.coroutines.flow.StateFlow

enum class DownloadFilter {
    ALL,
    ACTIVE,
    QUEUED,
    COMPLETED,
    FAILED,
}

class DownloadsViewModel : ViewModel() {

    private val repository = ServiceLocator.downloads
    val downloads: StateFlow<List<Download>> = repository.downloads

    var filter by mutableStateOf(DownloadFilter.ALL)
        private set

    fun setFilter(value: DownloadFilter) {
        filter = value
    }

    fun visible(all: List<Download>): List<Download> = when (filter) {
        DownloadFilter.ALL -> all
        DownloadFilter.ACTIVE -> all.filter {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED
        }
        DownloadFilter.QUEUED -> all.filter { it.state == DownloadState.QUEUED }
        DownloadFilter.COMPLETED -> all.filter { it.state == DownloadState.COMPLETED }
        DownloadFilter.FAILED -> all.filter {
            it.state == DownloadState.FAILED || it.state == DownloadState.PARTIAL
        }
    }

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
