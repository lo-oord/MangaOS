package com.mangaos.app.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun SignUpScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val googleMessage = stringResource(R.string.google_unavailable)

    AuthScaffold(title = stringResource(R.string.signup_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.md))
            AppLogo(size = 68.dp)
            Spacer(Modifier.height(Spacing.xl))

            GoogleButton(
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar(googleMessage) }
                },
            )

            Spacer(Modifier.height(Spacing.lg))

            AppTextField(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.label_email),
                leadingIcon = Icons.Outlined.AlternateEmail,
                keyboardType = KeyboardType.Email,
                error = viewModel.emailError?.let { stringResource(it) },
            )
            Spacer(Modifier.height(Spacing.md))
            AppTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.label_password),
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                error = viewModel.passwordError?.let { stringResource(it) },
            )
            Spacer(Modifier.height(Spacing.md))
            AppTextField(
                value = viewModel.confirmPassword,
                onValueChange = viewModel::onConfirmChange,
                label = stringResource(R.string.label_confirm_password),
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                error = viewModel.confirmError?.let { stringResource(it) },
            )

            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                text = stringResource(R.string.action_next),
                onClick = { if (viewModel.validateCredentials()) onNext() },
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
