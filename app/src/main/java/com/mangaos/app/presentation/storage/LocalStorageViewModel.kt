package com.mangaos.app.presentation.storage

import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.StoredWork
import kotlinx.coroutines.flow.StateFlow

class LocalStorageViewModel : ViewModel() {

    private val repository = ServiceLocator.localStorage

    val works: StateFlow<List<StoredWork>> = repository.works
}
