package com.mangaos.app.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.Validators
import com.mangaos.app.data.firebase.AuthFailure
import com.mangaos.app.data.firebase.AuthReason
import com.mangaos.app.data.firebase.FirebaseAuthManager
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The screens of one shell. The shell is fixed; only its content changes. */
enum class AuthStage { WELCOME, EMAIL, PASSWORD, VERIFY, RESET, LINK }

enum class AuthMode { SIGN_IN, SIGN_UP }

/**
 * Drives the account shell: one stage at a time, one request at a time.
 *
 * A stage only advances when the step actually succeeded, so a loading state can never stand in
 * for a real sign-in. Google is handled as a credential: when it collides with an existing
 * password account the shell asks for that password and links Google to it instead of creating a
 * second account.
 */
class AuthViewModel : ViewModel() {

    var stage by mutableStateOf(AuthStage.WELCOME)
        private set
    var mode by mutableStateOf(AuthMode.SIGN_IN)
        private set

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")

    var emailError by mutableStateOf<Int?>(null)
    var passwordError by mutableStateOf<Int?>(null)
    var confirmError by mutableStateOf<Int?>(null)

    var failure by mutableStateOf<AuthReason?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var canResendVerification by mutableStateOf(true)
        private set
    var verificationSent by mutableStateOf(false)
        private set

    private var pendingGoogleToken: String? = null
    private var resendJob: Job? = null

    fun open(next: AuthStage, nextMode: AuthMode = mode) {
        if (isSubmitting) return
        stage = next
        mode = nextMode
        failure = null
        confirmPassword = ""
        confirmError = null
        passwordError = null
        emailError = null
    }

    fun back() {
        if (isSubmitting) return
        stage = when (stage) {
            AuthStage.WELCOME -> AuthStage.WELCOME
            AuthStage.EMAIL -> AuthStage.WELCOME
            AuthStage.PASSWORD -> AuthStage.EMAIL
            AuthStage.VERIFY -> AuthStage.VERIFY
            AuthStage.RESET -> AuthStage.EMAIL
            AuthStage.LINK -> AuthStage.EMAIL
        }
        failure = null
    }

    fun onEmailChange(value: String) {
        email = value.trim()
        emailError = null
        failure = null
    }

    fun onPasswordChange(value: String) {
        password = value
        passwordError = null
        confirmError = null
        failure = null
    }

    fun onConfirmChange(value: String) {
        confirmPassword = value
        confirmError = null
        failure = null
    }

    /** Step one: the address alone, so the keyboard shows the right layout for one field. */
    fun submitEmail() {
        emailError = when {
            email.isEmpty() -> R.string.error_required
            !Validators.isEmailValid(email) -> R.string.auth_error_email_invalid
            else -> null
        }
        if (emailError != null) return
        failure = null
        stage = when (mode) {
            AuthMode.SIGN_UP -> AuthStage.PASSWORD
            AuthMode.SIGN_IN -> AuthStage.PASSWORD
        }
    }

    fun submitPassword(onSignedIn: () -> Unit) {
        if (isSubmitting) return
        val valid = when (mode) {
            AuthMode.SIGN_IN -> {
                passwordError = when {
                    password.isEmpty() -> R.string.error_required
                    else -> null
                }
                passwordError == null
            }
            AuthMode.SIGN_UP -> {
                passwordError = when {
                    password.isEmpty() -> R.string.error_required
                    !Validators.isPasswordValid(password) -> R.string.auth_error_password_weak
                    else -> null
                }
                confirmError = when {
                    confirmPassword.isEmpty() -> R.string.error_required
                    !Validators.passwordsMatch(password, confirmPassword) -> R.string.auth_error_password_mismatch
                    else -> null
                }
                passwordError == null && confirmError == null
            }
        }
        if (!valid) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                val user = when (mode) {
                    AuthMode.SIGN_IN -> FirebaseAuthManager.signIn(email, password)
                    AuthMode.SIGN_UP -> FirebaseAuthManager.register(email, password, "")
                }
                if (!user.isEmailVerified) {
                    sendVerification(quiet = true)
                    stage = AuthStage.VERIFY
                } else {
                    onSignedIn()
                }
            } catch (error: AuthFailure) {
                failure = error.reason
            } finally {
                isSubmitting = false
            }
        }
    }

    /** Google for a fresh session, or Google linked to whoever is signed in right now. */
    fun google(token: String, onSignedIn: () -> Unit) {
        if (isSubmitting) return
        failure = null
        isSubmitting = true
        viewModelScope.launch {
            try {
                val linked = FirebaseAuthManager.user != null
                if (linked) {
                    FirebaseAuthManager.linkGoogle(token)
                    ServiceLocator.account?.refresh()
                } else {
                    FirebaseAuthManager.signInWithGoogle(token)
                }
                onSignedIn()
            } catch (error: AuthFailure) {
                if (error.reason == AuthReason.NEEDS_LINK) {
                    // The address already belongs to a password account: ask for that password
                    // and add Google to it, never create a duplicate account.
                    pendingGoogleToken = token
                    stage = AuthStage.LINK
                } else {
                    failure = error.reason
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    fun googleFailed(reason: AuthReason) {
        if (reason == AuthReason.CANCELLED) return
        failure = reason
    }

    /** Completes a Google/password collision by signing in, then linking Google to that user. */
    fun linkGoogleWithPassword(onSignedIn: () -> Unit) {
        if (isSubmitting) return
        val token = pendingGoogleToken ?: return
        passwordError = if (password.isEmpty()) R.string.error_required else null
        if (passwordError != null) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                FirebaseAuthManager.signInAndLinkGoogle(email, password, token)
                pendingGoogleToken = null
                onSignedIn()
            } catch (error: AuthFailure) {
                failure = error.reason
            } finally {
                isSubmitting = false
            }
        }
    }

    /** Sends the verification mail; a repeated tap is refused for a short while. */
    fun sendVerification(quiet: Boolean = false) {
        if (!canResendVerification) return
        canResendVerification = false
        verificationSent = true
        if (quiet) failure = null
        viewModelScope.launch {
            runCatching { FirebaseAuthManager.sendVerificationEmail() }
                .onFailure { error -> if (error is AuthFailure) failure = error.reason }
        }
        resendJob?.cancel()
        resendJob = viewModelScope.launch {
            delay(RESEND_COOLDOWN_MILLIS)
            canResendVerification = true
        }
    }

    /** Re-reads the account; advancing is the proof, not the tap. */
    fun checkVerification(onVerified: () -> Unit) {
        if (isSubmitting) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                val user = FirebaseAuthManager.reload()
                if (user?.isEmailVerified == true) onVerified() else failure = null
            } catch (error: AuthFailure) {
                failure = error.reason
            } finally {
                isSubmitting = false
            }
        }
    }

    fun resetPassword() {
        if (isSubmitting) return
        emailError = when {
            email.isEmpty() -> R.string.error_required
            !Validators.isEmailValid(email) -> R.string.auth_error_email_invalid
            else -> null
        }
        if (emailError != null) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                FirebaseAuthManager.sendPasswordReset(email)
                verificationSent = true
                failure = null
            } catch (error: AuthFailure) {
                failure = error.reason
            } finally {
                isSubmitting = false
            }
        }
    }

    fun continueAsGuest(onDone: () -> Unit) {
        if (isSubmitting) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                ServiceLocator.user.startGuestSession()
                onDone()
            } finally {
                isSubmitting = false
            }
        }
    }

    fun signOutSilently() {
        viewModelScope.launch { FirebaseAuthManager.signOut() }
    }

    private companion object {
        const val RESEND_COOLDOWN_MILLIS = 30_000L
    }
}
