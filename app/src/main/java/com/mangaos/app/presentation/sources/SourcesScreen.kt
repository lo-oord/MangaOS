package com.mangaos.app.presentation.sources

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collect
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppIconButton
import com.mangaos.app.core.ui.components.EmptyState
import com.mangaos.app.core.ui.components.ErrorState
import com.mangaos.app.core.ui.components.LoadingIndicator
import com.mangaos.app.core.ui.components.MangaSearchField
import com.mangaos.app.domain.model.ExtensionEntry
import com.mangaos.app.domain.model.ExtensionFailure
import com.mangaos.app.domain.model.ExtensionsState
import com.mangaos.app.domain.model.LanguageFilter
import com.mangaos.app.domain.model.ManagedExtension
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.model.TransferState

@Composable
fun SourcesScreen(
    onOpenSource: (SourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SourcesViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val failures by viewModel.failures.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val refreshFailure by viewModel.failureState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current

    var permissionDismissed by remember { mutableStateOf(false) }
    var pendingInstall by remember { mutableStateOf<ManagedExtension?>(null) }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Re-check on return: never assume the user granted anything.
        val extension = pendingInstall
        pendingInstall = null
        if (extension != null && viewModel.canInstallPackages()) {
            viewModel.install(extension)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.reloadInstalled()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SourcesEvent.Refreshed -> context.getString(R.string.sources_refresh_done)
                is SourcesEvent.Failed -> context.getString(
                    R.string.sources_refresh_failed,
                    context.getString(
                        when (event.failure) {
                            ExtensionFailure.OFFLINE -> R.string.state_offline
                            ExtensionFailure.INVALID_INDEX -> R.string.sources_index_error
                            else -> R.string.state_source_unavailable
                        },
                    ),
                )
            }
            snackbar.showSnackbar(message)
        }
    }

    fun requestInstall(extension: ManagedExtension) {
        if (viewModel.canInstallPackages()) {
            viewModel.install(extension)
        } else {
            pendingInstall = extension
            permissionDismissed = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LanguageFilterRow(
            selected = viewModel.languageFilter,
            onSelect = viewModel::changeLanguageFilter,
            refreshing = refreshing,
            onRefresh = viewModel::refresh,
        )

        MangaSearchField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_sources_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        refreshFailure?.let { failure ->
            RefreshFailureBanner(failure = failure, onRetry = viewModel::refresh)
        }

        when (val current = state) {
            ExtensionsState.Loading -> LoadingIndicator()
            is ExtensionsState.Error -> when (current.failure) {
                ExtensionFailure.OFFLINE, ExtensionFailure.NETWORK -> ErrorState(
                    onRetry = viewModel::refresh,
                    offline = current.failure == ExtensionFailure.OFFLINE,
                )
                else -> EmptyState(
                    icon = Icons.Outlined.Extension,
                    title = stringResource(R.string.sources_index_error),
                    actionLabel = stringResource(R.string.state_error_retry),
                    onAction = viewModel::refresh,
                )
            }
            is ExtensionsState.Content -> {
                val visibleSources = viewModel.visibleSources(sources)
                val visibleExtensions = viewModel.visibleExtensions(current.entries)
                val installedExtensions = visibleExtensions.filter { it.isInstalled }
                val failedExtensions = failures.filterKeys { pkg ->
                    visibleExtensions.any { it.extension.pkgName == pkg }
                }

                when {
                    visibleSources.isEmpty() && visibleExtensions.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(
                            if (viewModel.query.isBlank()) R.string.sources_empty else R.string.search_no_results,
                        ),
                    )

                    visibleSources.isEmpty() && installedExtensions.isNotEmpty() &&
                        failedExtensions.isNotEmpty() -> EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.sources_incompatible_title),
                        actionLabel = stringResource(R.string.sources_refresh),
                        onAction = viewModel::refresh,
                    )

                    visibleSources.isEmpty() && installedExtensions.isNotEmpty() -> EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = stringResource(R.string.sources_no_usable_title),
                        actionLabel = stringResource(R.string.sources_refresh),
                        onAction = viewModel::refresh,
                    )

                    else -> {
                        val iconByPackage = current.entries.associate {
                            it.extension.pkgName to it.extension.iconUrl
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = Spacing.screen,
                                end = Spacing.screen,
                                top = Spacing.xs,
                                bottom = Spacing.xl,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            if (visibleSources.isNotEmpty()) {
                                item(key = "sources-header") {
                                    SectionHeader(
                                        title = stringResource(R.string.sources_section_sources),
                                        count = visibleSources.size,
                                    )
                                }
                                items(visibleSources, key = { "source:${it.id}" }) { source ->
                                    SourceRow(
                                        source = source,
                                        iconUrl = iconByPackage[source.pkgName],
                                        onOpen = { onOpenSource(source) },
                                    )
                                }
                            }

                            if (visibleExtensions.isNotEmpty()) {
                                item(key = "extensions-header") {
                                    SectionHeader(
                                        title = stringResource(R.string.sources_section_extensions),
                                        count = visibleExtensions.size,
                                    )
                                }
                                items(visibleExtensions, key = { "extension:${it.extension.pkgName}" }) { entry ->
                                    ExtensionRow(
                                        entry = entry,
                                        transfer = transfers[entry.extension.pkgName],
                                        loadFailure = failures[entry.extension.pkgName],
                                        onInstall = { requestInstall(entry.extension) },
                                        onUninstall = { viewModel.uninstall(entry.extension.pkgName) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val installing = pendingInstall
    if (installing != null && !permissionDismissed) {
        AppDialog(
            title = stringResource(R.string.sources_permission_title),
            message = stringResource(R.string.sources_permission_message),
            confirmLabel = stringResource(R.string.sources_permission_open),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                permissionDismissed = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }
            },
            onDismiss = {
                permissionDismissed = true
                pendingInstall = null
            },
        )
    }

}

@Composable
private fun LanguageFilterRow(
    selected: LanguageFilter,
    onSelect: (LanguageFilter) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.xs),
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
        Spacer(Modifier.weight(1f))
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
                    .size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            AppIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.sources_refresh),
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RefreshFailureBanner(failure: ExtensionFailure, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(
                if (failure == ExtensionFailure.OFFLINE) R.string.state_offline else R.string.sources_refresh_failed_short,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.state_error_retry), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceInfo,
    iconUrl: String?,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(iconUrl)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(languageName(source.lang))
                    append(" · ").append(source.extensionName)
                    if (source.versionName.isNotBlank()) append(" · v").append(source.versionName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onOpen) {
            Text(stringResource(R.string.sources_open), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExtensionRow(
    entry: ExtensionEntry,
    transfer: TransferState?,
    loadFailure: String?,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
) {
    val extension = entry.extension
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtensionIcon(extension.iconUrl)

        Spacer(Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = extension.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(languageLabel(extension.languages))
                    if (extension.versionName.isNotBlank()) append(" · v").append(extension.versionName)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (transfer is TransferState.Failed) {
                Text(
                    text = stringResource(R.string.sources_install_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                )
            } else if (entry.isInstalled && loadFailure != null) {
                Text(
                    text = stringResource(R.string.sources_incompatible_body),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when (transfer) {
            TransferState.Downloading -> StatusPill(stringResource(R.string.sources_downloading), true)
            TransferState.Installing -> StatusPill(stringResource(R.string.sources_installing), true)
            is TransferState.Failed -> TextButton(onClick = onInstall) {
                Text(stringResource(R.string.state_error_retry), color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                when {
                    entry.isInstalled && entry.hasUpdate -> TextButton(onClick = onInstall) {
                        Text(
                            text = stringResource(R.string.sources_update),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    entry.isInstalled -> Text(
                        text = stringResource(R.string.sources_installed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                    extension.apkUrl.isNotEmpty() -> TextButton(onClick = onInstall) {
                        Text(
                            text = stringResource(R.string.sources_install),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (entry.isInstalled) {
                    AppIconButton(
                        icon = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.sources_remove),
                        onClick = onUninstall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionIcon(iconUrl: String?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (iconUrl != null) {
            AsyncImage(
                model = iconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Extension,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, loading: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun languageLabel(languages: List<String>): String {
    val resolved = languages.filter { it.isNotBlank() }
    return when {
        resolved.isEmpty() -> stringResource(R.string.sources_filter_all)
        resolved.size == 1 -> languageName(resolved.first())
        else -> stringResource(R.string.sources_multi_language)
    }
}

@Composable
private fun languageName(code: String): String = when (code.lowercase()) {
    "ar" -> stringResource(R.string.language_arabic)
    "ja" -> stringResource(R.string.language_japanese)
    "en" -> stringResource(R.string.language_english)
    else -> code.uppercase()
}
