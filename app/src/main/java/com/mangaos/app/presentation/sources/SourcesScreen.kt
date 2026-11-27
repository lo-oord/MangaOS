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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.mangaos.app.domain.model.LanguageFilter
import com.mangaos.app.domain.model.SourceInfo
import java.util.Locale

/**
 * Compact list of the bundled sources, grouped by language. Every row only offers enable or
 * disable: the source code is already inside the app, so there is no install, update or
 * remove action and no repository to refresh.
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

    val visible = viewModel.visibleSources(sources)
    val groups = LANGUAGE_ORDER.mapNotNull { code ->
        val items = visible.filter { it.lang.lowercase().startsWith(code) }
        if (items.isEmpty()) null else code to items.sortedBy { it.name.lowercase() }
    } + visible.filter { source ->
        LANGUAGE_ORDER.none { source.lang.lowercase().startsWith(it) }
    }.let { others -> if (others.isEmpty()) emptyList() else listOf("other" to others) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            // Search and the language filter only make sense once there is a source to narrow
            // down; the empty state stays clean instead of showing dead controls.
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                MangaSearchField(
                    value = viewModel.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = stringResource(R.string.search_sources_placeholder),
                )
                Spacer(Modifier.height(Spacing.xs))
                LanguageFilterRow(
                    selected = viewModel.languageFilter,
                    onSelect = viewModel::changeLanguageFilter,
                )
            }
        }

        if (visible.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Extension,
                title = stringResource(
                    if (sources.isEmpty()) R.string.sources_none else R.string.sources_empty,
                ),
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
            groups.forEach { (code, items) ->
                item(key = "header-$code") {
                    Text(
                        text = if (code == "other") {
                            stringResource(R.string.sources_multi_language)
                        } else {
                            languageName(code)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = 2.dp),
                    )
                }
                items(items, key = { it.id }) { source ->
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
}

@Composable
private fun LanguageFilterRow(
    selected: LanguageFilter,
    onSelect: (LanguageFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        LanguageFilter.entries.forEach { filter ->
            val label = stringResource(
                when (filter) {
                    LanguageFilter.ALL -> R.string.sources_filter_all
                    LanguageFilter.ARABIC -> R.string.language_arabic
                    LanguageFilter.ENGLISH -> R.string.language_english
                    LanguageFilter.JAPANESE -> R.string.language_japanese
                },
            )
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
            )
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
            Text(
                text = "${source.baseUrl.removePrefix("https://")} · ${languageName(source.lang)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** Order the app lists languages: Arabic first for this app's primary audience. */
private val LANGUAGE_ORDER = listOf("ar", "en", "ja")

private fun languageName(tag: String): String {
    if (tag.isBlank()) return ""
    return runCatching {
        Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: tag.uppercase()
}
