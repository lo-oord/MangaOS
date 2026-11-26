package com.mangaos.app.presentation.sources

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
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
import com.mangaos.app.domain.model.TransferState

@Composable
fun SourcesScreen(modifier: Modifier = Modifier) {
    val viewModel: SourcesViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transfers by viewModel.transfers.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            onRefresh = viewModel::refresh,
        )

        MangaSearchField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = stringResource(R.string.search_sources_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        )

        if (!viewModel.runtimeAvailable) {
            RuntimeNotice()
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
                val entries = viewModel.visibleEntries(current.entries)
                if (entries.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(
                            if (viewModel.query.isBlank()) R.string.sources_empty else R.string.search_no_results,
                        ),
                    )
                } else {
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
                        items(entries, key = { it.extension.pkgName }) { entry ->
                            SourceRow(
                                entry = entry,
                                transfer = transfers[entry.extension.pkgName],
                                onInstall = { requestInstall(entry.extension) },
                                onUninstall = { viewModel.uninstall(entry.extension.pkgName) },
                            )
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
        AppIconButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = stringResource(R.string.sources_refresh),
            onClick = onRefresh,
        )
    }
}

@Composable
private fun RuntimeNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(R.string.sources_runtime_notice),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceRow(
    entry: ExtensionEntry,
    transfer: TransferState?,
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
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (extension.iconUrl != null) {
                AsyncImage(
                    model = extension.iconUrl,
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
                    append(" · v").append(extension.versionName)
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

@Composable
private fun StatusPillText(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 1.dp),
        )
    }
}
