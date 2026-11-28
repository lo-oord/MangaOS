package com.mangaos.app.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.mangaos.app.core.designsystem.glassPanelBorder
import com.mangaos.app.core.designsystem.glassPanelFill
import com.mangaos.app.core.designsystem.goldColor
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.ProfileAvatar
import com.mangaos.app.core.ui.components.SecondaryButton
import com.mangaos.app.core.ui.components.ProfileSkeleton
import com.mangaos.app.core.ui.components.SettingsRow
import com.mangaos.app.core.ui.components.VerifiedBadge
import com.mangaos.app.core.ui.components.VerifiedName
import com.mangaos.app.data.firebase.AccountState
import com.mangaos.app.data.firebase.CloudSync
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.SessionType
import com.mangaos.app.presentation.auth.AuthMode
import com.mangaos.app.presentation.auth.messageRes
import com.mangaos.app.presentation.auth.rememberGoogleSignIn
import com.mangaos.app.presentation.common.AgePickerDialog
import com.mangaos.app.presentation.common.CountryPickerDialog
import com.mangaos.app.presentation.common.LanguagePickerSheet
import com.mangaos.app.presentation.common.LocalGuestGate
import com.mangaos.app.presentation.common.countryDisplayName
import com.mangaos.app.presentation.common.languageDisplayName
import kotlinx.coroutines.launch

/**
 * The account page: who is signed in, what the account can sign in with, and the two actions
 * that end a session or an account. There is no public profile here, no follower count and no
 * placeholder data — every row is a real value from the account document.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sync by viewModel.syncStatus.collectAsStateWithLifecycle()
    val localProfile by ServiceLocator.user.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<ProfileField?>(null) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    var showLanguages by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    var showAges by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf<PasswordDialog?>(null) }
    var showSignOut by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }
    var showLeaveGuest by remember { mutableStateOf(false) }

    fun message(res: Int) {
        scope.launch { snackbar.showSnackbar(context.getString(res)) }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) pendingPhoto = uri }

    val launchGoogle = rememberGoogleSignIn { result ->
        result
            .onSuccess { token ->
                viewModel.linkGoogle(token) { success ->
                    message(if (success) R.string.profile_google_linked else R.string.profile_save_failed)
                }
            }
            .onFailure { message(R.string.profile_save_failed) }
    }

    if (state.loading && state.account == null) {
        ProfileSkeleton(modifier = modifier)
        return
    }

    val account = state.account
    val isGuest = localProfile?.sessionType == SessionType.GUEST
    val guestGate = LocalGuestGate.current

    // A guest page lists only what a guest has: no account rows, no linked providers and no
    // security card, because none of them can be read or written without a session. The way
    // forward is the one card at the top.
    if (isGuest) {
        GuestProfile(
            avatarUri = localProfile?.avatarUri,
            onCreate = { guestGate.start(AuthMode.SIGN_UP) },
            onSignIn = { guestGate.start(AuthMode.SIGN_IN) },
            onLeave = { showLeaveGuest = true },
        )
        if (showLeaveGuest) {
            AppDialog(
                title = stringResource(R.string.guest_leave_title),
                message = stringResource(R.string.guest_leave_message),
                confirmLabel = stringResource(R.string.guest_leave),
                dismissLabel = stringResource(R.string.action_cancel),
                onConfirm = {
                    showLeaveGuest = false
                    ServiceLocator.user.signOut()
                },
                onDismiss = { showLeaveGuest = false },
                icon = MangaOsIcons.logout,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.md,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            ProfileHeader(
                state = state,
                avatarUri = localProfile?.avatarUri,
                onPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onEditName = { editing = ProfileField.DISPLAY_NAME },
            )
        }

        item {
            AccountSection(title = stringResource(R.string.profile_account_info)) {
                SettingsRow(
                    icon = MangaOsIcons.account,
                    title = stringResource(R.string.setup_display_label),
                    value = state.displayName.ifBlank { stringResource(R.string.value_not_set) },
                    onClick = { editing = ProfileField.DISPLAY_NAME },
                    showDivider = true,
                )
                SettingsRow(
                    icon = MangaOsIcons.verified,
                    title = stringResource(R.string.setup_username_label),
                    value = state.username.ifBlank { stringResource(R.string.value_not_set) },
                    onClick = { editing = ProfileField.USERNAME },
                    showDivider = true,
                )
                SettingsRow(
                    icon = MangaOsIcons.mail,
                    title = stringResource(R.string.profile_email),
                    value = state.email ?: stringResource(R.string.value_not_set),
                    showDivider = true,
                )
                SettingsRow(
                    icon = MangaOsIcons.languages,
                    title = stringResource(R.string.label_preferred_language),
                    value = languageDisplayName(account?.language ?: "ar"),
                    onClick = { showLanguages = true },
                    showDivider = true,
                )
                SettingsRow(
                    icon = MangaOsIcons.globe,
                    title = stringResource(R.string.label_country),
                    value = account?.country?.let(::countryDisplayName)
                        ?: stringResource(R.string.value_not_set),
                    onClick = { showCountries = true },
                    showDivider = true,
                )
                SettingsRow(
                    icon = MangaOsIcons.cake,
                    title = stringResource(R.string.label_age),
                    value = account?.age?.toString() ?: stringResource(R.string.value_not_set),
                    onClick = { showAges = true },
                )
            }
        }

        item {
            AccountSection(title = stringResource(R.string.profile_linked_accounts)) {
                SettingsRow(
                    icon = MangaOsIcons.mail,
                    title = stringResource(R.string.profile_password_row),
                    value = stringResource(
                        if (state.hasPassword) R.string.profile_password_set
                        else R.string.profile_password_missing,
                    ),
                    enabled = !viewModel.isSaving,
                    onClick = {
                        passwordDialog = if (state.hasPassword) PasswordDialog.CHANGE else PasswordDialog.SET
                    },
                    showDivider = true,
                    trailing = {
                        if (state.hasPassword) {
                            AppIcon(
                                icon = MangaOsIcons.success,
                                contentDescription = null,
                                role = IconRole.Success,
                                size = 18.dp,
                            )
                        }
                    },
                )
                SettingsRow(
                    icon = MangaOsIcons.globe,
                    title = stringResource(R.string.profile_google_row),
                    value = stringResource(
                        if (state.hasGoogle) R.string.profile_linked else R.string.profile_not_linked,
                    ),
                    enabled = !viewModel.isSaving,
                    onClick = {
                        if (state.hasGoogle) {
                            if (state.hasPassword) showUnlink = true
                            else message(R.string.profile_unlink_last_method)
                        } else {
                            launchGoogle()
                        }
                    },
                    trailing = {
                        if (viewModel.busyAction == "link") {
                            AppIcon(
                                icon = MangaOsIcons.loading,
                                contentDescription = null,
                                role = IconRole.Muted,
                                size = 18.dp,
                            )
                        } else if (state.hasGoogle) {
                            VerifiedBadge(size = 18.dp)
                        }
                    },
                )
            }
        }

        item {
            AccountSection(title = stringResource(R.string.profile_security)) {
                SettingsRow(
                    icon = MangaOsIcons.shield,
                    title = stringResource(R.string.profile_email_status),
                    value = stringResource(
                        if (state.emailVerified) R.string.profile_email_verified
                        else R.string.profile_email_unverified,
                    ),
                    onClick = if (state.emailVerified) null else viewModel::refreshVerification,
                    showDivider = true,
                    trailing = {
                        if (!state.emailVerified) {
                            TextButton(
                                onClick = {
                                    viewModel.sendVerification { sent ->
                                        message(
                                            if (sent) R.string.auth_verify_sent
                                            else R.string.auth_error_generic,
                                        )
                                    }
                                },
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_verify_resend),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    },
                )
                SettingsRow(
                    icon = MangaOsIcons.cloud,
                    title = stringResource(R.string.profile_sync_row),
                    value = sync.label(),
                    trailing = {
                        AppIcon(
                            icon = when (sync) {
                                CloudSync.Status.OFFLINE -> MangaOsIcons.cloudOff
                                CloudSync.Status.SYNCED -> MangaOsIcons.success
                                else -> MangaOsIcons.sync
                            },
                            contentDescription = null,
                            role = when (sync) {
                                CloudSync.Status.OFFLINE -> IconRole.Warning
                                CloudSync.Status.SYNCED -> IconRole.Success
                                else -> IconRole.Muted
                            },
                            size = 18.dp,
                        )
                    },
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.profile_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        item {
            ProfileCard {
                SettingsRow(
                    icon = MangaOsIcons.logout,
                    title = stringResource(R.string.action_sign_out),
                    onClick = { showSignOut = true },
                )
            }
        }

        // Deliberately apart from the ordinary actions: this is the only destructive row.
        item {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = { showDelete = true },
                    enabled = !viewModel.isSaving,
                ) {
                    AppIcon(
                        icon = MangaOsIcons.trash,
                        contentDescription = null,
                        role = IconRole.Error,
                        size = 18.dp,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(R.string.delete_account),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    viewModel.failure?.let { reason ->
        AppDialog(
            title = stringResource(R.string.profile_action_failed),
            message = stringResource(reason.messageRes()),
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = viewModel::clearMessages,
            onDismiss = viewModel::clearMessages,
            icon = MangaOsIcons.error,
        )
    }
    if (viewModel.failure == null) {
        viewModel.notice?.let { notice ->
            AppDialog(
                title = stringResource(notice),
                confirmLabel = stringResource(R.string.action_done),
                onConfirm = viewModel::clearMessages,
                onDismiss = viewModel::clearMessages,
                icon = MangaOsIcons.info,
            )
        }
    }

    editing?.let { field ->
        EditFieldSheet(
            field = field,
            initial = if (field == ProfileField.USERNAME) state.username else state.displayName,
            isSaving = viewModel.isSaving,
            usernameCheck = viewModel.usernameCheck,
            onCheck = viewModel::checkUsername,
            onDismiss = {
                viewModel.clearMessages()
                editing = null
            },
            onSave = { value ->
                val done: (Boolean) -> Unit = { success ->
                    if (success) {
                        editing = null
                        message(R.string.profile_saved)
                    } else {
                        message(R.string.profile_save_failed)
                    }
                }
                when (field) {
                    ProfileField.DISPLAY_NAME -> viewModel.saveDisplayName(value, done)
                    ProfileField.USERNAME -> viewModel.saveUsername(value, done)
                }
            },
        )
    }

    pendingPhoto?.let { uri ->
        AppBottomSheet(onDismiss = { if (!viewModel.isSaving) pendingPhoto = null }) {
            Text(
                text = stringResource(R.string.profile_photo_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProfileAvatar(
                    name = state.displayName,
                    avatarUri = uri.toString(),
                    size = 148.dp,
                    modifier = Modifier.size(148.dp),
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.profile_photo_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
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
                loading = viewModel.isSaving,
            )
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = { pendingPhoto = null },
                enabled = !viewModel.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    }

    if (showLanguages) {
        LanguagePickerSheet(
            selected = account?.language ?: "ar",
            onSelect = {
                showLanguages = false
                viewModel.saveLanguage(it) { ok ->
                    message(if (ok) R.string.profile_saved else R.string.profile_save_failed)
                }
            },
            onDismiss = { showLanguages = false },
        )
    }
    if (showCountries) {
        CountryPickerDialog(
            selectedCode = account?.country,
            onSelect = {
                showCountries = false
                viewModel.saveCountry(it) { ok ->
                    message(if (ok) R.string.profile_saved else R.string.profile_save_failed)
                }
            },
            onDismiss = { showCountries = false },
        )
    }
    if (showAges) {
        AgePickerDialog(
            selectedAge = account?.age,
            onSelect = {
                showAges = false
                viewModel.saveAge(it) { ok ->
                    message(if (ok) R.string.profile_saved else R.string.profile_save_failed)
                }
            },
            onDismiss = { showAges = false },
        )
    }

    passwordDialog?.let { dialog ->
        PasswordSheet(
            dialog = dialog,
            isSaving = viewModel.isSaving,
            onDismiss = { if (!viewModel.isSaving) passwordDialog = null },
            onSubmit = { current, newPassword, confirmation ->
                if (newPassword != confirmation) {
                    message(R.string.auth_error_password_mismatch)
                } else if (newPassword.length < 8) {
                    message(R.string.auth_error_password_weak)
                } else {
                    val done: (Boolean) -> Unit = { success ->
                        if (success) {
                            passwordDialog = null
                            message(R.string.profile_password_saved)
                        } else {
                            message(R.string.profile_save_failed)
                        }
                    }
                    when (dialog) {
                        PasswordDialog.SET -> viewModel.setPassword(newPassword, done)
                        PasswordDialog.CHANGE -> viewModel.changePassword(current, newPassword, done)
                    }
                }
            },
        )
    }

    if (showUnlink) {
        AppDialog(
            title = stringResource(R.string.profile_unlink_title),
            message = stringResource(R.string.profile_unlink_message),
            confirmLabel = stringResource(R.string.profile_unlink),
            onConfirm = {
                showUnlink = false
                viewModel.unlinkGoogle { ok ->
                    message(if (ok) R.string.profile_unlinked else R.string.profile_save_failed)
                }
            },
            onDismiss = { showUnlink = false },
            icon = MangaOsIcons.unlink,
        )
    }

    if (showSignOut) {
        AppDialog(
            title = stringResource(R.string.sign_out_title),
            message = stringResource(R.string.sign_out_message),
            confirmLabel = stringResource(R.string.action_sign_out),
            onConfirm = {
                showSignOut = false
                viewModel.signOut()
            },
            onDismiss = { showSignOut = false },
            icon = MangaOsIcons.logout,
        )
    }

    if (showDelete) {
        DeleteAccountDialog(
            username = state.username,
            needsPassword = state.hasPassword,
            isSaving = viewModel.isSaving,
            onDismiss = { if (!viewModel.isSaving) showDelete = false },
            onDelete = { typed, password, confirmation ->
                viewModel.deleteAccount(typed, password, confirmation) {
                    showDelete = false
                    message(R.string.delete_account_done)
                }
            },
        )
    }

}

@Composable
private fun CloudSync.Status.label(): String = stringResource(
    when (this) {
        CloudSync.Status.IDLE -> R.string.profile_sync_idle
        CloudSync.Status.SYNCING -> R.string.profile_sync_running
        CloudSync.Status.SYNCED -> R.string.profile_sync_done
        CloudSync.Status.OFFLINE -> R.string.profile_sync_offline
    },
)

private enum class ProfileField { DISPLAY_NAME, USERNAME }

private enum class PasswordDialog { SET, CHANGE }

@Composable
private fun ProfileHeader(
    state: AccountState,
    avatarUri: String?,
    onPhoto: () -> Unit,
    onEditName: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangaGlass.dialogCorner),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ProfileAvatar(
                        name = state.displayName,
                        avatarUri = avatarUri,
                        size = 76.dp,
                        verified = state.verified,
                    )
                    // The camera sits at the top corner so the golden verified badge keeps the
                    // lower one to itself.
                    TextButton(
                        onClick = onPhoto,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                            AppIcon(
                                icon = MangaOsIcons.camera,
                                contentDescription = null,
                                role = IconRole.Primary,
                                size = 16.dp,
                                modifier = Modifier.padding(5.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    VerifiedName(
                        name = state.displayName.ifBlank { state.username },
                        verified = state.verified,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "@${state.username.ifBlank { "reader" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.verified) goldColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = state.email.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(Spacing.xs))
            TextButton(onClick = onEditName, contentPadding = PaddingValues(0.dp)) {
                Text(
                    text = stringResource(R.string.profile_edit_name),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * The guest page: who they are browsing as, the one action that turns the session into an
 * account, and the way back to the sign-in shell. Nothing here pretends to be stored remotely.
 */
@Composable
private fun GuestProfile(
    avatarUri: String?,
    onCreate: () -> Unit,
    onSignIn: () -> Unit,
    onLeave: () -> Unit,
) {
    val name = stringResource(R.string.guest_display_name)
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = Spacing.md,
            bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MangaGlass.dialogCorner),
                contentPadding = PaddingValues(Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(name = name, avatarUri = avatarUri, size = 72.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = stringResource(R.string.guest_mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.xs,
                                    vertical = 2.dp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.guest_profile_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MangaGlass.corner),
                contentPadding = PaddingValues(Spacing.lg),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.guest_register_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = stringResource(R.string.guest_register_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = stringResource(R.string.auth_flow_create),
                        onClick = onCreate,
                        icon = MangaOsIcons.account,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    SecondaryButton(
                        text = stringResource(R.string.auth_flow_sign_in),
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            ProfileCard {
                SettingsRow(
                    icon = MangaOsIcons.logout,
                    title = stringResource(R.string.guest_leave),
                    onClick = onLeave,
                )
            }
        }
    }
}

/** The one glass card every group of account rows sits on. */
@Composable
private fun ProfileCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(MangaGlass.corner),
        color = glassPanelFill(),
        border = glassPanelBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.xxs), content = content)
    }
}

@Composable
private fun AccountSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
        ProfileCard(content = content)
    }
}

@Composable
private fun EditFieldSheet(
    field: ProfileField,
    initial: String,
    isSaving: Boolean,
    usernameCheck: UsernameCheck,
    onCheck: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(field, initial) { mutableStateOf(initial) }
    AppBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(
            if (field == ProfileField.USERNAME) R.string.setup_username_label
            else R.string.setup_display_label,
        ),
        icon = if (field == ProfileField.USERNAME) MangaOsIcons.verified else MangaOsIcons.account,
    ) {
        AppTextField(
            value = value,
            onValueChange = {
                value = it
                if (field == ProfileField.USERNAME) onCheck(it)
            },
            label = stringResource(
                if (field == ProfileField.USERNAME) R.string.setup_username_label
                else R.string.setup_display_label,
            ),
            prefix = if (field == ProfileField.USERNAME) "@" else null,
            imeAction = ImeAction.Done,
            enabled = !isSaving,
        )
        if (field == ProfileField.USERNAME) {
            val (text, color) = when (usernameCheck) {
                UsernameCheck.AVAILABLE ->
                    stringResource(R.string.setup_username_available) to MaterialTheme.colorScheme.primary
                UsernameCheck.TAKEN ->
                    stringResource(R.string.setup_username_taken) to MaterialTheme.colorScheme.error
                UsernameCheck.CHECKING ->
                    stringResource(R.string.setup_username_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                UsernameCheck.IDLE -> null to MaterialTheme.colorScheme.onSurfaceVariant
            }
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = stringResource(R.string.profile_save),
            onClick = { onSave(value) },
            loading = isSaving,
        )
    }
}

@Composable
private fun PasswordSheet(
    dialog: PasswordDialog,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AppBottomSheet(
        onDismiss = onDismiss,
        title = stringResource(
            if (dialog == PasswordDialog.SET) R.string.profile_set_password
            else R.string.profile_change_password,
        ),
        icon = MangaOsIcons.key,
    ) {
        if (dialog == PasswordDialog.CHANGE) {
            AppTextField(
                value = current,
                onValueChange = { current = it },
                label = stringResource(R.string.profile_current_password),
                leadingIcon = MangaOsIcons.lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                enabled = !isSaving,
            )
        }
        AppTextField(
            value = next,
            onValueChange = { next = it },
            label = stringResource(R.string.auth_flow_new_password),
            leadingIcon = MangaOsIcons.key,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            enabled = !isSaving,
        )
        AppTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = stringResource(R.string.auth_flow_confirm),
            leadingIcon = MangaOsIcons.key,
            isPassword = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            enabled = !isSaving,
        )
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = stringResource(R.string.profile_save),
            onClick = { onSubmit(current, next, confirm) },
            loading = isSaving,
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    username: String,
    needsPassword: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onDelete: (String, String, String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AppDialog(
        title = stringResource(R.string.delete_account),
        message = stringResource(R.string.delete_account_message),
        confirmLabel = stringResource(R.string.delete_account_confirm),
        dismissLabel = stringResource(R.string.action_cancel),
        destructive = true,
        icon = MangaOsIcons.trash,
        onConfirm = {
            if (!isSaving) onDelete(typed, password, confirmation)
        },
        onDismiss = onDismiss,
    ) {
        AppTextField(
            value = typed,
            onValueChange = { typed = it },
            label = stringResource(R.string.delete_username_label, username),
            leadingIcon = MangaOsIcons.verified,
            enabled = !isSaving,
        )
        if (needsPassword) {
            AppTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.profile_current_password),
                leadingIcon = MangaOsIcons.lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                enabled = !isSaving,
            )
            AppTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = stringResource(R.string.auth_flow_confirm),
                leadingIcon = MangaOsIcons.lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                enabled = !isSaving,
            )
        } else {
            Text(
                text = stringResource(R.string.delete_need_password),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (isSaving) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.delete_in_progress),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
