package com.mangaos.app.core.ui

/** Shared screen state so every list screen renders loading / empty / error the same way. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val offline: Boolean = false) : UiState<Nothing>
}
