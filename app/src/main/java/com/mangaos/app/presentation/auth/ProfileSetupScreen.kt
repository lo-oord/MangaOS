package com.mangaos.app.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.ProfileAvatar
import com.mangaos.app.presentation.common.AgePickerDialog
import com.mangaos.app.presentation.common.CountryPickerDialog
import com.mangaos.app.presentation.common.LanguagePickerSheet
import com.mangaos.app.presentation.common.ProfileSelectorRow
import com.mangaos.app.presentation.common.countryDisplayName
import com.mangaos.app.presentation.common.languageDisplayName

@Composable
fun ProfileSetupScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.onAvatarPicked(uri.toString())
    }

    var showLanguagePicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showAgePicker by remember { mutableStateOf(false) }

    AuthScaffold(title = stringResource(R.string.profile_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.tagline_profile),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))

            Box(
                modifier = Modifier.padding(bottom = Spacing.lg),
                contentAlignment = Alignment.BottomEnd,
            ) {
                ProfileAvatar(
                    name = viewModel.displayName.ifBlank { "M" },
                    avatarUri = viewModel.avatarUri,
                    size = 88.dp,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .offset(x = 2.dp, y = 2.dp)
                        .size(30.dp)
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.cd_change_photo),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            AppTextField(
                value = viewModel.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = stringResource(R.string.label_display_name),
                leadingIcon = Icons.Outlined.Badge,
                error = viewModel.nameError?.let { stringResource(it) },
            )
            Spacer(Modifier.height(Spacing.xs))
            AppTextField(
                value = viewModel.username,
                onValueChange = viewModel::onUsernameChange,
                label = stringResource(R.string.label_username),
                prefix = "@",
                imeAction = ImeAction.Done,
                error = viewModel.usernameError?.let { stringResource(it) },
            )

            Spacer(Modifier.height(Spacing.md))

            ProfileSelectorRow(
                icon = Icons.Outlined.Translate,
                label = stringResource(R.string.label_preferred_language),
                value = languageDisplayName(viewModel.preferredLanguage),
                onClick = { showLanguagePicker = true },
            )
            ProfileSelectorRow(
                icon = Icons.Outlined.Public,
                label = stringResource(R.string.label_country),
                value = viewModel.countryCode?.let(::countryDisplayName) ?: stringResource(R.string.value_not_set),
                onClick = { showCountryPicker = true },
            )
            ProfileSelectorRow(
                icon = Icons.Outlined.Cake,
                label = stringResource(R.string.label_age),
                value = viewModel.age?.toString() ?: stringResource(R.string.value_not_set),
                onClick = { showAgePicker = true },
            )
            viewModel.ageError?.let { error ->
                Text(
                    text = stringResource(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                )
            }

            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.profile_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = stringResource(R.string.action_continue),
                onClick = { if (viewModel.validateProfile()) onNext() },
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            selected = viewModel.preferredLanguage,
            onSelect = {
                viewModel.onPreferredLanguageChange(it)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCode = viewModel.countryCode,
            onSelect = {
                viewModel.onCountryChange(it)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    if (showAgePicker) {
        AgePickerDialog(
            selectedAge = viewModel.age,
            onSelect = {
                viewModel.onAgeChange(it)
                showAgePicker = false
            },
            onDismiss = { showAgePicker = false },
        )
    }
}
