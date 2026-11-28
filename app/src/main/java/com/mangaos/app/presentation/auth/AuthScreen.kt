package com.mangaos.app.presentation.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.util.appString

/**
 * One authentication shell: the mark, a title, the fields of the active state and its actions.
 * Sign-in, sign-up and password reset swap inside the same column with a cross-fade, so nothing
 * jumps and the keyboard stays where the reader left it.
 */
@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val viewModel: AuthViewModel = rememberAuthViewModel()
    val reduceMotion = LocalReduceMotion.current
    val stage = viewModel.stage

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
        ) {
            Spacer(Modifier.height(Spacing.xl))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(size = 44.dp)
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.tagline_welcome),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    val duration = if (reduceMotion) 0 else 220
                    (fadeIn(tween(duration)) +
                        scaleIn(initialScale = 0.99f, animationSpec = tween(duration)))
                        .togetherWith(fadeOut(tween(if (reduceMotion) 0 else 150)))
                },
                label = "authStage",
            ) { current ->
                // Each state is one column inside the same shell; AnimatedContent lays its
                // content out in a box, so the fields must be stacked explicitly.
                Column {
                    when (current) {
                        AuthStage.SIGN_IN -> SignInForm(viewModel)
                        AuthStage.SIGN_UP -> SignUpForm(viewModel)
                        AuthStage.RESET -> ResetForm(viewModel)
                    }
                }
            }

            AnimatedVisibility(visible = viewModel.formError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            icon = MangaOsIcons.error,
                            contentDescription = null,
                            role = IconRole.Error,
                            size = 16.dp,
                        )
                        Spacer(Modifier.width(Spacing.xxs))
                        Text(
                            text = viewModel.formError?.let { appString(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(
                text = when (stage) {
                    AuthStage.SIGN_IN -> stringResource(R.string.auth_flow_sign_in)
                    AuthStage.SIGN_UP -> stringResource(R.string.auth_flow_create)
                    AuthStage.RESET -> stringResource(R.string.auth_flow_reset)
                },
                onClick = {
                    when (stage) {
                        AuthStage.SIGN_IN -> viewModel.signInAccount(onSignedIn)
                        AuthStage.SIGN_UP -> viewModel.signUpAccount(onSignedIn)
                        AuthStage.RESET -> viewModel.resetPassword { }
                    }
                },
                loading = viewModel.isSubmitting,
                enabled = !viewModel.succeeded,
            )

            if (viewModel.succeeded && stage == AuthStage.RESET) {
                AnimatedVisibility(visible = true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        AppIcon(
                            icon = MangaOsIcons.success,
                            contentDescription = null,
                            role = IconRole.Success,
                            size = 18.dp,
                        )
                        Spacer(Modifier.width(Spacing.xxs))
                        Text(
                            text = stringResource(R.string.auth_flow_reset_done),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = { viewModel.openStage(AuthStage.SIGN_IN) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.auth_flow_back_to_sign_in),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (stage) {
                    AuthStage.SIGN_IN -> {
                        TextButton(onClick = { viewModel.openStage(AuthStage.SIGN_UP) }) {
                            Text(
                                text = stringResource(R.string.auth_flow_to_sign_up),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        TextButton(onClick = { viewModel.openStage(AuthStage.RESET) }) {
                            Text(
                                text = stringResource(R.string.auth_flow_forgot),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    AuthStage.SIGN_UP -> TextButton(onClick = { viewModel.openStage(AuthStage.SIGN_IN) }) {
                        Text(
                            text = stringResource(R.string.auth_flow_to_sign_in),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    AuthStage.RESET -> TextButton(onClick = { viewModel.openStage(AuthStage.SIGN_IN) }) {
                        Text(
                            text = stringResource(R.string.auth_flow_to_sign_in),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            TextButton(
                onClick = { viewModel.continueAsGuest(onSignedIn) },
                enabled = !viewModel.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.auth_flow_guest),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun SignInForm(viewModel: AuthViewModel) {
    AuthFormTitle(
        title = stringResource(R.string.auth_flow_welcome_back),
        subtitle = stringResource(R.string.auth_flow_sign_in_hint),
    )
    AppTextField(
        value = viewModel.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.auth_flow_email),
        leadingIcon = MangaOsIcons.globe,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        error = viewModel.emailError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    AppTextField(
        value = viewModel.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.auth_flow_password),
        leadingIcon = MangaOsIcons.lock,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        error = viewModel.passwordError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
}

@Composable
private fun SignUpForm(viewModel: AuthViewModel) {
    AuthFormTitle(
        title = stringResource(R.string.auth_flow_create_title),
        subtitle = stringResource(R.string.auth_flow_create_hint),
    )
    AppTextField(
        value = viewModel.displayName,
        onValueChange = viewModel::onDisplayNameChange,
        label = stringResource(R.string.auth_flow_name),
        leadingIcon = MangaOsIcons.author,
        imeAction = ImeAction.Next,
        error = viewModel.nameError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    AppTextField(
        value = viewModel.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.auth_flow_email),
        leadingIcon = MangaOsIcons.globe,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        error = viewModel.emailError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    AppTextField(
        value = viewModel.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.auth_flow_password),
        leadingIcon = MangaOsIcons.lock,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Next,
        error = viewModel.passwordError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    AppTextField(
        value = viewModel.confirmPassword,
        onValueChange = viewModel::onConfirmChange,
        label = stringResource(R.string.auth_flow_confirm),
        leadingIcon = MangaOsIcons.lock,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        error = viewModel.confirmError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
}

@Composable
private fun ResetForm(viewModel: AuthViewModel) {
    AuthFormTitle(
        title = stringResource(R.string.auth_flow_reset_title),
        subtitle = stringResource(R.string.auth_flow_reset_hint),
    )
    AppTextField(
        value = viewModel.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.auth_flow_email),
        leadingIcon = MangaOsIcons.globe,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        error = viewModel.emailError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    AppTextField(
        value = viewModel.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.auth_flow_new_password),
        leadingIcon = MangaOsIcons.lock,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Next,
        error = viewModel.passwordError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    AppTextField(
        value = viewModel.confirmPassword,
        onValueChange = viewModel::onConfirmChange,
        label = stringResource(R.string.auth_flow_confirm),
        leadingIcon = MangaOsIcons.lock,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        error = viewModel.confirmError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
}

@Composable
private fun AuthFormTitle(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.md))
}
