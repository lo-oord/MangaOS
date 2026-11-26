package com.mangaos.app.data.repository

import com.mangaos.app.data.local.DemoCatalogue
import com.mangaos.app.domain.model.AppNotification
import com.mangaos.app.domain.model.NotificationType
import com.mangaos.app.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID

class InMemoryNotificationRepository(scope: CoroutineScope) : NotificationRepository {

    private val _notifications = MutableStateFlow(DemoCatalogue.seedNotifications())
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    override val unreadCount: StateFlow<Int> = _notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(scope, SharingStarted.Eagerly, _notifications.value.count { !it.isRead })

    override fun add(title: String, message: String, type: NotificationType) {
        _notifications.update { current ->
            listOf(
                AppNotification(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    message = message,
                    type = type,
                    createdAt = System.currentTimeMillis(),
                    isRead = false,
                ),
            ) + current
        }
    }

    override fun markRead(notificationId: String) {
        _notifications.update { current ->
            current.map { if (it.id == notificationId) it.copy(isRead = true) else it }
        }
    }

    override fun markAllRead() {
        _notifications.update { current -> current.map { it.copy(isRead = true) } }
    }

    override fun delete(notificationId: String) {
        _notifications.update { current -> current.filterNot { it.id == notificationId } }
    }

    override fun deleteAll() {
        _notifications.value = emptyList()
    }
}
