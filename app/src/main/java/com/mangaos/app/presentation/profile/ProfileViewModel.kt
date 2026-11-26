package com.mangaos.app.presentation.profile

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.R
import com.mangaos.app.core.util.Validators
import com.mangaos.app.data.local.AvatarStore
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.UserProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One source of truth for profile edits. Every change is written through
 * [com.mangaos.app.domain.repository.UserRepository], so the drawer and profile screen
 * update together. Nothing here talks to a server.
 */
class ProfileViewModel : ViewModel() {

    val profile: StateFlow<UserProfile?> = ServiceLocator.user.profile

    var isSaving by mutableStateOf(false)
        private set
    var isSavingPhoto by mutableStateOf(false)
        private set
    var editError by mutableStateOf<Int?>(null)
        private set

    fun clearEditError() {
        editError = null
    }

    fun saveDisplayName(value: String, onResult: (Boolean) -> Unit) {
        val name = value.trim()
        editError = when {
            name.isEmpty() -> R.string.error_required
            name.length > 40 -> R.string.error_display_name_length
            else -> null
        }
        if (editError != null) {
            onResult(false)
            return
        }
        mutate(onResult) { it.copy(displayName = name) }
    }

    fun saveUsername(value: String, onResult: (Boolean) -> Unit) {
        val username = value.trim().removePrefix("@")
        editError = when (Validators.usernameError(username)) {
            null -> null
            Validators.UsernameError.EMPTY -> R.string.error_required
            Validators.UsernameError.LENGTH -> R.string.error_username_length
            Validators.UsernameError.CHARACTERS -> R.string.error_username_chars
        }
        if (editError != null) {
            onResult(false)
            return
        }
        if (username.equals(profile.value?.username, ignoreCase = true)) {
            onResult(true)
            return
        }
        mutate(onResult) { it.copy(username = username) }
    }

    fun savePhoto(uri: Uri, onResult: (Boolean) -> Unit) {
        if (isSavingPhoto) return
        val current = profile.value ?: return
        isSavingPhoto = true
        viewModelScope.launch {
            try {
                val stored = AvatarStore.save(ServiceLocator.context(), uri)
                ServiceLocator.user.updateProfile(
                    displayName = current.displayName,
                    username = current.username,
                    avatarUri = stored,
                    email = current.email,
                    countryCode = current.countryCode,
                    age = current.age,
                    preferredLanguage = current.preferredLanguage,
                )
                AvatarStore.delete(ServiceLocator.context(), current.avatarUri)
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            } finally {
                isSavingPhoto = false
            }
        }
    }

    fun setLanguage(code: String, onResult: (Boolean) -> Unit = {}) {
        mutate(onResult) { it.copy(preferredLanguage = code) }
    }

    fun setCountry(code: String?, onResult: (Boolean) -> Unit = {}) {
        mutate(onResult) { it.copy(countryCode = code) }
    }

    fun setAge(age: Int?, onResult: (Boolean) -> Unit = {}) {
        mutate(onResult) { it.copy(age = age) }
    }

    private fun mutate(
        onResult: (Boolean) -> Unit,
        transform: (UserProfile) -> UserProfile,
    ) {
        val current = profile.value ?: return
        if (isSaving) return
        val next = transform(current)
        isSaving = true
        viewModelScope.launch {
            try {
                ServiceLocator.user.updateProfile(
                    displayName = next.displayName,
                    username = next.username,
                    avatarUri = next.avatarUri,
                    email = next.email,
                    countryCode = next.countryCode,
                    age = next.age,
                    preferredLanguage = next.preferredLanguage,
                )
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            } finally {
                isSaving = false
            }
        }
    }
}
