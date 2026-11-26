package com.mangaos.app.presentation.notifications

import androidx.lifecycle.ViewModel
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.AppNotification
import kotlinx.coroutines.flow.StateFlow

class NotificationsViewModel : ViewModel() {

    private val repository = ServiceLocator.notifications
    val notifications: StateFlow<List<AppNotification>> = repository.notifications

    fun markRead(id: String) = repository.markRead(id)
    fun markAllRead() = repository.markAllRead()
    fun delete(id: String) = repository.delete(id)
    fun deleteAll() = repository.deleteAll()
}
