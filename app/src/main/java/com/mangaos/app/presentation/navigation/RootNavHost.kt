package com.mangaos.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.data.firebase.SessionStage
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.SessionType
import com.mangaos.app.presentation.auth.AccountSetupScreen
import com.mangaos.app.presentation.auth.AuthMode
import com.mangaos.app.presentation.auth.AuthRequests
import com.mangaos.app.presentation.auth.AuthScreen
import com.mangaos.app.presentation.auth.VerifyEmailScreen
import com.mangaos.app.presentation.common.GuestAccountDialog
import com.mangaos.app.presentation.common.GuestGate
import com.mangaos.app.presentation.common.GuestNeed
import com.mangaos.app.presentation.common.LocalGuestGate
import com.mangaos.app.presentation.onboarding.OnboardingScreen
import com.mangaos.app.presentation.splash.SplashScreen

/**
 * The root of the app, written as one gate instead of a set of routes.
 *
 * Only four things can ever be composed here: the launch screen, the first-run wizard, the
 * account screens, and the app itself. Which one is decided by [SessionStage], computed from the
 * Firebase session, the account document and the local guest flag. There is no navigation route
 * to a protected screen, so a deep link, an intent, a restored back stack, an old activity state
 * or a stale local session has nothing to navigate to: the protected UI simply does not exist
 * until the gate opens.
 */
@Composable
fun RootNavHost(modifier: Modifier = Modifier) {
    val profile by ServiceLocator.user.profile.collectAsStateWithLifecycle()
    val account by ServiceLocator.account.state.collectAsStateWithLifecycle()
    val unreadNotifications by ServiceLocator.notifications.unreadCount.collectAsStateWithLifecycle()
    val onboardingCompleted by ServiceLocator.settings.onboardingCompleted
        .collectAsStateWithLifecycle(initialValue = true)

    var splashDone by rememberSaveable { mutableStateOf(false) }
    var onboardingDone by rememberSaveable { mutableStateOf(false) }
    var onboardingShown by rememberSaveable { mutableStateOf(false) }
    var guestPrompt by remember { mutableStateOf<GuestNeed?>(null) }

    val guestSession = profile?.sessionType == SessionType.GUEST

    // The gate a guest meets: features that only exist on an account ask here, and the two
    // buttons leave the guest session exactly the way signing out does, so the root gate opens
    // the account shell with the mode the reader picked.
    val guestGate = remember(guestSession) {
        GuestGate(
            isGuest = guestSession,
            showPrompt = { need -> guestPrompt = need },
            openAuth = { mode ->
                guestPrompt = null
                AuthRequests.request(mode)
                ServiceLocator.user.signOut()
            },
        )
    }
    val stage = account.stage(guestSession)

    // The wizard is answered once and never asked again; signing out restarts neither.
    val needsOnboarding = !onboardingCompleted && !onboardingDone

    if (!splashDone) {
        // The launch screen reads the session and the wizard state while it plays, so the first
        // real screen is chosen with resolved values rather than with defaults.
        SplashScreen(onFinished = { splashDone = true })
        return
    }

    if (needsOnboarding) {
        LaunchedEffect(Unit) { onboardingShown = true }
        OnboardingScreen(onFinished = { onboardingDone = true })
        return
    }
    // A wizard answered a moment ago writes its flag asynchronously; these keep the screen from
    // flickering back to the wizard while that write lands.
    if (onboardingShown && !onboardingCompleted) {
        SessionLoading()
        return
    }

    CompositionLocalProvider(LocalGuestGate provides guestGate) {
        Box(modifier = modifier.fillMaxSize()) {
            when (stage) {
                SessionStage.LOADING -> SessionLoading()
                SessionStage.SIGNED_OUT -> AuthScreen()
                SessionStage.EMAIL_VERIFICATION -> VerifyEmailScreen()
                SessionStage.ACCOUNT_SETUP -> AccountSetupScreen()
                SessionStage.GUEST, SessionStage.READY -> MainScaffold(
                    profile = profile,
                    unreadNotifications = unreadNotifications,
                )
            }
        }

        guestPrompt?.let { need ->
            GuestAccountDialog(
                need = need,
                onSignIn = { guestGate.start(AuthMode.SIGN_IN) },
                onSignUp = { guestGate.start(AuthMode.SIGN_UP) },
                onDismiss = { guestPrompt = null },
            )
        }
    }
}

/**
 * Shown while the session is still unresolved. It is deliberately not the launch animation: a
 * second splash after signing in would look like the app restarting.
 */
@Composable
private fun SessionLoading() {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppLogo(size = 64.dp)
                Spacer(Modifier.height(Spacing.md))
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.session_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
