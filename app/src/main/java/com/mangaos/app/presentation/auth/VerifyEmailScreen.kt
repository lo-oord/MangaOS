package com.mangaos.app.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.GlassSurface
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaGlass
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.SecondaryButton
import com.mangaos.app.data.firebase.AuthFailure
import com.mangaos.app.data.firebase.FirebaseAuthManager
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * The mandatory verification gate.
 *
 * A signed-in account whose address is not confirmed sees this and nothing else: the setup and
 * every app screen are not composed at all until Firebase reports a confirmed address. The mail
 * is sent once when the screen appears, and the button re-reads the account — a tap is never
 * accepted as proof of verification.
 */
@Composable
fun VerifyEmailScreen(modifier: Modifier = Modifier) {
    val state by ServiceLocator.account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var sending by mutableStateOf(false)
    var checking by mutableStateOf(false)
    var sent by mutableStateOf(false)
    var notYet by mutableStateOf(false)
    var canResend by mutableStateOf(true)

    LaunchedEffect(Unit) {
        runCatching { FirebaseAuthManager.sendVerificationEmail() }
            .onSuccess { sent = true }
    }

    fun check() {
        if (checking) return
        checking = true
        notYet = false
        scope.launch {
            try {
                ServiceLocator.account.refresh()
                // The gate opens by itself when the reloaded session reports a confirmed
                // address; this flag only explains why nothing happened.
                if (!FirebaseAuthManager.isEmailVerified) notYet = true
            } catch (_: AuthFailure) {
                notYet = true
            } finally {
                checking = false
            }
        }
    }

    fun resend() {
        if (!canResend) return
        canResend = false
        sending = true
        scope.launch {
            runCatching { FirebaseAuthManager.sendVerificationEmail() }
                .onSuccess { sent = true }
            sending = false
            kotlinx.coroutines.delay(RESEND_COOLDOWN_MILLIS)
            canResend = true
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.xxl))
                AppIcon(
                    icon = MangaOsIcons.mail,
                    contentDescription = null,
                    role = IconRole.Primary,
                    size = 40.dp,
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.auth_verify_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.auth_verify_hint, state.email.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.lg))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(MangaGlass.dialogCorner),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.auth_verify_gate_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AnimatedVisibility(visible = sent) {
                            Text(
                                text = stringResource(R.string.auth_verify_sent),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                        AnimatedVisibility(visible = notYet) {
                            Text(
                                text = stringResource(R.string.auth_verify_not_yet),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                        Spacer(Modifier.height(Spacing.lg))
                        PrimaryButton(
                            text = stringResource(R.string.auth_verify_check),
                            onClick = ::check,
                            loading = checking,
                            icon = MangaOsIcons.success,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        SecondaryButton(
                            text = stringResource(R.string.auth_verify_resend),
                            onClick = ::resend,
                            enabled = canResend && !sending,
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch { FirebaseAuthManager.signOut() }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.auth_verify_other),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(R.string.auth_verify_sign_out),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

private const val RESEND_COOLDOWN_MILLIS = 30_000L
