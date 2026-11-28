package com.mangaos.app.presentation.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaCollection
import com.mangaos.app.core.util.formatBytes
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaViewMode

/**
 * The offline library: one card per work, however many chapters it holds. It draws the shared
 * manga collection, so its covers, facts, spacing and Grid/List behaviour match every catalogue.
 */
@Composable
fun LocalStorageScreen(
    viewMode: MangaViewMode,
    onOpenWork: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocalStorageViewModel = viewModel()
    val works by viewModel.works.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    // The stored cover and the downloaded chapter count are the only offline-only facts; the
    // cards themselves stay the same component used online.
    val mangas = remember(works) {
        works.map { work ->
            work.manga.copy(
                coverUrl = work.coverModel,
                latestChapter = work.latestChapterNumber,
            )
        }
    }
    val chapterCounts = remember(works) { works.associate { it.manga.key to it.chapters.size } }

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
        MangaCollection(
            mangas = mangas,
            viewMode = viewMode,
            onMangaClick = onOpenWork,
            gridState = gridState,
            listState = listState,
            caption = { manga ->
                Text(
                    text = stringResource(
                        R.string.storage_chapter_count,
                        chapterCounts[manga.key] ?: 0,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}
