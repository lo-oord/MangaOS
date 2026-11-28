package com.mangaos.app.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.Validators
import com.mangaos.app.data.local.LocalAuthStore
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.launch

/** The three states of the authentication shell; the shell itself never changes. */
enum class AuthStage { SIGN_IN, SIGN_UP, RESET }

/**
 * Owns the account form, its validation and the local credential check. The screen keeps one
 * shell and animates between [AuthStage]s; this ViewModel is the single source of truth for the
 * fields, the error and the busy state, so switching stage never loses what was typed.
 */
class AuthViewModel : ViewModel() {

    var stage by mutableStateOf(AuthStage.SIGN_IN)
        private set

    /** One short message for the whole form (unknown address, wrong password, taken address). */
    var formError by mutableStateOf<Int?>(null)
        private set

    /** Sets after a successful operation, so the shell can show its confirmation state. */
    var succeeded by mutableStateOf(false)
        private set

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var displayName by mutableStateOf("")

    var emailError by mutableStateOf<Int?>(null)
    var passwordError by mutableStateOf<Int?>(null)
    var confirmError by mutableStateOf<Int?>(null)
    var nameError by mutableStateOf<Int?>(null)

    /** Blocks repeated taps while the account is being written. */
    var isSubmitting by mutableStateOf(false)
        private set

    fun openStage(next: AuthStage) {
        if (isSubmitting) return
        stage = next
        formError = null
        succeeded = false
    }

    fun onEmailChange(value: String) {
        email = value.trim()
        emailError = null
        formError = null
    }

    fun onPasswordChange(value: String) {
        password = value
        passwordError = null
        confirmError = null
    }

    fun onConfirmChange(value: String) {
        confirmPassword = value
        confirmError = null
    }

    fun onDisplayNameChange(value: String) {
        displayName = value
        nameError = null
    }

    private fun validateSignIn(): Boolean {
        emailError = when {
            email.isEmpty() -> R.string.error_required
            !Validators.isEmailValid(email) -> R.string.error_email_invalid
            else -> null
        }
        passwordError = when {
            password.isEmpty() -> R.string.error_required
            !Validators.isPasswordValid(password) -> R.string.error_password_short
            else -> null
        }
        confirmError = null
        return emailError == null && passwordError == null
    }

    private fun validateNewPassword(): Boolean {
        val credentialsValid = validateSignIn()
        confirmError = when {
            confirmPassword.isEmpty() -> R.string.error_required
            !Validators.passwordsMatch(password, confirmPassword) -> R.string.error_password_mismatch
            else -> null
        }
        return credentialsValid && confirmError == null
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

    /** Sign-in against the stored credentials; a wrong password is reported, never accepted. */
    fun signInAccount(onDone: () -> Unit) {
        if (isSubmitting) return
        formError = null
        if (!validateSignIn()) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                when (ServiceLocator.localAuth.signIn(email, password)) {
                    is LocalAuthStore.Result.Success -> openSession(onDone)
                    LocalAuthStore.Result.UnknownEmail -> formError = R.string.auth_error_no_account
                    LocalAuthStore.Result.WrongPassword -> formError = R.string.auth_error_wrong_password
                    else -> formError = R.string.auth_error_generic
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    /** Creates the account and its session; an address that already exists is refused. */
    fun signUpAccount(onDone: () -> Unit) {
        if (isSubmitting) return
        formError = null
        nameError = if (displayName.isBlank()) R.string.error_required else null
        if (!validateNewPassword() || nameError != null) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                val result = ServiceLocator.localAuth.register(
                    email = email,
                    password = password,
                    displayName = displayName.trim(),
                    username = handleFor(displayName.trim().ifBlank { email.substringBefore('@') }),
                )
                when (result) {
                    is LocalAuthStore.Result.Success -> openSession(onDone)
                    LocalAuthStore.Result.EmailTaken -> {
                        emailError = R.string.auth_error_email_taken
                        formError = R.string.auth_error_email_taken
                    }
                    else -> formError = R.string.auth_error_generic
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    /** Replaces the password of an existing account, then confirms inside the same shell. */
    fun resetPassword(onDone: () -> Unit) {
        if (isSubmitting) return
        formError = null
        if (!validateNewPassword()) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                when (ServiceLocator.localAuth.resetPassword(email, password)) {
                    is LocalAuthStore.Result.Success -> {
                        succeeded = true
                        onDone()
                    }
                    LocalAuthStore.Result.UnknownEmail -> {
                        emailError = R.string.auth_error_no_account
                        formError = R.string.auth_error_no_account
                    }
                    else -> formError = R.string.auth_error_generic
                }
            } finally {
                isSubmitting = false
            }
        }
    }

    /** A handle derived from a name: letters, digits, '_' and '.' only. */
    private fun handleFor(source: String): String =
        source.filter { it.isLetterOrDigit() || it == '_' || it == '.' }
            .take(20)
            .ifBlank { "reader" }

    private fun openSession(onDone: () -> Unit) {
        val fallbackName = displayName.trim().ifBlank { email.substringBefore('@') }
        ServiceLocator.user.createAccount(
            displayName = fallbackName.ifBlank { "reader" },
            username = handleFor(fallbackName),
            avatarUri = null,
            email = email.trim().ifBlank { null },
            countryCode = null,
            age = null,
            preferredLanguage = null,
        )
        succeeded = true
        onDone()
    }
}
