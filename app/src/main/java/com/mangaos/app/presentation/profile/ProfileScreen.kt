package com.mangaos.app.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.ProfileAvatar
import com.mangaos.app.core.ui.components.ProfileSkeleton
import com.mangaos.app.core.ui.components.SecondaryButton
import com.mangaos.app.domain.model.SessionType
import com.mangaos.app.domain.model.UserProfile
import com.mangaos.app.presentation.common.AgePickerDialog
import com.mangaos.app.presentation.common.CountryPickerDialog
import com.mangaos.app.presentation.common.LanguagePickerSheet
import com.mangaos.app.presentation.common.countryDisplayName
import com.mangaos.app.presentation.common.languageDisplayName
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var editTarget by remember { mutableStateOf<ProfileEditTarget?>(null) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showAgePicker by remember { mutableStateOf(false) }
    var showPasswordInfo by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingPhoto = uri
    }

    fun message(res: Int) {
        scope.launch { snackbar.showSnackbar(context.getString(res)) }
    }

    val current = profile
    if (current == null) {
        ProfileSkeleton(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.lg,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
            ProfileHero(
                profile = current,
                onPhotoClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onUsernameClick = {
                    viewModel.clearEditError()
                    editTarget = ProfileEditTarget.USERNAME
                },
                onDisplayNameClick = {
                    viewModel.clearEditError()
                    editTarget = ProfileEditTarget.DISPLAY_NAME
                },
            )
        }

        item {
            ProfileCard(title = stringResource(R.string.profile_account_info)) {
                InfoRow(
                    icon = Icons.Outlined.Language,
                    label = stringResource(R.string.label_preferred_language),
                    value = languageDisplayName(current.preferredLanguage ?: "ar"),
                    onClick = { showLanguagePicker = true },
                )
                RowDivider()
                InfoRow(
                    icon = Icons.Outlined.Public,
                    label = stringResource(R.string.label_country),
                    value = current.countryCode?.let(::countryDisplayName)
                        ?: stringResource(R.string.value_not_set),
                    onClick = { showCountryPicker = true },
                )
                RowDivider()
                InfoRow(
                    icon = Icons.Outlined.Cake,
                    label = stringResource(R.string.label_age),
                    value = current.age?.toString() ?: stringResource(R.string.value_not_set),
                    onClick = { showAgePicker = true },
                )
                RowDivider()
                InfoRow(
                    icon = Icons.Outlined.AlternateEmail,
                    label = stringResource(R.string.profile_email),
                    value = current.email ?: stringResource(R.string.value_not_set),
                )
                RowDivider()
                InfoRow(
                    icon = Icons.Outlined.Lock,
                    label = stringResource(R.string.profile_password),
                    value = stringResource(R.string.profile_change_password),
                    onClick = { showPasswordInfo = true },
                )
            }
        }

        item {
            ProfileCard(title = stringResource(R.string.profile_auth_sync)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.profile_auto_sync),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.profile_auto_sync_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = false,
                        onCheckedChange = null,
                        enabled = false,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = stringResource(R.string.profile_sync_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.profile_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editTarget?.let { target ->
        EditProfileSheet(
            target = target,
            profile = current,
            isSaving = viewModel.isSaving,
            errorRes = viewModel.editError,
            onValueChange = viewModel::clearEditError,
            onDismiss = {
                viewModel.clearEditError()
                editTarget = null
            },
            onSave = { value ->
                val onResult: (Boolean) -> Unit = { success ->
                    if (success) {
                        editTarget = null
                        message(R.string.profile_saved)
                    } else {
                        message(R.string.profile_save_failed)
                    }
                }
                when (target) {
                    ProfileEditTarget.DISPLAY_NAME -> viewModel.saveDisplayName(value, onResult)
                    ProfileEditTarget.USERNAME -> viewModel.saveUsername(value, onResult)
                }
            },
        )
    }

    pendingPhoto?.let { uri ->
        AppBottomSheet(onDismiss = { if (!viewModel.isSavingPhoto) pendingPhoto = null }) {
            Text(
                text = stringResource(R.string.profile_photo_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.lg))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProfileAvatar(
                    name = current.displayName,
                    avatarUri = uri.toString(),
                    size = 156.dp,
                    modifier = Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        shape = CircleShape,
                    ),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.profile_photo_upload_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = stringResource(R.string.profile_save),
                onClick = {
                    viewModel.savePhoto(uri) { success ->
                        if (success) {
                            pendingPhoto = null
                            message(R.string.profile_photo_saved)
                        } else {
                            message(R.string.profile_photo_save_failed)
                        }
                    }
                },
                loading = viewModel.isSavingPhoto,
                enabled = !viewModel.isSavingPhoto,
            )
            Spacer(Modifier.height(Spacing.sm))
            SecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = { pendingPhoto = null },
                enabled = !viewModel.isSavingPhoto,
            )
        }
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            selected = current.preferredLanguage ?: "ar",
            onSelect = {
                showLanguagePicker = false
                viewModel.setLanguage(it) { success ->
                    message(if (success) R.string.profile_saved else R.string.profile_save_failed)
                }
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCode = current.countryCode,
            onSelect = {
                showCountryPicker = false
                viewModel.setCountry(it) { success ->
                    message(if (success) R.string.profile_saved else R.string.profile_save_failed)
                }
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    if (showAgePicker) {
        AgePickerDialog(
            selectedAge = current.age,
            onSelect = {
                showAgePicker = false
                viewModel.setAge(it) { success ->
                    message(if (success) R.string.profile_saved else R.string.profile_save_failed)
                }
            },
            onDismiss = { showAgePicker = false },
        )
    }

    if (showPasswordInfo) {
        AppDialog(
            title = stringResource(R.string.profile_change_password),
            message = stringResource(R.string.profile_password_unavailable),
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = { showPasswordInfo = false },
            onDismiss = { showPasswordInfo = false },
        )
    }
}

private enum class ProfileEditTarget { DISPLAY_NAME, USERNAME }

@Composable
private fun ProfileHero(
    profile: UserProfile,
    onPhotoClick: () -> Unit,
    onUsernameClick: () -> Unit,
    onDisplayNameClick: () -> Unit,
) {
    val isGuest = profile.sessionType == SessionType.GUEST
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPhotoClick)
                    .padding(3.dp),
            ) {
                ProfileAvatar(
                    name = profile.displayName.ifBlank { profile.username },
                    avatarUri = profile.avatarUri,
                    size = Dimens.avatarLarge,
                    modifier = Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = CircleShape,
                    ),
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(30.dp)
                    .clickable(onClick = onPhotoClick),
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

        Spacer(Modifier.height(Spacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !isGuest, onClick = onUsernameClick)
                .padding(horizontal = Spacing.sm, vertical = 2.dp),
        ) {
            Text(
                text = "@${profile.username}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isGuest) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.profile_edit_username),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxs))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = !isGuest, onClick = onDisplayNameClick)
                .padding(horizontal = Spacing.sm, vertical = 2.dp),
        ) {
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isGuest) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.profile_edit_display_name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        if (isGuest) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.guest_display_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.sm),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = Spacing.md),
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
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
            textAlign = TextAlign.End,
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Spacing.xs)
                    .size(17.dp),
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 36.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun EditProfileSheet(
    target: ProfileEditTarget,
    profile: UserProfile,
    isSaving: Boolean,
    errorRes: Int?,
    onValueChange: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val initial = when (target) {
        ProfileEditTarget.DISPLAY_NAME -> profile.displayName
        ProfileEditTarget.USERNAME -> profile.username
    }
    var value by remember(target) { mutableStateOf(initial) }
    val isUsername = target == ProfileEditTarget.USERNAME
    val title = stringResource(
        if (isUsername) R.string.profile_edit_username else R.string.profile_edit_display_name,
    )
    val label = stringResource(
        if (isUsername) R.string.label_username else R.string.label_display_name,
    )

    AppBottomSheet(onDismiss = { if (!isSaving) onDismiss() }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.lg))
        AppTextField(
            value = value,
            onValueChange = {
                value = if (isUsername) it.filterNot(Char::isWhitespace).take(24) else it.take(48)
                onValueChange()
            },
            label = label,
            leadingIcon = if (isUsername) Icons.Outlined.AlternateEmail else Icons.Outlined.Badge,
            prefix = if (isUsername) "@" else null,
            imeAction = ImeAction.Done,
            enabled = !isSaving,
            error = errorRes?.let { stringResource(it) },
        )
        if (isUsername) {
            Text(
                text = stringResource(R.string.profile_username_rule),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        PrimaryButton(
            text = stringResource(if (isSaving) R.string.profile_saving else R.string.profile_save),
            onClick = { onSave(value) },
            loading = isSaving,
            enabled = !isSaving,
        )
        Spacer(Modifier.height(Spacing.sm))
        TextButton(
            onClick = onDismiss,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
