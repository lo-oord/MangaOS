package com.mangaos.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalSnackbarHostState
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val snackbarHostState = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val googleMessage = stringResource(R.string.google_unavailable)
    var showForgotPassword by remember { mutableStateOf(false) }

    AuthScaffold(title = stringResource(R.string.signin_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xl))
            AppLogo(size = 68.dp)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = stringResource(R.string.signin_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.tagline_signin),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.xl))

            AppTextField(
                value = viewModel.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.label_email),
                leadingIcon = Icons.Outlined.AlternateEmail,
                keyboardType = KeyboardType.Email,
                error = viewModel.emailError?.let { stringResource(it) },
            )
            Spacer(Modifier.height(Spacing.xs))
            AppTextField(
                value = viewModel.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.label_password),
                leadingIcon = Icons.Outlined.Lock,
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                error = viewModel.passwordError?.let { stringResource(it) },
            )

            RowEnd {
                TextButton(
                    onClick = { showForgotPassword = true },
                    enabled = !viewModel.isSubmitting,
                ) {
                    Text(
                        text = stringResource(R.string.forgot_password),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(
                text = stringResource(R.string.action_sign_in),
                onClick = {
                    if (viewModel.validateSignIn()) viewModel.signIn(onSignedIn)
                },
                enabled = !viewModel.isSubmitting,
                loading = viewModel.isSubmitting,
            )

            Spacer(Modifier.height(Spacing.lg))
            AuthDivider()
            Spacer(Modifier.height(Spacing.lg))
            GoogleButton(
                onClick = {
                    scope.launch { snackbarHostState.showSnackbar(googleMessage) }
                },
                text = stringResource(R.string.action_google_sign_in),
                enabled = !viewModel.isSubmitting,
            )

            Spacer(Modifier.height(Spacing.md))
            AuthLink(
                text = stringResource(R.string.auth_no_account),
                onClick = onCreateAccount,
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    if (showForgotPassword) {
        AppDialog(
            title = stringResource(R.string.forgot_password_title),
            message = stringResource(R.string.forgot_password_unavailable),
            confirmLabel = stringResource(R.string.action_done),
            onConfirm = { showForgotPassword = false },
            onDismiss = { showForgotPassword = false },
        )
    }
}

@Composable
private fun RowEnd(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}
