package com.mangaos.app.presentation.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaCard
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.domain.model.Manga

/** The offline library: one card per work, however many chapters it holds. */
@Composable
fun LocalStorageScreen(
    onOpenWork: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocalStorageViewModel = viewModel()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    if (works.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.SdStorage,
            title = stringResource(R.string.storage_empty_title),
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.storage_summary,
                    works.size,
                    formatBytes(works.sumOf { it.sizeBytes }),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.xs,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.gridGap),
        ) {
            items(items = works, key = { it.manga.key }) { work ->
                Column {
                    MangaCard(
                        manga = work.manga.copy(
                            coverUrl = work.coverModel,
                            latestChapter = work.latestChapterNumber,
                        ),
                        onClick = { onOpenWork(work.manga) },
                        showLatestChapter = true,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(
                            R.string.storage_chapter_count,
                            work.chapters.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
