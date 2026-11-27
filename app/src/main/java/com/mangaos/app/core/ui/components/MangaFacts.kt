package com.mangaos.app.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.MangaTheme
import com.mangaos.app.domain.model.MangaStatus

/**
 * The shared vocabulary of the card and the details header: one status label and one status
 * colour, derived from the active theme so a badge never fights the palette.
 */
fun mangaStatusLabel(status: MangaStatus): Int = when (status) {
    MangaStatus.ONGOING -> R.string.status_ongoing
    MangaStatus.COMPLETED -> R.string.status_completed
    MangaStatus.HIATUS -> R.string.status_hiatus
    MangaStatus.CANCELLED -> R.string.status_cancelled
    MangaStatus.UNKNOWN -> R.string.status_unknown
}

@Composable
fun mangaStatusColor(status: MangaStatus): Color {
    val tokens = MangaTheme.tokens
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        MangaStatus.ONGOING -> tokens.success
        MangaStatus.COMPLETED -> scheme.secondary
        MangaStatus.HIATUS -> tokens.warning
        MangaStatus.CANCELLED -> scheme.error
        MangaStatus.UNKNOWN -> scheme.onSurfaceVariant
    }
}
