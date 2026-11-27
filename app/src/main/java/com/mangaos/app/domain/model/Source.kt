package com.mangaos.app.domain.model

import com.mangaos.app.sources.core.SourceCapability

/**
 * One source compiled into MangaOS, as the app lists it. [id] is the stable registry id, so a
 * renamed or retitled source keeps its library, reading history and downloads.
 */
data class SourceInfo(
    val id: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val description: String,
    val version: String,
    val capabilities: Set<SourceCapability>,
    val enabled: Boolean = false,
    /** The source's own icon, drawn beside its name on the Sources screen. */
    val iconRes: Int? = null,
) {
    val language: String get() = lang
    val supportsLatest: Boolean get() = SourceCapability.LATEST in capabilities
}

/** A failure owned by a source; screens translate it into their own state. */
enum class SourceFailure {
    OFFLINE,
    NETWORK,
    PARSING,
    UNSUPPORTED,
    EMPTY,
    NO_SOURCES,
}

/** Source browsing scope, used by the unified search and the Latest source picker. */
enum class MangaViewMode { GRID, LIST;

    fun toggled(): MangaViewMode = if (this == GRID) LIST else GRID

    companion object {
        fun fromStorage(value: String?): MangaViewMode =
            if (value == "list") LIST else GRID
    }
}
