package com.mangaos.app.presentation.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppDialog
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.SecondaryButton
import com.mangaos.app.presentation.auth.AuthMode

/**
 * What a guest tried to do. Each need has its own wording, so the prompt explains the actual
 * consequence ("added to your account") instead of a generic "sign in to continue".
 */
enum class GuestNeed(val titleRes: Int, val messageRes: Int) {
    LIBRARY(R.string.guest_library_title, R.string.guest_library_message),
    SYNC(R.string.guest_sync_title, R.string.guest_sync_message),
}

/**
 * The account gate for guest sessions.
 *
 * One object is provided by the root host, so no screen has to thread a flag and two callbacks
 * through its whole tree: a screen either asks [require] to explain why the feature needs an
 * account, or [start] to open the shell directly. A signed-in reader gets an inert gate, so call
 * sites can read [isGuest] and keep their normal path.
 */
class GuestGate(
    val isGuest: Boolean,
    private val showPrompt: (GuestNeed) -> Unit = {},
    private val openAuth: (AuthMode) -> Unit = {},
) {
    fun require(need: GuestNeed) = showPrompt(need)

    fun start(mode: AuthMode) = openAuth(mode)
}

val LocalGuestGate = staticCompositionLocalOf { GuestGate(isGuest = false) }

/**
 * The prompt itself: the same glass dialog as the rest of the app, with the two actions a guest
 * actually needs side by side — create an account, or sign in to the one they already have.
 */
@Composable
fun GuestAccountDialog(
    need: GuestNeed,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = stringResource(need.titleRes),
        message = stringResource(need.messageRes),
        confirmLabel = stringResource(R.string.action_cancel),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        icon = MangaOsIcons.account,
        content = {
            Spacer(Modifier.height(Spacing.xs))
            PrimaryButton(
                text = stringResource(R.string.auth_flow_create),
                onClick = onSignUp,
                icon = MangaOsIcons.account,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            SecondaryButton(
                text = stringResource(R.string.auth_flow_sign_in),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
