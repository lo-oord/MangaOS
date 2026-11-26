package com.mangaos.app.domain.model

/** Library ordering, applied to the works already stored by the user. */
enum class LibrarySort(val storageKey: String) {
    TITLE("title"),
    LAST_UPDATED("last_updated"),
    LAST_READ("last_read");

    companion object {
        fun fromStorage(value: String?): LibrarySort =
            entries.firstOrNull { it.storageKey == value } ?: LAST_UPDATED
    }
}

/** Reading-state filter for the library. */
enum class LibraryFilter(val storageKey: String) {
    ALL("all"),
    READING("reading"),
    UNREAD("unread");

    companion object {
        fun fromStorage(value: String?): LibraryFilter =
            entries.firstOrNull { it.storageKey == value } ?: ALL
    }
}

/**
 * Preferences that are not part of a single screen but shape app behaviour. Reader-specific
 * options deliberately stay in [ReaderSettings] so a value has exactly one source of truth.
 */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val newChapterNotifications: Boolean = true,
    val downloadNotifications: Boolean = true,
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = false,
    val wifiOnlyDownloads: Boolean = true,
    val autoDownload: Boolean = false,
    val concurrentDownloads: Int = 2,
    val librarySort: LibrarySort = LibrarySort.LAST_UPDATED,
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val reduceMotion: Boolean = false,
    val hapticFeedback: Boolean = true,
    val touchEffects: Boolean = true,
    val restoreLastScreen: Boolean = true,
    val confirmExit: Boolean = false,
    val autoRefreshOnOpen: Boolean = true,
    val openLinksExternally: Boolean = true,
)
