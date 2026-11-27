package com.mangaos.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.BrandColors
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.util.displayGenres
import com.mangaos.app.core.util.ratingLabel
import com.mangaos.app.domain.model.MangaStatus

/**
 * The shared vocabulary of the card and the details header: one rating treatment, one status
 * badge and one genre tag, so the same work never looks like two different records.
 */

fun mangaStatusLabel(status: MangaStatus): Int = when (status) {
    MangaStatus.ONGOING -> R.string.status_ongoing
    MangaStatus.COMPLETED -> R.string.status_completed
    MangaStatus.HIATUS -> R.string.status_hiatus
    MangaStatus.CANCELLED -> R.string.status_cancelled
    MangaStatus.UNKNOWN -> R.string.status_unknown
}

fun mangaStatusColor(status: MangaStatus): Color = when (status) {
    MangaStatus.ONGOING -> BrandColors.Ongoing
    MangaStatus.COMPLETED -> BrandColors.Completed
    MangaStatus.HIATUS -> BrandColors.Hiatus
    MangaStatus.CANCELLED -> BrandColors.Cancelled
    MangaStatus.UNKNOWN -> BrandColors.Hiatus
}

/**
 * A real rating only. [ratingLabel] already rejects zero, NaN and out-of-range values, so a
 * source without a rating keeps the row empty instead of showing a star with a fake number.
 */
@Composable
fun RatingLabel(rating: Double?, modifier: Modifier = Modifier) {
    val label = ratingLabel(rating) ?: return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = BrandColors.Rating,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Small, quiet status badge. The dot plus the label keep it readable without colour alone. */
@Composable
fun StatusBadge(status: MangaStatus, modifier: Modifier = Modifier) {
    if (status == MangaStatus.UNKNOWN) return
    val color = mangaStatusColor(status)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(5.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = stringResource(mangaStatusLabel(status)),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/**
 * At most [limit] genres, each able to shrink with an ellipsis, so tags never become wider than
 * the cover and never push the title. Sources without genres render nothing.
 */
@Composable
fun GenreTags(
    genres: List<String>,
    modifier: Modifier = Modifier,
    limit: Int = 2,
) {
    val shown = displayGenres(genres, limit)
    if (shown.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { genre ->
            Surface(
                modifier = Modifier.weight(1f, fill = false),
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}
