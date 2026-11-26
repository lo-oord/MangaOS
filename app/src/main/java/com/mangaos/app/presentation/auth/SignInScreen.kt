package com.mangaos.app.presentation.auth

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton

@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
) {
    AuthScaffold(title = stringResource(R.string.signin_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.md))
            AppLogo(size = 52.dp)
            Spacer(Modifier.height(Spacing.sm))
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
            Spacer(Modifier.height(Spacing.md))
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

            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                text = stringResource(R.string.action_sign_in),
                onClick = {
                    if (viewModel.validateSignIn()) viewModel.signIn(onSignedIn)
                },
                height = 48.dp,
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}
