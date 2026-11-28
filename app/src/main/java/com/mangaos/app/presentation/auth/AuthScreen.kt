package com.mangaos.app.presentation.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.GlassSurface
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.MangaGlass
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalReduceMotion
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.AppTextField
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.util.appString

/**
 * One account shell for the whole flow. The welcome step, the address, the password, the
 * verification note and the Google link step all share this frame and slide into each other, so
 * the reader never leaves the page they started on.
 */
@Composable
fun AuthScreen(onSignedIn: () -> Unit = {}) {
    val viewModel: AuthViewModel = rememberAuthViewModel()
    val reduceMotion = LocalReduceMotion.current
    val stage = viewModel.stage

    // A guest who chose "sign in" or "create account" arrives with that intent already made, so
    // the shell opens on the address step instead of asking the same question again.
    val requestedMode by AuthRequests.pending.collectAsStateWithLifecycle()
    LaunchedEffect(requestedMode) {
        val mode = requestedMode ?: return@LaunchedEffect
        AuthRequests.consume()
        if (viewModel.stage == AuthStage.WELCOME) viewModel.open(AuthStage.EMAIL, mode)
    }

    val launchGoogle = rememberGoogleSignIn { result ->
        result
            .onSuccess { token -> viewModel.google(token, onSignedIn) }
            .onFailure { error ->
                val reason = (error as? com.mangaos.app.data.firebase.AuthFailure)?.reason
                    ?: com.mangaos.app.data.firebase.AuthReason.GENERIC
                viewModel.googleFailed(reason)
            }
    }

    // The page wash comes from the app's single glass backdrop, so the account screens and the
    // rest of the app are lit by exactly the same light.
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.xl))
                AppLogo(size = 76.dp)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.tagline_welcome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.lg))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MangaGlass.dialogCorner),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedContent(
                            targetState = stage,
                            transitionSpec = {
                                val duration = if (reduceMotion) 0 else 240
                                val forward = targetState.ordinal >= initialState.ordinal
                                val enter = fadeIn(tween(duration)) + slideInHorizontally(
                                    animationSpec = tween(duration),
                                ) { width -> if (forward) width / 6 else -width / 6 }
                                val exit = fadeOut(tween(if (reduceMotion) 0 else 140)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(if (reduceMotion) 0 else 140),
                                    ) { width -> if (forward) -width / 6 else width / 6 }
                                enter togetherWith exit
                            },
                            label = "authStage",
                        ) { current ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                when (current) {
                                    AuthStage.WELCOME -> WelcomeStep(
                                        onSignIn = { viewModel.open(AuthStage.EMAIL, AuthMode.SIGN_IN) },
                                        onCreate = { viewModel.open(AuthStage.EMAIL, AuthMode.SIGN_UP) },
                                        onGoogle = launchGoogle,
                                        submitting = viewModel.isSubmitting,
                                    )
                                    AuthStage.EMAIL -> EmailStep(viewModel)
                                    AuthStage.PASSWORD -> PasswordStep(viewModel)
                                    AuthStage.RESET -> ResetStep(viewModel)
                                    AuthStage.LINK -> LinkStep(viewModel)
                                }
                            }
                        }

                        FailureBanner(viewModel.failure)

                        if (stage != AuthStage.WELCOME) {
                            StepActions(viewModel, onSignedIn, launchGoogle)
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))

                if (stage == AuthStage.WELCOME) {
                    TextButton(
                        onClick = { viewModel.continueAsGuest(onSignedIn) },
                        enabled = !viewModel.isSubmitting,
                    ) {
                        Text(
                            text = stringResource(R.string.auth_flow_guest),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    TextButton(onClick = viewModel::back, enabled = !viewModel.isSubmitting) {
                        Text(
                            text = stringResource(R.string.action_back),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun StepActions(
    viewModel: AuthViewModel,
    onSignedIn: () -> Unit,
    onGoogle: () -> Unit,
) {
    val stage = viewModel.stage
    val mode = viewModel.mode
    Spacer(Modifier.height(Spacing.md))
    when (stage) {
        AuthStage.EMAIL -> PrimaryButton(
            text = stringResource(R.string.action_continue),
            onClick = viewModel::submitEmail,
            enabled = !viewModel.isSubmitting,
        )
        AuthStage.PASSWORD -> PrimaryButton(
            text = stringResource(
                if (mode == AuthMode.SIGN_UP) R.string.auth_flow_create else R.string.auth_flow_sign_in,
            ),
            onClick = { viewModel.submitPassword(onSignedIn) },
            loading = viewModel.isSubmitting,
            icon = if (mode == AuthMode.SIGN_UP) MangaOsIcons.add else MangaOsIcons.login,
        )
        AuthStage.RESET -> if (viewModel.resetSent) PrimaryButton(
            text = stringResource(R.string.auth_flow_back_to_sign_in),
            onClick = { viewModel.open(AuthStage.EMAIL, AuthMode.SIGN_IN) },
            enabled = !viewModel.isSubmitting,
            icon = MangaOsIcons.login,
        ) else PrimaryButton(
            text = stringResource(R.string.auth_flow_reset),
            onClick = viewModel::resetPassword,
            loading = viewModel.isSubmitting,
            icon = MangaOsIcons.mail,
        )
        AuthStage.LINK -> PrimaryButton(
            text = stringResource(R.string.auth_link_confirm),
            onClick = { viewModel.linkGoogleWithPassword(onSignedIn) },
            loading = viewModel.isSubmitting,
            icon = MangaOsIcons.link,
        )
        AuthStage.WELCOME -> Unit
    }
}

@Composable
private fun SecondaryGooglelessAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(MangaGlass.corner),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WelcomeStep(
    onSignIn: () -> Unit,
    onCreate: () -> Unit,
    onGoogle: () -> Unit,
    submitting: Boolean,
) {
    Text(
        text = stringResource(R.string.auth_welcome_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.auth_welcome_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.lg))
    PrimaryButton(
        text = stringResource(R.string.auth_flow_sign_in),
        onClick = onSignIn,
        enabled = !submitting,
        icon = MangaOsIcons.login,
    )
    Spacer(Modifier.height(Spacing.sm))
    SecondaryGooglelessAction(
        text = stringResource(R.string.auth_flow_create),
        enabled = !submitting,
        onClick = onCreate,
    )
    Spacer(Modifier.height(Spacing.sm))
    GoogleButton(onClick = onGoogle, enabled = !submitting)
}

/**
 * The Google action follows the brand: a white surface with the four-colour G, whatever the app
 * theme is. The uppercase "G" letter that used to stand in for the logo is gone.
 */
@Composable
private fun GoogleButton(onClick: () -> Unit, enabled: Boolean) {
    val contentColor = GoogleContentColor
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(MangaGlass.corner),
        color = Color.White,
        contentColor = contentColor,
        border = BorderStroke(1.dp, Color(0xFFDADCE0)),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.auth_continue_google),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

/** Google's own dark grey, used on the white brand button in both themes. */
private val GoogleContentColor = Color(0xFF1F1F1F)

@Composable
private fun EmailStep(viewModel: AuthViewModel) {
    StepTitle(
        title = stringResource(
            if (viewModel.mode == AuthMode.SIGN_UP) R.string.auth_email_title_create
            else R.string.auth_email_title_sign_in,
        ),
        hint = stringResource(R.string.auth_email_hint),
    )
    AppTextField(
        value = viewModel.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.auth_flow_email),
        leadingIcon = MangaOsIcons.mail,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Done,
        error = viewModel.emailError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    if (viewModel.mode == AuthMode.SIGN_IN) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.open(AuthStage.RESET, AuthMode.SIGN_IN) }) {
                Text(
                    text = stringResource(R.string.auth_flow_forgot),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun PasswordStep(viewModel: AuthViewModel) {
    StepTitle(
        title = stringResource(
            if (viewModel.mode == AuthMode.SIGN_UP) R.string.auth_password_title_create
            else R.string.auth_password_title_sign_in,
        ),
        hint = viewModel.email,
    )
    AppTextField(
        value = viewModel.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.auth_flow_password),
        leadingIcon = MangaOsIcons.lock,
        isPassword = true,
        keyboardType = KeyboardType.Password,
        imeAction = if (viewModel.mode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done,
        error = viewModel.passwordError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
    if (viewModel.mode == AuthMode.SIGN_UP) {
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
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { viewModel.open(AuthStage.EMAIL, viewModel.mode) },
            enabled = !viewModel.isSubmitting,
        ) {
            Text(
                text = stringResource(R.string.action_change_email),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ResetStep(viewModel: AuthViewModel) {
    // Once the request is out, the step says exactly what was done and what to expect, instead of
    // leaving the reader on a form that looks like it never ran.
    if (viewModel.resetSent) {
        StepTitle(
            title = stringResource(R.string.auth_reset_sent_title),
            hint = stringResource(R.string.auth_reset_sent_body, viewModel.email),
        )
        Text(
            text = stringResource(R.string.auth_reset_sent_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { viewModel.open(AuthStage.RESET, AuthMode.SIGN_IN) },
                enabled = !viewModel.isSubmitting,
            ) {
                Text(
                    text = stringResource(R.string.action_change_email),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        return
    }
    StepTitle(
        title = stringResource(R.string.auth_flow_reset_title),
        hint = stringResource(R.string.auth_reset_hint),
    )
    AppTextField(
        value = viewModel.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.auth_flow_email),
        leadingIcon = MangaOsIcons.mail,
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Done,
        error = viewModel.emailError?.let { appString(it) },
        enabled = !viewModel.isSubmitting,
    )
}

@Composable
private fun LinkStep(viewModel: AuthViewModel) {
    StepTitle(
        title = stringResource(R.string.auth_link_title),
        hint = stringResource(R.string.auth_link_hint, viewModel.email),
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
private fun StepTitle(title: String, hint: String) {
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
        maxLines = 2,
        modifier = Modifier.padding(top = 2.dp, bottom = Spacing.md),
    )
}

@Composable
private fun FailureBanner(reason: com.mangaos.app.data.firebase.AuthReason?) {
    AnimatedVisibility(visible = reason != null) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
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
                    text = reason?.messageRes()?.let { appString(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
