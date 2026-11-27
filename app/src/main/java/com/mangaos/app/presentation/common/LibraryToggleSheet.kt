package com.mangaos.app.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.MangaCoverArt
import com.mangaos.app.core.ui.components.SecondaryButton
import com.mangaos.app.domain.model.Manga

@Composable
fun LibraryToggleSheet(
    manga: Manga,
    isInLibrary: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MangaCoverArt(
                manga = manga,
                modifier = Modifier
                    .width(44.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xxs))
                // Never the source: a work's origin is only shown in Sources and the picker.
                val subtitle = manga.genres.take(2).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        SecondaryButton(
            text = stringResource(
                if (isInLibrary) R.string.action_remove_from_library else R.string.action_add_to_library,
            ),
            icon = if (isInLibrary) Icons.Outlined.BookmarkRemove else Icons.Outlined.BookmarkAdd,
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
        )
    }
}
