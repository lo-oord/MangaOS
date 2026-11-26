package com.mangaos.app.presentation.downloads

import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.Download
import kotlinx.coroutines.flow.StateFlow

class DownloadsViewModel : ViewModel() {

    private val repository = ServiceLocator.downloads
    val downloads: StateFlow<List<Download>> = repository.downloads

    fun pause(id: String) = repository.pause(id)
    fun resume(id: String) = repository.resume(id)
    fun retry(id: String) = repository.retry(id)
    fun cancel(id: String) = repository.cancel(id)
    fun remove(id: String) = repository.remove(id)
}
