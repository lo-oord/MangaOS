package com.mangaos.app.domain.model

import com.mangaos.app.core.designsystem.AppTheme

/** Where downloaded chapters should live. */
enum class StorageChoice(val storageKey: String) {
    DEFAULT("default"),
    CUSTOM("custom");

    companion object {
        fun fromStorage(value: String?): StorageChoice =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT
    }
}

/**
 * The first-launch wizard, persisted as it is filled in so closing the app mid-setup resumes on
 * the same step with the same choices. Kept separate from the session: completing this says
 * nothing about being signed in.
 */
data class OnboardingState(
    val step: Int = 0,
    val language: String = "ar",
    val theme: AppTheme = AppTheme.DEFAULT,
    val storageChoice: StorageChoice = StorageChoice.DEFAULT,
    val folderUri: String? = null,
    val folderName: String? = null,
    val genres: Set<String> = emptySet(),
    val contentTypes: Set<String> = DEFAULT_CONTENT_TYPES,
    val notificationsEnabled: Boolean = true,
) {
    companion object {
        /** Only the work types MangaOS actually reads. */
        val DEFAULT_CONTENT_TYPES: Set<String> = setOf("manga", "manhwa", "manhua")

        val ALL_CONTENT_TYPES: List<String> = listOf("manga", "manhwa", "manhua")

        /** The genre ids offered by the wizard; every one is a real catalogue tag. */
        val ALL_GENRES: List<String> = listOf(
            "action",
            "adventure",
            "romance",
            "fantasy",
            "horror",
            "comedy",
            "drama",
            "psychological",
            "thriller",
            "sports",
            "school",
            "historical",
            "sci_fi",
            "mystery",
            "slice_of_life",
        )
    }
}
