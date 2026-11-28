package com.mangaos.app.data.firebase

/**
 * The only states the root of the app can be in.
 *
 * The root host renders one of these and nothing else, so a screen cannot be reached by a route,
 * a deep link, an intent, a restored back stack or a stale local value: if the stage is not
 * [READY] or [GUEST], the signed-in UI is not composed at all.
 */
enum class SessionStage {
    /** Firebase has not reported a session yet; the app must not guess. */
    LOADING,

    /** No Firebase session and no guest session: only the authentication shell is available. */
    SIGNED_OUT,

    /** The reader chose to browse without an account; nothing account-specific is written. */
    GUEST,

    /** Signed in, but the address is not confirmed: setup and every screen stay closed. */
    EMAIL_VERIFICATION,

    /** Signed in and verified, but the account document is incomplete (no username, no setup). */
    ACCOUNT_SETUP,

    /** A verified, complete account: the app itself. */
    READY,
}

/**
 * Pure decision table, kept free of Android and Firebase so it can be unit tested directly and so
 * there is exactly one place that decides whether a screen is allowed to exist.
 *
 * The order matters. Loading first, because an unresolved session would otherwise send a signed-in
 * reader to the sign-in screen or a new Google account to the home screen. Verification next,
 * because an unverified address must never reach the setup or the app. Setup last, because an
 * incomplete account is still authenticated but has nothing to show yet.
 */
object SessionGate {

    fun resolve(
        loading: Boolean,
        signedIn: Boolean,
        emailVerified: Boolean,
        setupCompleted: Boolean,
        hasUsername: Boolean,
        guestSession: Boolean,
    ): SessionStage = when {
        loading -> SessionStage.LOADING
        !signedIn -> if (guestSession) SessionStage.GUEST else SessionStage.SIGNED_OUT
        !emailVerified -> SessionStage.EMAIL_VERIFICATION
        !setupCompleted || !hasUsername -> SessionStage.ACCOUNT_SETUP
        else -> SessionStage.READY
    }

    /** True for the two stages that may show the app's own screens. */
    fun allowsApp(stage: SessionStage): Boolean =
        stage == SessionStage.READY || stage == SessionStage.GUEST
}
