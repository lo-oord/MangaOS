package com.mangaos.app.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.LocaleManager
import com.mangaos.app.core.util.Validators
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Owns the account flow draft and all local validation. No authentication happens here:
 * the future auth layer will replace [completeAccount] and [signIn] only.
 */
class AuthViewModel : ViewModel() {

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
        viewModelScope.launch {
            ServiceLocator.user.createAccount(
                displayName = displayName.trim(),
                username = username.trim(),
                avatarUri = avatarUri,
                countryCode = countryCode,
                age = age,
                preferredLanguage = preferredLanguage,
            )
            ServiceLocator.settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    fun signIn(onDone: () -> Unit) {
        viewModelScope.launch {
            val fallbackName = email.substringBefore('@').ifBlank { "reader" }
            val handle = fallbackName.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(20)
            ServiceLocator.user.createAccount(
                displayName = fallbackName,
                username = handle.ifBlank { "reader" },
                avatarUri = null,
                preferredLanguage = preferredLanguage,
            )
            ServiceLocator.settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    fun continueAsGuest(onDone: () -> Unit) {
        viewModelScope.launch {
            ServiceLocator.user.startGuestSession()
            ServiceLocator.settings.setOnboardingCompleted(true)
            onDone()
        }
    }
}
