package com.mangaos.app.data.firebase

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The only place the app talks to Firebase Authentication.
 *
 * Everything here keeps the account's UID stable: linking a provider adds a credential to the
 * signed-in user, it never creates a second account. Password operations are `link`/`update`
 * calls for exactly that reason.
 */
object FirebaseAuthManager {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val user: FirebaseUser? get() = auth.currentUser
    val uid: String? get() = auth.currentUser?.uid
    val email: String? get() = auth.currentUser?.email

    val hasPassword: Boolean
        get() = hasProvider(EmailAuthProvider.PROVIDER_ID)

    val hasGoogle: Boolean
        get() = hasProvider(GoogleAuthProvider.PROVIDER_ID)

    val isEmailVerified: Boolean
        get() = auth.currentUser?.isEmailVerified == true

    private fun hasProvider(providerId: String): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == providerId } == true

    /** Emits on every sign-in, sign-out and token refresh of the active session. */
    fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String): FirebaseUser = authorized {
        auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()?.user
            ?: throw AuthFailure(AuthReason.GENERIC)
    }

    suspend fun register(email: String, password: String, displayName: String): FirebaseUser = authorized {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        val created = result?.user ?: throw AuthFailure(AuthReason.GENERIC)
        val name = displayName.trim()
        if (name.isNotEmpty()) {
            runCatching {
                created.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).awaitUnit()
            }
        }
        created
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser = authorized {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).awaitResult()?.user ?: throw AuthFailure(AuthReason.GENERIC)
    }

    /** Links Google to the user that is already signed in; the UID does not change. */
    suspend fun linkGoogle(idToken: String) = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        current.linkWithCredential(credential).awaitUnit()
    }

    suspend fun unlinkGoogle() = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        current.unlink(GoogleAuthProvider.PROVIDER_ID).awaitUnit()
    }

    /**
     * Gives a Google-only account a password. The credential is linked to the current user, so
     * the UID, the library and the Google provider all stay exactly as they are.
     */
    suspend fun setPassword(password: String) = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        val address = current.email ?: throw AuthFailure(AuthReason.INVALID_EMAIL)
        val credential = EmailAuthProvider.getCredential(address, password)
        current.linkWithCredential(credential).awaitUnit()
    }

    suspend fun changePassword(newPassword: String) = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        current.updatePassword(newPassword).awaitUnit()
    }

    /** Proves the password again before a sensitive operation; required by Firebase. */
    suspend fun reauthenticate(password: String) = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        val address = current.email ?: throw AuthFailure(AuthReason.INVALID_EMAIL)
        current.reauthenticate(EmailAuthProvider.getCredential(address, password)).awaitUnit()
    }

    /**
     * Signs in with the password after a Google attempt collided with an existing account, then
     * links Google to it. This is the safe path that avoids a duplicate account.
     */
    suspend fun signInAndLinkGoogle(email: String, password: String, idToken: String): FirebaseUser {
        val signedIn = signIn(email, password)
        linkGoogle(idToken)
        return signedIn
    }

    suspend fun sendVerificationEmail() = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        current.sendEmailVerification().awaitUnit()
    }

    /** Re-reads the account so `emailVerified` and the provider list are current. */
    suspend fun reload(): FirebaseUser? = authorized {
        val current = auth.currentUser
        current?.reload()?.awaitUnit()
        auth.currentUser
    }

    suspend fun sendPasswordReset(email: String) = authorized {
        auth.sendPasswordResetEmail(email.trim()).awaitUnit()
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun deleteAccount() = authorized {
        val current = auth.currentUser ?: throw AuthFailure(AuthReason.NOT_CONFIGURED)
        current.delete().awaitUnit()
    }

    private suspend fun <T> authorized(block: suspend () -> T): T = try {
        block()
    } catch (failure: AuthFailure) {
        throw failure
    } catch (exception: FirebaseAuthException) {
        throw AuthFailure(exception.toAuthReason(), exception.message)
    } catch (exception: Exception) {
        val code = runCatching {
            (exception as? com.google.firebase.FirebaseNetworkException)?.let { AuthReason.NETWORK }
        }.getOrNull()
        throw AuthFailure(code ?: AuthReason.GENERIC, exception.message)
    }
}
