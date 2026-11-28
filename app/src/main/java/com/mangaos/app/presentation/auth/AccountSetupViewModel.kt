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

/**
 * The two things a new account still needs: how it is called, and its optional preferences.
 * The photo lives with the name, so the first step the reader sees already carries every field
 * the account will keep.
 */
enum class SetupStep { PROFILE, PREFERENCES }

enum class UsernameState { UNKNOWN, CHECKING, AVAILABLE, TAKEN, INVALID }

/**
 * The half of onboarding a new account still needs. Google accounts arrive without a username,
 * so this is a first-class step rather than an edit afterwards.
 *
 * Seeding happens once per account and only into empty fields: a value the reader deleted must
 * stay deleted, which is exactly what a seed on every recomposition used to break.
 */
class AccountSetupViewModel : ViewModel() {

    private val accounts: AccountRepository get() = ServiceLocator.accounts

    var step by mutableStateOf(SetupStep.PROFILE)
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
    private var seededUid: String? = null

    /**
     * Fills the form once for [uid]. An account that already owns a username resumes with it
     * instead of being asked for a new one, and nothing is ever re-filled after a deletion.
     */
    fun seed(uid: String, displayNameSuggestion: String, existingUsername: String) {
        if (seededUid == uid) return
        seededUid = uid
        val suggestion = displayNameSuggestion.trim()
        if (displayName.isBlank()) displayName = suggestion.take(MAX_DISPLAY_NAME)
        val existing = existingUsername.trim().lowercase()
        if (existing.isNotBlank()) {
            if (username.isBlank()) username = existing
            if (username == existing) {
                usernameState = UsernameState.AVAILABLE
                usernameMessage = null
            }
            return
        }
        if (username.isBlank() && suggestion.isNotBlank()) {
            username = suggestion
                .filter { it.isLetterOrDigit() || it == '_' }
                .lowercase()
                .take(Validators.MAX_USERNAME_LENGTH)
                .takeIf { Validators.usernameError(it) == null }
                .orEmpty()
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
            checkJob?.cancel()
            return
        }
        usernameState = UsernameState.CHECKING
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            // Debounce: a keystroke burst costs one request, not one request per letter.
            delay(USERNAME_DEBOUNCE_MILLIS)
            val uid = FirebaseAuthManager.uid ?: return@launch
            val available = runCatching { accounts.isUsernameAvailable(username, uid) }
                .getOrDefault(false)
            usernameState = if (available) UsernameState.AVAILABLE else UsernameState.TAKEN
        }
    }

    fun onDisplayNameChange(value: String) {
        displayName = value.take(MAX_DISPLAY_NAME)
        failure = null
        usernameMessage = null
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
            SetupStep.PROFILE -> {
                if (displayName.isBlank()) {
                    usernameMessage = R.string.error_required
                    return
                }
                if (usernameState != UsernameState.AVAILABLE) {
                    usernameMessage = R.string.setup_username_unavailable
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
            SetupStep.PROFILE -> SetupStep.PROFILE
            SetupStep.PREFERENCES -> SetupStep.PROFILE
        }
    }

    /** Writes the whole setup in one field-level update and marks the account complete. */
    fun finish(avatarBase64: String?, onDone: () -> Unit) {
        if (isSaving) return
        val uid = FirebaseAuthManager.uid ?: return
        // The root gate already refuses this screen to an unconfirmed address; the check stays
        // here as well so no future caller can complete an account without it.
        if (!FirebaseAuthManager.isEmailVerified) {
            failure = AuthReason.EMAIL_NOT_VERIFIED
            return
        }
        if (displayName.isBlank()) {
            usernameMessage = R.string.error_required
            step = SetupStep.PROFILE
            return
        }
        isSaving = true
        viewModelScope.launch {
            try {
                val previous = ServiceLocator.account?.state?.value?.account?.username
                if (previous?.lowercase() != username) {
                    val claimed = accounts.claimUsername(uid, username, previous)
                    if (!claimed) {
                        usernameState = UsernameState.TAKEN
                        usernameMessage = R.string.setup_username_unavailable
                        step = SetupStep.PROFILE
                        return@launch
                    }
                }
                val fields = mutableMapOf<String, Any?>(
                    "username" to username,
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
        const val MAX_DISPLAY_NAME = 40
    }
}
