package com.mangaos.app.data.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The root gate is the only thing standing between a session and the app's screens, so every
 * combination that matters is pinned here: signed out, guest, unverified, incomplete, complete,
 * and the unresolved session that must not guess.
 */
class SessionGateTest {

    private fun stage(
        loading: Boolean = false,
        signedIn: Boolean = false,
        emailVerified: Boolean = false,
        setupCompleted: Boolean = false,
        hasUsername: Boolean = false,
        guest: Boolean = false,
    ) = SessionGate.resolve(loading, signedIn, emailVerified, setupCompleted, hasUsername, guest)

    @Test
    fun `an unresolved session is never guessed`() {
        assertEquals(SessionStage.LOADING, stage(loading = true))
        // Even a signed-in user with every flag set waits for the session to resolve.
        assertEquals(
            SessionStage.LOADING,
            stage(loading = true, signedIn = true, emailVerified = true, setupCompleted = true, hasUsername = true),
        )
    }

    @Test
    fun `no session and no guest is signed out`() {
        assertEquals(SessionStage.SIGNED_OUT, stage())
    }

    @Test
    fun `a guest session may use the app without an account`() {
        assertEquals(SessionStage.GUEST, stage(guest = true))
    }

    @Test
    fun `a guest flag never masks a signed-in account`() {
        assertEquals(
            SessionStage.EMAIL_VERIFICATION,
            stage(signedIn = true, guest = true),
        )
    }

    @Test
    fun `an unconfirmed address closes setup and the app`() {
        // Exactly the reported bug: Firebase signs in, the account is complete, yet the address
        // is not confirmed. Nothing may open before it is.
        assertEquals(
            SessionStage.EMAIL_VERIFICATION,
            stage(signedIn = true, emailVerified = false, setupCompleted = true, hasUsername = true),
        )
        assertEquals(
            SessionStage.EMAIL_VERIFICATION,
            stage(signedIn = true, emailVerified = false),
        )
    }

    @Test
    fun `an incomplete account always lands on setup`() {
        assertEquals(
            SessionStage.ACCOUNT_SETUP,
            stage(signedIn = true, emailVerified = true, setupCompleted = false, hasUsername = false),
        )
        assertEquals(
            SessionStage.ACCOUNT_SETUP,
            stage(signedIn = true, emailVerified = true, setupCompleted = false, hasUsername = true),
        )
        // A completed flag with no username is still an incomplete account.
        assertEquals(
            SessionStage.ACCOUNT_SETUP,
            stage(signedIn = true, emailVerified = true, setupCompleted = true, hasUsername = false),
        )
    }

    @Test
    fun `a verified account with a username opens the app`() {
        assertEquals(
            SessionStage.READY,
            stage(signedIn = true, emailVerified = true, setupCompleted = true, hasUsername = true),
        )
    }

    @Test
    fun `only ready and guest may show app screens`() {
        assertTrue(SessionGate.allowsApp(SessionStage.READY))
        assertTrue(SessionGate.allowsApp(SessionStage.GUEST))
        listOf(
            SessionStage.LOADING,
            SessionStage.SIGNED_OUT,
            SessionStage.EMAIL_VERIFICATION,
            SessionStage.ACCOUNT_SETUP,
        ).forEach { assertFalse(it.name, SessionGate.allowsApp(it)) }
    }

    @Test
    fun `account state reports the same stage as the gate`() {
        val state = AccountState(
            loading = false,
            uid = "uid",
            email = "a@b.c",
            emailVerified = true,
            account = AccountDoc(uid = "uid", username = "reader", setupCompleted = true),
        )
        assertEquals(SessionStage.READY, state.stage(guestSession = false))
        assertEquals(SessionStage.SIGNED_OUT, AccountState(loading = false).stage(guestSession = false))
        assertEquals(SessionStage.GUEST, AccountState(loading = false).stage(guestSession = true))
    }

    @Test
    fun `the verification mirror is read from the account document`() {
        val verified = AccountDoc.from("uid", mapOf("isVerified" to true))
        val plain = AccountDoc.from("uid", mapOf("verified" to true))
        assertTrue(verified.isVerified)
        // The old, free-form key must not be mistaken for the badge.
        assertFalse(plain.isVerified)
    }
}
