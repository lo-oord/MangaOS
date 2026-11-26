package com.mangaos.app.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.ProfileAvatar
import com.mangaos.app.core.ui.components.SettingsOptionRow
import com.mangaos.app.core.util.LocaleManager
import java.util.Locale

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
            Spacer(Modifier.height(Spacing.md))
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
                    size = 84.dp,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .offset(x = 2.dp, y = 2.dp)
                        .size(28.dp)
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
                            modifier = Modifier.size(15.dp),
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
            Spacer(Modifier.height(Spacing.md))
            AppTextField(
                value = viewModel.username,
                onValueChange = viewModel::onUsernameChange,
                label = stringResource(R.string.label_username),
                prefix = "@",
                imeAction = ImeAction.Done,
                error = viewModel.usernameError?.let { stringResource(it) },
            )

            Spacer(Modifier.height(Spacing.lg))

            SelectorRow(
                icon = Icons.Outlined.Translate,
                label = stringResource(R.string.label_preferred_language),
                value = languageName(viewModel.preferredLanguage),
                onClick = { showLanguagePicker = true },
            )
            SelectorRow(
                icon = Icons.Outlined.Public,
                label = stringResource(R.string.label_country),
                value = viewModel.countryCode?.let(::countryName) ?: stringResource(R.string.value_not_set),
                onClick = { showCountryPicker = true },
            )
            SelectorRow(
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
                height = 48.dp,
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (showLanguagePicker) {
        AppBottomSheet(onDismiss = { showLanguagePicker = false }) {
            Text(
                text = stringResource(R.string.label_preferred_language),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
            listOf(
                LocaleManager.ARABIC to R.string.language_arabic,
                LocaleManager.ENGLISH to R.string.language_english,
                LocaleManager.JAPANESE to R.string.language_japanese,
            ).forEach { (code, label) ->
                SettingsOptionRow(
                    label = stringResource(label),
                    selected = viewModel.preferredLanguage == code,
                    onClick = {
                        viewModel.onPreferredLanguageChange(code)
                        showLanguagePicker = false
                    },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
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

@Composable
private fun SelectorRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CountryPickerDialog(
    selectedCode: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val countries = remember {
        Locale.getISOCountries()
            .map { code -> code to countryName(code) }
            .sortedBy { it.second.lowercase() }
    }
    val visible = remember(query) {
        if (query.isBlank()) {
            countries
        } else {
            countries.filter { it.second.contains(query.trim(), ignoreCase = true) || it.first.equals(query.trim(), true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.label_country), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.country_search_hint)) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(visible, key = { it.first }) { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(code) }
                                .background(
                                    if (code == selectedCode) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun AgePickerDialog(
    selectedAge: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val ages = remember { (13..99).toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.label_age), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.age_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(ages, key = { it }) { age ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(age) }
                                .background(
                                    if (age == selectedAge) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = age.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun languageName(code: String): String = stringResource(
    when (code) {
        LocaleManager.ARABIC -> R.string.language_arabic
        LocaleManager.JAPANESE -> R.string.language_japanese
        else -> R.string.language_english
    },
)

private fun countryName(code: String): String =
    runCatching { Locale("", code).getDisplayCountry(Locale.getDefault()) }
        .getOrDefault(code)
        .ifBlank { code }
