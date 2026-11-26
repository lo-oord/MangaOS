package com.mangaos.app.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.ProfileAvatar

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

    AuthScaffold(title = stringResource(R.string.profile_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xl))

            Box(
                modifier = Modifier.padding(bottom = Spacing.xl),
                contentAlignment = Alignment.BottomEnd,
            ) {
                ProfileAvatar(
                    name = viewModel.displayName.ifBlank { "M" },
                    avatarUri = viewModel.avatarUri,
                    size = Dimens.avatarLarge,
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
            Spacer(Modifier.height(Spacing.md))
            AppTextField(
                value = viewModel.username,
                onValueChange = viewModel::onUsernameChange,
                label = stringResource(R.string.label_username),
                prefix = "@",
                imeAction = ImeAction.Done,
                error = viewModel.usernameError?.let { stringResource(it) },
            )

            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                text = stringResource(R.string.action_next),
                onClick = { if (viewModel.validateProfile()) onNext() },
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
