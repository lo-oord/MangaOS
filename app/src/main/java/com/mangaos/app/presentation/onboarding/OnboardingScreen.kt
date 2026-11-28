package com.mangaos.app.presentation.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.designsystem.themeSwatches
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.domain.model.OnboardingState
import com.mangaos.app.domain.model.StorageChoice

/**
 * The first-launch wizard. One fixed shell (header, progress, content, actions); only the
 * content of the current step changes, with a short cross-fade, so the screen never reloads and
 * every choice is applied in place.
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val storageError by viewModel.storageError.collectAsStateWithLifecycle()
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::chooseCustomFolder) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setNotificationsEnabled(granted) }

    fun enableNotifications(enabled: Boolean) {
        if (!enabled) {
            viewModel.setNotificationsEnabled(false)
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotificationsEnabled(true)
        }
    }

    if (!ready) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        return
    }

    val step = state.step.coerceIn(0, OnboardingViewModel.STEP_COUNT - 1)
    val progressTarget = (step + 1).toFloat() / OnboardingViewModel.STEP_COUNT
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(if (reduceMotion) 0 else 260),
        label = "onboardingProgress",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 40.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(
                            R.string.onboarding_step_of,
                            step + 1,
                            OnboardingViewModel.STEP_COUNT,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            // A plain two-layer bar: the reached share of the wizard, animated between steps.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(50),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val spec = tween<Float>(if (reduceMotion) 0 else 220)
                        (fadeIn(tween(if (reduceMotion) 0 else 220)) +
                            scaleIn(
                                initialScale = 0.985f,
                                animationSpec = tween(if (reduceMotion) 0 else 220),
                            )).togetherWith(fadeOut(spec))
                    },
                    label = "onboardingStep",
                ) { current ->
                    when (current) {
                        OnboardingViewModel.STEP_LANGUAGE -> LanguageStep(
                            selected = state.language,
                            onSelect = viewModel::selectLanguage,
                        )
                        OnboardingViewModel.STEP_THEME -> ThemeStep(
                            selected = state.theme,
                            onSelect = viewModel::selectTheme,
                        )
                        OnboardingViewModel.STEP_STORAGE -> StorageStep(
                            state = state,
                            error = storageError,
                            onDefault = viewModel::chooseDefaultStorage,
                            onPickFolder = { folderPicker.launch(null) },
                        )
                        OnboardingViewModel.STEP_GENRES -> GenresStep(
                            selected = state.genres,
                            onToggle = viewModel::toggleGenre,
                        )
                        OnboardingViewModel.STEP_TYPES -> TypesStep(
                            selected = state.contentTypes,
                            onToggle = viewModel::toggleContentType,
                        )
                        else -> NotificationsStep(
                            enabled = state.notificationsEnabled,
                            onChange = ::enableNotifications,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                AnimatedVisibility(visible = step > 0) {
                    TextButton(onClick = { viewModel.back() }) {
                        Text(
                            text = stringResource(R.string.onboarding_back),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (step == OnboardingViewModel.STEP_GENRES) {
                    TextButton(onClick = viewModel::next) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = if (step == OnboardingViewModel.STEP_COUNT - 1) {
                        stringResource(R.string.onboarding_finish)
                    } else {
                        stringResource(R.string.onboarding_continue)
                    },
                    onClick = {
                        if (step == OnboardingViewModel.STEP_COUNT - 1) {
                            viewModel.finish(onFinished)
                        } else {
                            viewModel.next()
                        }
                    },
                    icon = if (step == OnboardingViewModel.STEP_COUNT - 1) {
                        Icons.Outlined.Check
                    } else {
                        MangaOsIcons.next
                    },
                    modifier = Modifier.weight(1.1f),
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------------------- */
/* Steps                                                                                    */
/* --------------------------------------------------------------------------------------- */

@Composable
private fun StepScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        content()
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun StepHeader(@StringRes title: Int, @StringRes subtitle: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.md))
}

@Composable
private fun LanguageStep(selected: String, onSelect: (String) -> Unit) {
    StepScaffold {
        StepHeader(R.string.onboarding_language_title, R.string.onboarding_language_subtitle)
        languageOptions().forEach { option ->
            val active = option.code == selected
            SelectionSurface(active = active, onClick = { onSelect(option.code) }) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.nativeName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = option.sample,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SelectionMark(active = active)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeStep(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    val dark = isSystemInDarkTheme()
    StepScaffold {
        StepHeader(R.string.onboarding_theme_title, R.string.onboarding_theme_subtitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            maxItemsInEachRow = 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AppTheme.entries.forEach { theme ->
                ThemePreviewCard(
                    theme = theme,
                    label = stringResource(themeTitle(theme)),
                    dark = dark,
                    active = theme == selected,
                    onClick = { onSelect(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StorageStep(
    state: OnboardingState,
    error: Boolean,
    onDefault: () -> Unit,
    onPickFolder: () -> Unit,
) {
    StepScaffold {
        StepHeader(R.string.onboarding_storage_title, R.string.onboarding_storage_subtitle)

        SelectionSurface(
            active = state.storageChoice == StorageChoice.DEFAULT,
            onClick = onDefault,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    icon = MangaOsIcons.storage,
                    contentDescription = null,
                    role = IconRole.Muted,
                    active = state.storageChoice == StorageChoice.DEFAULT,
                    size = 22.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_storage_default),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_storage_default_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SelectionMark(active = state.storageChoice == StorageChoice.DEFAULT)
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        SelectionSurface(
            active = state.storageChoice == StorageChoice.CUSTOM,
            onClick = onPickFolder,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    icon = MangaOsIcons.library,
                    contentDescription = null,
                    role = IconRole.Muted,
                    active = state.storageChoice == StorageChoice.CUSTOM,
                    size = 22.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.onboarding_storage_custom),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.folderName
                            ?.takeIf { state.storageChoice == StorageChoice.CUSTOM }
                            ?: stringResource(R.string.onboarding_storage_custom_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        if (state.folderUri == null) {
                            R.string.onboarding_storage_pick
                        } else {
                            R.string.onboarding_storage_change
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(visible = error) {
            Text(
                text = stringResource(R.string.onboarding_storage_denied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenresStep(selected: Set<String>, onToggle: (String) -> Unit) {
    StepScaffold {
        StepHeader(R.string.onboarding_genres_title, R.string.onboarding_genres_subtitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            modifier = Modifier.fillMaxWidth(),
        ) {
            genreOptions.forEach { option ->
                val active = option.id in selected
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (active) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier
                        .animateContentSize()
                        .clickable { onToggle(option.id) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            icon = option.icon,
                            contentDescription = null,
                            role = IconRole.Muted,
                            active = active,
                            size = 15.dp,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(option.label),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypesStep(selected: Set<String>, onToggle: (String) -> Unit) {
    StepScaffold {
        StepHeader(R.string.onboarding_types_title, R.string.onboarding_types_subtitle)
        contentTypeOptions.forEach { option ->
            val active = option.id in selected
            SelectionSurface(active = active, onClick = { onToggle(option.id) }) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(
                        icon = option.icon,
                        contentDescription = null,
                        role = IconRole.Muted,
                        active = active,
                        size = 20.dp,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(option.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    SelectionMark(active = active)
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun NotificationsStep(enabled: Boolean, onChange: (Boolean) -> Unit) {
    StepScaffold {
        StepHeader(R.string.onboarding_notifications_title, R.string.onboarding_notifications_desc)
        SelectionSurface(active = enabled, onClick = { onChange(!enabled) }) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    icon = MangaOsIcons.notification,
                    contentDescription = null,
                    role = IconRole.Muted,
                    active = enabled,
                    activeRole = IconRole.Primary,
                    size = 22.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.onboarding_notifications_toggle),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = enabled, onCheckedChange = onChange)
            }
        }
        if (!enabled) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.onboarding_notifications_denied),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* --------------------------------------------------------------------------------------- */
/* Shared pieces                                                                            */
/* --------------------------------------------------------------------------------------- */

@Composable
private fun SelectionSurface(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            },
        ),
        content = content,
    )
}

/** The single selection mark used by every step; it scales in instead of redrawing the row. */
@Composable
private fun SelectionMark(active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier.size(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = active,
                enter = scaleIn(tween(160)) + fadeIn(tween(140)),
                exit = fadeOut(tween(90)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    label: String,
    dark: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swatches = remember(theme, dark) { themeSwatches(theme, dark) }
    Surface(
        modifier = modifier
            .animateContentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = swatches.background,
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) swatches.accent else swatches.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(swatches.accent, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(swatches.surface, CircleShape),
                )
                Spacer(Modifier.weight(1f))
                if (active) {
                    AppIcon(
                        icon = Icons.Outlined.Check,
                        contentDescription = null,
                        role = IconRole.Primary,
                        size = 16.dp,
                        modifier = Modifier,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(swatches.surface, RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (dark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                maxLines = 1,
                textAlign = TextAlign.Start,
            )
        }
    }
}

private data class LanguageOption(val code: String, val nativeName: String, val sample: String)

private fun languageOptions(): List<LanguageOption> = listOf(
    LanguageOption("ar", "العربية", "قراءة المانجا والمانهوا"),
    LanguageOption("en", "English", "Read manga and manhwa"),
    LanguageOption("ja", "日本語", "マンガとマンファを読む"),
)
