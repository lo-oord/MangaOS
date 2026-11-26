package com.mangaos.app.domain.model

/**
 * A work as delivered by one installed extension. [id] is the source's own identifier
 * (usually a relative URL), so it is only unique together with [sourceId].
 */
data class Manga(
    val id: String,
    val sourceId: String,
    val title: String,
    val altTitle: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val rating: Double? = null,
    val author: String? = null,
    val artist: String? = null,
    val year: Int? = null,
    val originalLanguage: String? = null,
    val coverUrl: String? = null,
    val latestChapter: String? = null,
    /** Source-relative id of [latestChapter], when the feed could resolve it. */
    val latestChapterId: String? = null,
    val updatedAt: Long? = null,
    val url: String? = null,
    val sourceName: String? = null,
) {
    /** Stable key across sources; used by the library and reading progress. */
    val key: String get() = "$sourceId/$id"
}

enum class MangaStatus { ONGOING, COMPLETED, HIATUS, CANCELLED, UNKNOWN }

/** The last chapter the reader opened for a work. */
data class ReadingProgress(
    val chapterId: String,
    val chapterNumber: String,
    val updatedAt: Long = 0L,
)
