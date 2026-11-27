package com.mangaos.app.presentation.sources

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.domain.model.SourceInfo

/**
 * The bundled Arabic sources, one flat list. Every row only offers enable or disable: the
 * source code is already inside the app, so there is no install, update or remove action and
 * no repository to refresh.
 */
@Composable
fun SourcesScreen(
    onOpenSource: (SourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SourcesViewModel = viewModel()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val enabledCount by viewModel.enabledCount.collectAsStateWithLifecycle()
    val failures by viewModel.failures.collectAsStateWithLifecycle()

    val visible = viewModel.visibleSources(sources).sortedBy { it.name.lowercase() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.sources_section_sources),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (sources.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.sources_native_summary,
                        enabledCount,
                        sources.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        MangaSearchField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_sources_placeholder),
            modifier = Modifier.padding(
                start = Spacing.screen,
                end = Spacing.screen,
                bottom = Spacing.sm,
            ),
        )

        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Extension,
                title = stringResource(R.string.sources_none),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                bottom = Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(visible, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    failure = failures[source.id],
                    onOpen = { onOpenSource(source) },
                    onToggle = { enabled -> viewModel.setEnabled(source.id, enabled) },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceInfo,
    failure: String?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = source.enabled, onClick = onOpen)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (source.enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val icon = source.iconRes
            if (icon != null) {
                // The source's own icon keeps its square aspect ratio instead of being cropped.
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Text(
                    text = source.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (source.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (failure != null && source.enabled) {
                Text(
                    text = stringResource(R.string.sources_last_error, failure),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Switch(
            checked = source.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.widthIn(min = 48.dp),
        )
    }
}
