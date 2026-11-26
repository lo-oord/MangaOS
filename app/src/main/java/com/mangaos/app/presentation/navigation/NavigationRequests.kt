package com.mangaos.app.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A notification tap that arrived before the UI was ready. MainActivity records the exact
 * source + manga ids here and the main navigation consumes them once, so a cold start still
 * lands on the right work instead of the start tab.
 */
object NavigationRequests {

    data class MangaTarget(
        val sourceId: String,
        val mangaId: String,
        val local: Boolean = false,
        val notificationId: String? = null,
    )

    private val _pending = MutableStateFlow<MangaTarget?>(null)
    val pending: StateFlow<MangaTarget?> = _pending.asStateFlow()

    fun open(target: MangaTarget) {
        _pending.value = target
    }

    fun consume() {
        _pending.value = null
    }
}
