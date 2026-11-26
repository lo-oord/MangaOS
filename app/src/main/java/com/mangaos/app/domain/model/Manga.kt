package com.mangaos.app.domain.model

import androidx.annotation.DrawableRes

/**
 * A work in the catalogue. Phase 1 has no remote source: [localCoverRes] points at the
 * bundled demo artwork, [coverUrl] is where a real network cover will arrive later.
 */
data class Manga(
    val id: String,
    val title: String,
    val type: MangaType,
    val status: MangaStatus,
    val rating: Double,
    val latestChapter: String? = null,
    val updatedAt: Long = 0L,
    val coverUrl: String? = null,
    @DrawableRes val localCoverRes: Int? = null,
)

enum class MangaStatus { ONGOING, COMPLETED, HIATUS, CANCELLED }

enum class MangaType { MANGA, MANHWA }
