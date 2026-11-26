package com.mangaos.app.data.repository

import android.content.Context
import com.mangaos.app.data.local.AtomicFileWriter
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * In-app notification history. Only new-chapter and system entries are stored; download
 * progress belongs to Android notifications and never appears here.
 */
class FileNotificationRepository(
    context: Context,
    scope: CoroutineScope,
) : NotificationRepository {

    private val file = File(context.filesDir, RECORDS)
    private val lock = Any()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    override val unreadCount: StateFlow<Int> = _notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    init {
        _notifications.value = runCatching { decode(AtomicFileWriter.read(file)) }
            .getOrDefault(emptyList())
            .filter { it.type == NotificationType.NEW_CHAPTER || it.type == NotificationType.SYSTEM }
    }

    override fun add(title: String, message: String, type: NotificationType) {
        add(
            AppNotification(
                id = UUID.randomUUID().toString(),
                title = title,
                message = message,
                type = type,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun add(notification: AppNotification) {
        if (notification.type != NotificationType.NEW_CHAPTER &&
            notification.type != NotificationType.SYSTEM
        ) {
            return
        }
        synchronized(lock) {
            val next = (listOf(notification) + _notifications.value)
                .distinctBy { it.id }
                .take(MAX_RECORDS)
            persist(next)
            _notifications.value = next
        }
    }

    override fun markRead(notificationId: String) {
        synchronized(lock) {
            val next = _notifications.value.map {
                if (it.id == notificationId) it.copy(isRead = true) else it
            }
            persist(next)
            _notifications.value = next
        }
    }

    override fun markAllRead() {
        synchronized(lock) {
            val next = _notifications.value.map { it.copy(isRead = true) }
            persist(next)
            _notifications.value = next
        }
    }

    override fun delete(notificationId: String) {
        synchronized(lock) {
            val next = _notifications.value.filterNot { it.id == notificationId }
            persist(next)
            _notifications.value = next
        }
    }

    override fun deleteAll() {
        synchronized(lock) {
            persist(emptyList())
            _notifications.value = emptyList()
        }
    }

    private fun persist(items: List<AppNotification>) {
        AtomicFileWriter.write(file, encode(items))
    }

    private fun encode(items: List<AppNotification>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("message", item.message)
                    put("type", item.type.name)
                    put("createdAt", item.createdAt)
                    put("isRead", item.isRead)
                    item.sourceId?.let { put("sourceId", it) }
                    item.mangaId?.let { put("mangaId", it) }
                    item.chapterNumber?.let { put("chapterNumber", it) }
                    item.coverUrl?.let { put("coverUrl", it) }
                    item.thumbnailPath?.let { put("thumbnailPath", it) }
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<AppNotification> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AppNotification(
                id = id,
                title = json.optString("title"),
                message = json.optString("message"),
                type = runCatching { NotificationType.valueOf(json.optString("type")) }
                    .getOrDefault(NotificationType.SYSTEM),
                createdAt = json.optLong("createdAt"),
                isRead = json.optBoolean("isRead"),
                sourceId = json.optString("sourceId").takeIf { it.isNotBlank() },
                mangaId = json.optString("mangaId").takeIf { it.isNotBlank() },
                chapterNumber = json.optString("chapterNumber").takeIf { it.isNotBlank() },
                coverUrl = json.optString("coverUrl").takeIf { it.isNotBlank() },
                thumbnailPath = json.optString("thumbnailPath").takeIf { it.isNotBlank() },
            )
        }
    }

    private companion object {
        const val RECORDS = "notifications.json"
        const val MAX_RECORDS = 200
    }
}
