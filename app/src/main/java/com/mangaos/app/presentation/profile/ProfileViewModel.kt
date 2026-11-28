package com.mangaos.app.presentation.profile

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.Validators
import com.mangaos.app.data.firebase.AccountState
import com.mangaos.app.data.firebase.AuthFailure
import com.mangaos.app.data.firebase.AuthReason
import com.mangaos.app.data.firebase.AvatarCodec
import com.mangaos.app.data.firebase.CloudSync
import com.mangaos.app.data.firebase.FirebaseAuthManager
import com.mangaos.app.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class UsernameCheck { IDLE, CHECKING, AVAILABLE, TAKEN }

/**
 * The profile's single source of truth: the account document, the Firebase session and the sync
 * status. Every write goes to Firestore first and the screen follows the document, so two
 * devices never disagree about what was saved.
 */
class ProfileViewModel : ViewModel() {

    val state: StateFlow<AccountState> = ServiceLocator.account.state
    val syncStatus: StateFlow<CloudSync.Status> = ServiceLocator.cloudSync.status

    var isSaving by mutableStateOf(false)
        private set
    var busyAction by mutableStateOf<String?>(null)
        private set
    var failure by mutableStateOf<AuthReason?>(null)
        private set
    var notice by mutableStateOf<Int?>(null)
        private set
    var usernameCheck by mutableStateOf(UsernameCheck.IDLE)
        private set

    private var checkJob: Job? = null

    fun clearMessages() {
        failure = null
        notice = null
        usernameCheck = UsernameCheck.IDLE
    }

    fun saveDisplayName(value: String, onResult: (Boolean) -> Unit) {
        val name = value.trim()
        if (name.isEmpty()) {
            failure = AuthReason.GENERIC
            onResult(false)
            return
        }
        mutate(onResult) { uid -> mapOf("displayName" to name) }
    }

    /** Checking is a courtesy; the transaction in the repository is what guarantees uniqueness. */
    fun checkUsername(value: String) {
        val username = value.trim().removePrefix("@").lowercase()
        usernameCheck = UsernameCheck.IDLE
        if (Validators.usernameError(username) != null) return
        val uid = FirebaseAuthManager.uid ?: return
        val currentUsername = state.value.account?.username
        if (username == currentUsername?.lowercase()) {
            usernameCheck = UsernameCheck.AVAILABLE
            return
        }
        usernameCheck = UsernameCheck.CHECKING
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            delay(450)
            val available = runCatching {
                ServiceLocator.accounts.isUsernameAvailable(username, uid)
            }.getOrDefault(false)
            usernameCheck = if (available) UsernameCheck.AVAILABLE else UsernameCheck.TAKEN
        }
    }

    fun saveUsername(value: String, onResult: (Boolean) -> Unit) {
        val username = value.trim().removePrefix("@").lowercase()
        val error = Validators.usernameError(username)
        if (error != null) {
            failure = AuthReason.GENERIC
            onResult(false)
            return
        }
        val uid = FirebaseAuthManager.uid ?: return
        val previous = state.value.account?.username
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                if (!ServiceLocator.accounts.claimUsername(uid, username, previous)) {
                    usernameCheck = UsernameCheck.TAKEN
                    onResult(false)
                    return@launch
                }
                ServiceLocator.accounts.updateAccount(uid, mapOf("username" to username))
                onResult(true)
            } catch (_: Exception) {
                failure = AuthReason.NETWORK
                onResult(false)
            } finally {
                isSaving = false
            }
        }
    }

    fun saveLanguage(code: String, onResult: (Boolean) -> Unit) {
        mutate(onResult) { uid ->
            viewModelScope.launch { runCatching { ServiceLocator.settings.setLanguage(code) } }
            mapOf("language" to code)
        }
    }

    fun saveCountry(code: String?, onResult: (Boolean) -> Unit) =
        mutate(onResult) { _ -> mapOf("country" to code) }

    fun saveAge(age: Int?, onResult: (Boolean) -> Unit) =
        mutate(onResult) { _ -> mapOf("age" to age) }

    /** The photo is compressed first; only a small base64 JPEG reaches the account document. */
    fun savePhoto(uri: Uri, onResult: (Boolean) -> Unit) {
        val uid = FirebaseAuthManager.uid ?: return
        if (isSaving) return
        isSaving = true
        busyAction = "photo"
        viewModelScope.launch {
            try {
                val encoded = AvatarCodec.encode(ServiceLocator.context(), uri)
                ServiceLocator.accounts.saveAvatar(uid, encoded)
                onResult(true)
            } catch (_: Exception) {
                failure = AuthReason.GENERIC
                onResult(false)
            } finally {
                isSaving = false
                busyAction = null
            }
        }
    }

    fun linkGoogle(idToken: String, onResult: (Boolean) -> Unit) = runAction("link", onResult) {
        FirebaseAuthManager.linkGoogle(idToken)
        ServiceLocator.account.refresh()
    }

    fun unlinkGoogle(onResult: (Boolean) -> Unit) = runAction("unlink", onResult) {
        FirebaseAuthManager.unlinkGoogle()
        ServiceLocator.account.refresh()
    }

    fun setPassword(password: String, onResult: (Boolean) -> Unit) = runAction("password", onResult) {
        FirebaseAuthManager.setPassword(password)
        ServiceLocator.account.refresh()
    }

    fun changePassword(current: String, newPassword: String, onResult: (Boolean) -> Unit) =
        runAction("password", onResult) {
            FirebaseAuthManager.reauthenticate(current)
            FirebaseAuthManager.changePassword(newPassword)
        }

    fun sendVerification(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { FirebaseAuthManager.sendVerificationEmail() }
                .onSuccess { onResult(true) }
                .onFailure { onResult(false) }
        }
    }

    fun refreshVerification() {
        viewModelScope.launch {
            ServiceLocator.account.refresh()
            notice = if (FirebaseAuthManager.isEmailVerified) R.string.profile_verified_email else null
        }
    }

    fun signOut() {
        viewModelScope.launch { FirebaseAuthManager.signOut() }
    }

    /**
     * Deletion is three separate proofs: the typed username, the password, and Firebase's own
     * re-authentication. Only after all three does anything get removed, and the success notice
     * appears only when the account call itself succeeded.
     */
    fun deleteAccount(
        typedUsername: String,
        password: String,
        confirmation: String,
        onDeleted: () -> Unit,
    ) {
        val account = state.value.account
        val uid = FirebaseAuthManager.uid ?: return
        if (typedUsername.trim().lowercase() != account?.username?.lowercase()) {
            failure = AuthReason.GENERIC
            notice = R.string.delete_username_mismatch
            return
        }
        if (password.isBlank()) {
            notice = R.string.delete_password_required
            return
        }
        if (password != confirmation) {
            notice = R.string.auth_error_password_mismatch
            return
        }
        if (isSaving) return
        isSaving = true
        busyAction = "delete"
        viewModelScope.launch {
            try {
                FirebaseAuthManager.reauthenticate(password)
                ServiceLocator.accounts.deleteAccountData(uid, account?.username)
                FirebaseAuthManager.deleteAccount()
                onDeleted()
            } catch (error: AuthFailure) {
                failure = error.reason
            } catch (_: Exception) {
                failure = AuthReason.NETWORK
            } finally {
                isSaving = false
                busyAction = null
            }
        }
    }

    private fun mutate(onResult: (Boolean) -> Unit, fields: (String) -> Map<String, Any?>) {
        val uid = FirebaseAuthManager.uid ?: return
        if (isSaving) return
        isSaving = true
        viewModelScope.launch {
            try {
                ServiceLocator.accounts.updateAccount(uid, fields(uid))
                onResult(true)
            } catch (_: Exception) {
                failure = AuthReason.NETWORK
                onResult(false)
            } finally {
                isSaving = false
            }
        }
    }

    private fun runAction(key: String, onResult: (Boolean) -> Unit, block: suspend () -> Unit) {
        if (isSaving) return
        isSaving = true
        busyAction = key
        failure = null
        viewModelScope.launch {
            try {
                block()
                onResult(true)
            } catch (error: AuthFailure) {
                failure = error.reason
                onResult(false)
            } catch (_: Exception) {
                failure = AuthReason.GENERIC
                onResult(false)
            } finally {
                isSaving = false
                busyAction = null
            }
        }
    }
}
