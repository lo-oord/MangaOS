package com.mangaos.app.presentation.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.GlassSurface
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaGlass
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.ProfileAvatar
import com.mangaos.app.core.ui.components.SecondaryButton
import com.mangaos.app.data.firebase.AvatarCodec
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.presentation.auth.messageRes
import com.mangaos.app.presentation.common.AgePickerDialog
import com.mangaos.app.presentation.common.CountryPickerDialog
import com.mangaos.app.presentation.common.LanguagePickerSheet
import com.mangaos.app.presentation.common.countryDisplayName
import com.mangaos.app.presentation.common.languageDisplayName
import kotlinx.coroutines.launch

/**
 * Completes an account that Firebase created but the app could not finish: a Google sign-up
 * arrives with an address and nothing else.
 *
 * The name, the username and the photo are on the first step — the three fields the account
 * cannot work without are visible immediately — and the optional preferences follow. Values are
 * seeded once per account and are never written back over what the reader typed.
 */
@Composable
fun AccountSetupScreen(onFinished: () -> Unit = {}) {
    val viewModel: AccountSetupViewModel = viewModel()
    val state by ServiceLocator.account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showLanguages by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    var showAges by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var isPreparingPhoto by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pendingPhoto = uri }

    val uid = state?.uid
    val suggestion = state?.displayName.orEmpty()
    val existingUsername = state?.username.orEmpty()
    LaunchedEffect(uid) {
        if (uid != null) viewModel.seed(uid, suggestion, existingUsername)
    }

    val stepIndex = viewModel.step.ordinal + 1
    val stepCount = SetupStep.entries.size

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.xl))
            // The brand block and the glass card are the account flow's own shell; finishing
            // sign-up therefore looks like the page the reader just came from.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppLogo(size = 62.dp)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))

            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MangaGlass.dialogCorner),
                contentPadding = PaddingValues(Spacing.lg),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { stepIndex.toFloat() / stepCount },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Text(
                            text = "$stepIndex/$stepCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    AnimatedContent(
                        targetState = viewModel.step,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "setupStep",
                    ) { step ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (step) {
                                SetupStep.PROFILE -> {
                                    StepHeading(
                                        title = stringResource(R.string.setup_profile_title),
                                        hint = stringResource(R.string.setup_profile_hint),
                                    )
                                    AppTextField(
                                        value = viewModel.displayName,
                                        onValueChange = viewModel::onDisplayNameChange,
                                        label = stringResource(R.string.setup_display_label),
                                        leadingIcon = MangaOsIcons.author,
                                        imeAction = ImeAction.Next,
                                        enabled = !viewModel.isSaving,
                                    )
                                    Spacer(Modifier.height(Spacing.sm))
                                    AppTextField(
                                        value = viewModel.username,
                                        onValueChange = viewModel::onUsernameChange,
                                        label = stringResource(R.string.setup_username_label),
                                        leadingIcon = MangaOsIcons.account,
                                        prefix = "@",
                                        imeAction = ImeAction.Done,
                                        error = usernameError(viewModel),
                                        enabled = !viewModel.isSaving,
                                    )
                                    UsernameStatus(viewModel.usernameState)
                                    Spacer(Modifier.height(Spacing.md))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ProfileAvatar(
                                            name = viewModel.displayName.ifBlank { "M" },
                                            avatarUri = pendingPhoto?.toString()
                                                ?: ServiceLocator.user.profile.value?.avatarUri,
                                            size = 64.dp,
                                            modifier = Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                CircleShape,
                                            ),
                                        )
                                        Spacer(Modifier.size(Spacing.md))
                                        TextButton(
                                            onClick = {
                                                photoPicker.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                    ),
                                                )
                                            },
                                            enabled = !viewModel.isSaving && !isPreparingPhoto,
                                        ) {
                                            AppIcon(
                                                icon = MangaOsIcons.camera,
                                                contentDescription = null,
                                                role = IconRole.Primary,
                                                size = 18.dp,
                                            )
                                            Spacer(Modifier.size(Spacing.xs))
                                            Text(
                                                text = stringResource(
                                                    if (pendingPhoto == null) R.string.setup_photo_pick
                                                    else R.string.setup_photo_change,
                                                ),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                }
                                SetupStep.PREFERENCES -> {
                                    StepHeading(
                                        title = stringResource(R.string.setup_preferences_title),
                                        hint = stringResource(R.string.setup_preferences_hint),
                                    )
                                    PreferenceRow(
                                        icon = MangaOsIcons.languages,
                                        label = stringResource(R.string.label_preferred_language),
                                        value = languageDisplayName(viewModel.language),
                                        onClick = { showLanguages = true },
                                    )
                                    PreferenceRow(
                                        icon = MangaOsIcons.globe,
                                        label = stringResource(R.string.label_country),
                                        value = viewModel.countryCode?.let(::countryDisplayName)
                                            ?: stringResource(R.string.value_not_set),
                                        onClick = { showCountries = true },
                                    )
                                    PreferenceRow(
                                        icon = MangaOsIcons.cake,
                                        label = stringResource(R.string.label_age),
                                        value = viewModel.age?.toString()
                                            ?: stringResource(R.string.value_not_set),
                                        onClick = { showAges = true },
                                    )
                                }
                            }
                        }
                    }

                    viewModel.failure?.let { reason ->
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = stringResource(reason.messageRes()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    if (viewModel.step == SetupStep.PREFERENCES) {
                        PrimaryButton(
                            text = stringResource(R.string.setup_finish),
                            onClick = {
                                scope.launch {
                                    val encoded = pendingPhoto?.let { uri ->
                                        isPreparingPhoto = true
                                        runCatching {
                                            AvatarCodec.encode(ServiceLocator.context(), uri)
                                        }.getOrNull().also { isPreparingPhoto = false }
                                    }
                                    viewModel.finish(encoded, onFinished)
                                }
                            },
                            loading = viewModel.isSaving || isPreparingPhoto,
                            icon = MangaOsIcons.success,
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.action_continue),
                            onClick = viewModel::next,
                            enabled = !viewModel.isSaving,
                        )
                    }
                    if (viewModel.step != SetupStep.PROFILE) {
                        Spacer(Modifier.height(Spacing.xs))
                        SecondaryButton(
                            text = stringResource(R.string.action_back),
                            onClick = viewModel::back,
                            enabled = !viewModel.isSaving,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showLanguages) {
        LanguagePickerSheet(
            selected = viewModel.language,
            onSelect = {
                viewModel.onLanguageChange(it)
                showLanguages = false
            },
            onDismiss = { showLanguages = false },
        )
    }
    if (showCountries) {
        CountryPickerDialog(
            selectedCode = viewModel.countryCode,
            onSelect = {
                viewModel.onCountryChange(it)
                showCountries = false
            },
            onDismiss = { showCountries = false },
        )
    }
    if (showAges) {
        AgePickerDialog(
            selectedAge = viewModel.age,
            onSelect = {
                viewModel.onAgeChange(it)
                showAges = false
            },
            onDismiss = { showAges = false },
        )
    }
}

@Composable
private fun usernameError(viewModel: AccountSetupViewModel): String? = when (viewModel.usernameState) {
    UsernameState.INVALID, UsernameState.TAKEN -> viewModel.usernameMessage?.let {
        stringResource(it)
    }
    else -> null
}

@Composable
private fun UsernameStatus(state: UsernameState) {
    val (text, color) = when (state) {
        UsernameState.AVAILABLE -> stringResource(R.string.setup_username_available) to
            MaterialTheme.colorScheme.primary
        UsernameState.TAKEN -> stringResource(R.string.setup_username_taken) to
            MaterialTheme.colorScheme.error
        UsernameState.CHECKING -> stringResource(R.string.setup_username_checking) to
            MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(top = Spacing.xs),
    )
}

@Composable
private fun StepHeading(title: String, hint: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = Spacing.md),
    )
}

@Composable
private fun PreferenceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        TextButton(onClick = onClick) {
            AppIcon(icon = icon, contentDescription = null, role = IconRole.Secondary, size = 18.dp)
            Spacer(Modifier.size(Spacing.xs))
            Column {
                Text(text = label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
