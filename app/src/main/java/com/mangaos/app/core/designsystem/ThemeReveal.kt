package com.mangaos.app.core.designsystem

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Carries the screen position of the row that triggered a theme/mode change, so the new
 * palette can be revealed from exactly where the user tapped. The value is consumed once by
 * [com.mangaos.app.presentation.MangaOsApp]; a change coming from anywhere else (for example
 * the system switching to dark) simply falls back to the centre of the screen.
 */
object ThemeReveal {

    private val _origin = MutableStateFlow<Offset?>(null)

    fun setOrigin(origin: Offset) {
        _origin.value = origin
    }

    fun consume(): Offset? {
        val origin = _origin.value
        _origin.value = null
        return origin
    }
}
