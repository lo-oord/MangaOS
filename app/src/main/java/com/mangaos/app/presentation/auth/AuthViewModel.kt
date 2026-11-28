package com.mangaos.app.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import android.net.Uri
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.core.util.Validators
import com.mangaos.app.data.local.AvatarStore
import com.mangaos.app.data.local.LocalAuthStore
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.launch

/** The three states of the authentication shell; the shell itself never changes. */
enum class AuthStage { SIGN_IN, SIGN_UP, RESET }

/**
 * Owns the account flow draft, its validation and the local credential check. The screen keeps
 * one shell and animates between [AuthStage]s; this ViewModel is the single source of truth for
 * the fields, the error and the busy state, so switching stage never loses what was typed.
 */
class AuthViewModel : ViewModel() {

    var stage by mutableStateOf(AuthStage.SIGN_IN)
        private set

    /** One short message for the whole form (credentials, existing account, wrong password). */
    var formError by mutableStateOf<Int?>(null)
        private set

    /** Sets after a successful operation, so the shell can show its confirmation state. */
    var succeeded by mutableStateOf(false)
        private set

    fun openStage(next: AuthStage) {
        if (isSubmitting) return
        stage = next
        formError = null
        succeeded = false
    }

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var displayName by mutableStateOf("")
    var username by mutableStateOf("")
    var avatarUri by mutableStateOf<String?>(null)
    var code by mutableStateOf("")

    var preferredLanguage by mutableStateOf(LocaleManager.readLanguage(ServiceLocator.context()))
    var countryCode by mutableStateOf<String?>(null)
    var age by mutableStateOf<Int?>(null)

    var emailError by mutableStateOf<Int?>(null)
    var passwordError by mutableStateOf<Int?>(null)
    var confirmError by mutableStateOf<Int?>(null)
    var nameError by mutableStateOf<Int?>(null)
    var usernameError by mutableStateOf<Int?>(null)
    var codeError by mutableStateOf<Int?>(null)
    var ageError by mutableStateOf<Int?>(null)

    /** Blocks repeated taps while the local account is being written. */
    var isSubmitting by mutableStateOf(false)
        private set

    fun onEmailChange(value: String) {
        email = value.trim()
        emailError = null
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

    fun onUsernameChange(value: String) {
        username = value.filterNot { it.isWhitespace() }
        usernameError = null
    }

    fun onCodeChange(value: String) {
        code = value.filter(Char::isDigit).take(Validators.VERIFICATION_CODE_LENGTH)
        codeError = null
    }

    fun onAvatarPicked(uri: String) {
        avatarUri = uri
    }

    fun onPreferredLanguageChange(language: String) {
        preferredLanguage = language
    }

    fun onCountryChange(country: String?) {
        countryCode = country
    }

    fun onAgeChange(value: Int?) {
        age = value
        ageError = null
    }

    fun validateCredentials(): Boolean {
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
        confirmError = when {
            confirmPassword.isEmpty() -> R.string.error_required
            !Validators.passwordsMatch(password, confirmPassword) -> R.string.error_password_mismatch
            else -> null
        }
        return emailError == null && passwordError == null && confirmError == null
    }

    fun validateSignIn(): Boolean {
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

    fun validateProfile(): Boolean {
        nameError = if (displayName.isBlank()) R.string.error_required else null
        usernameError = when (Validators.usernameError(username)) {
            null -> null
            Validators.UsernameError.EMPTY -> R.string.error_required
            Validators.UsernameError.LENGTH -> R.string.error_username_length
            Validators.UsernameError.CHARACTERS -> R.string.error_username_chars
        }
        ageError = when {
            age == null -> null
            age!! < Validators.MIN_AGE -> R.string.error_age_min
            else -> null
        }
        return nameError == null && usernameError == null && ageError == null
    }

    fun validateCode(): Boolean {
        codeError = if (!Validators.isVerificationCodeComplete(code)) R.string.error_code_incomplete else null
        return codeError == null
    }

    fun completeAccount(onDone: () -> Unit) {
        if (isSubmitting) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                val storedAvatar = avatarUri?.let { picked ->
                    runCatching {
                        AvatarStore.save(ServiceLocator.context(), Uri.parse(picked))
                    }.getOrNull()
                }
                ServiceLocator.user.createAccount(
                    displayName = displayName.trim(),
                    username = username.trim(),
                    avatarUri = storedAvatar,
                    email = email.trim().ifBlank { null },
                    countryCode = countryCode,
                    age = age,
                    preferredLanguage = preferredLanguage,
                )
                ServiceLocator.settings.setOnboardingCompleted(true)
                onDone()
            } finally {
                isSubmitting = false
            }
        }
    }

    fun signIn(onDone: () -> Unit) {
        if (isSubmitting) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                val fallbackName = email.substringBefore('@').ifBlank { "reader" }
                val handle = fallbackName.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(20)
                ServiceLocator.user.createAccount(
                    displayName = fallbackName,
                    username = handle.ifBlank { "reader" },
                    avatarUri = null,
                    email = email.trim().ifBlank { null },
                    preferredLanguage = preferredLanguage,
                )
                ServiceLocator.settings.setOnboardingCompleted(true)
                onDone()
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
        if (!validateCredentials() || nameError != null) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                val result = ServiceLocator.localAuth.register(
                    email = email,
                    password = password,
                    displayName = displayName,
                    username = username,
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
        val emailValid = validateSignIn()
        confirmError = when {
            confirmPassword.isEmpty() -> R.string.error_required
            !Validators.passwordsMatch(password, confirmPassword) -> R.string.error_password_mismatch
            else -> null
        }
        if (!emailValid || confirmError != null) return
        isSubmitting = true
        viewModelScope.launch {
            try {
                when (ServiceLocator.localAuth.resetPassword(email, password)) {
                    is LocalAuthStore.Result.Success -> succeeded = true
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

    private fun openSession(onDone: () -> Unit) {
        val fallbackName = displayName.trim().ifBlank { email.substringBefore('@') }
        val handle = username.trim().ifBlank {
            fallbackName.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(20)
        }
        ServiceLocator.user.createAccount(
            displayName = fallbackName.ifBlank { "reader" },
            username = handle.ifBlank { "reader" },
            avatarUri = null,
            email = email.trim().ifBlank { null },
            countryCode = countryCode,
            age = age,
            preferredLanguage = preferredLanguage,
        )
        succeeded = true
        onDone()
    }
}
