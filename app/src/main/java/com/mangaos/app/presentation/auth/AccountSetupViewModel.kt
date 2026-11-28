package com.mangaos.app.presentation.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.Validators
import com.mangaos.app.data.firebase.AccountRepository
import com.mangaos.app.data.firebase.AuthFailure
import com.mangaos.app.data.firebase.AuthReason
import com.mangaos.app.data.firebase.FirebaseAuthManager
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SetupStep { USERNAME, PROFILE, PREFERENCES }

enum class UsernameState { UNKNOWN, CHECKING, AVAILABLE, TAKEN, INVALID }

/**
 * The half of onboarding a new account still needs. Google accounts arrive without a username,
 * so this is a first-class step rather than an edit afterwards. Progress is written to the
 * account document as each step completes, which is what makes closing the app harmless.
 */
class AccountSetupViewModel : ViewModel() {

    private val accounts: AccountRepository get() = ServiceLocator.accounts

    var step by mutableStateOf(SetupStep.USERNAME)
        private set

    var username by mutableStateOf("")
        private set
    var usernameState by mutableStateOf(UsernameState.UNKNOWN)
        private set
    var displayName by mutableStateOf("")
        private set
    var language by mutableStateOf("ar")
        private set
    var countryCode by mutableStateOf<String?>(null)
        private set
    var age by mutableStateOf<Int?>(null)
        private set

    var isSaving by mutableStateOf(false)
        private set
    var failure by mutableStateOf<AuthReason?>(null)
        private set
    var usernameMessage by mutableStateOf<Int?>(null)
        private set

    private var checkJob: Job? = null

    fun seed(displayNameSuggestion: String) {
        if (displayName.isBlank()) displayName = displayNameSuggestion
        if (username.isBlank()) {
            username = displayNameSuggestion
                .filter { it.isLetterOrDigit() || it == '_' }
                .lowercase()
                .take(Validators.MAX_USERNAME_LENGTH)
        }
    }

    fun onUsernameChange(value: String) {
        username = value.trim().removePrefix("@").lowercase()
        usernameMessage = null
        failure = null
        val error = Validators.usernameError(username)
        if (error != null) {
            usernameState = UsernameState.INVALID
            usernameMessage = when (error) {
                Validators.UsernameError.EMPTY -> R.string.error_required
                Validators.UsernameError.LENGTH -> R.string.error_username_length
                Validators.UsernameError.CHARACTERS -> R.string.error_username_chars
            }
            return
        }
        usernameState = UsernameState.CHECKING
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            // Debounce: a keystroke burst costs one request, not one request per letter.
            delay(USERNAME_DEBOUNCE_MILLIS)
            val uid = FirebaseAuthManager.uid ?: return@launch
            val available = runCatching { accounts.isUsernameAvailable(username, uid) }.getOrDefault(false)
            usernameState = if (available) UsernameState.AVAILABLE else UsernameState.TAKEN
        }
    }

    fun onDisplayNameChange(value: String) {
        displayName = value.take(40)
        failure = null
    }

    fun onLanguageChange(code: String) {
        language = code
    }

    fun onCountryChange(code: String?) {
        countryCode = code
    }

    fun onAgeChange(value: Int?) {
        age = value
    }

    fun next() {
        failure = null
        when (step) {
            SetupStep.USERNAME -> {
                if (usernameState != UsernameState.AVAILABLE) {
                    usernameMessage = R.string.setup_username_unavailable
                    return
                }
                step = SetupStep.PROFILE
            }
            SetupStep.PROFILE -> {
                if (displayName.isBlank()) {
                    usernameMessage = R.string.error_required
                    return
                }
                step = SetupStep.PREFERENCES
            }
            SetupStep.PREFERENCES -> Unit
        }
    }

    fun back() {
        failure = null
        step = when (step) {
            SetupStep.USERNAME -> SetupStep.USERNAME
            SetupStep.PROFILE -> SetupStep.USERNAME
            SetupStep.PREFERENCES -> SetupStep.PROFILE
        }
    }

    /** Writes the whole setup in one field-level update and marks the account complete. */
    fun finish(avatarBase64: String?, onDone: () -> Unit) {
        if (isSaving) return
        val uid = FirebaseAuthManager.uid ?: return
        if (displayName.isBlank()) {
            usernameMessage = R.string.error_required
            return
        }
        isSaving = true
        viewModelScope.launch {
            try {
                val previous = ServiceLocator.account?.state?.value?.account?.username
                val claimed = accounts.claimUsername(uid, username, previous)
                if (!claimed) {
                    usernameState = UsernameState.TAKEN
                    usernameMessage = R.string.setup_username_unavailable
                    step = SetupStep.USERNAME
                    return@launch
                }
                val fields = mutableMapOf<String, Any?>("username" to username) +
                    mapOf(
                        "displayName" to displayName.trim(),
                        "language" to language,
                        "country" to countryCode,
                        "age" to age,
                        "setupCompleted" to true,
                    )
                if (avatarBase64 != null) fields["photo"] = avatarBase64
                accounts.updateAccount(uid, fields)
                if (language.isNotBlank()) ServiceLocator.settings.setLanguage(language)
                onDone()
            } catch (error: AuthFailure) {
                failure = error.reason
            } catch (_: Exception) {
                failure = AuthReason.NETWORK
            } finally {
                isSaving = false
            }
        }
    }

    private companion object {
        const val USERNAME_DEBOUNCE_MILLIS = 450L
    }
}
