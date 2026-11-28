package com.mangaos.app.presentation.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.OnboardingState
import com.mangaos.app.domain.model.StorageChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The onboarding wizard's state.
 *
 * Every choice is written to storage as it happens (language and theme through their own
 * settings so the whole app follows them immediately, the rest through the wizard record), so
 * leaving the app half way through resumes on the same step with the same answers.
 */
class OnboardingViewModel : ViewModel() {

    private val settings = ServiceLocator.settings
    private val context = ServiceLocator.context()

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _direction = MutableStateFlow(1)
    /** 1 forward, -1 backward; drives the content transition. */
    val direction: StateFlow<Int> = _direction.asStateFlow()

    private val _storageError = MutableStateFlow(false)
    val storageError: StateFlow<Boolean> = _storageError.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = settings.onboardingState.first()
            _ready.value = true
        }
    }

    fun selectLanguage(code: String) {
        _state.update { it.copy(language = code) }
        viewModelScope.launch {
            settings.setLanguage(code)
            settings.updateOnboarding { it.copy(language = code) }
        }
    }

    fun selectTheme(theme: AppTheme) {
        _state.update { it.copy(theme = theme) }
        viewModelScope.launch {
            settings.setTheme(theme)
            settings.updateOnboarding { it.copy(theme = theme) }
        }
    }

    fun chooseDefaultStorage() {
        _storageError.value = false
        _state.update {
            it.copy(storageChoice = StorageChoice.DEFAULT, folderUri = null, folderName = null)
        }
        persist()
    }

    /** Takes a persistable grant on the picked folder and proves it can actually be written. */
    fun chooseCustomFolder(uri: Uri) {
        val resolver = context.contentResolver
        val granted = runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        val writable = runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
        }.getOrDefault(false)
        if (!granted || !writable) {
            _storageError.value = true
            return
        }
        _storageError.value = false
        val name = folderName(uri)
        _state.update {
            it.copy(
                storageChoice = StorageChoice.CUSTOM,
                folderUri = uri.toString(),
                folderName = name,
            )
        }
        persist()
    }

    fun toggleGenre(genre: String) {
        _state.update { current ->
            val genres = if (genre in current.genres) {
                current.genres - genre
            } else {
                current.genres + genre
            }
            current.copy(genres = genres)
        }
        persist()
    }

    fun toggleContentType(type: String) {
        _state.update { current ->
            val types = if (type in current.contentTypes) {
                current.contentTypes - type
            } else {
                current.contentTypes + type
            }
            current.copy(contentTypes = types)
        }
        persist()
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled) }
        viewModelScope.launch { settings.updateOnboarding { it.copy(notificationsEnabled = enabled) } }
    }

    fun next() {
        val current = _state.value
        if (current.step >= STEP_COUNT - 1) return
        val target = current.step + 1
        _direction.value = 1
        _state.update { it.copy(step = target) }
        // One write per step: a later launch that was interrupted resumes on the saved step.
        viewModelScope.launch { settings.updateOnboarding { it.copy(step = target) } }
    }

    fun back(): Boolean {
        val current = _state.value
        if (current.step == 0) return false
        val target = current.step - 1
        _direction.value = -1
        _state.update { it.copy(step = target) }
        viewModelScope.launch { settings.updateOnboarding { it.copy(step = target) } }
        return true
    }

    /** The wizard is complete whether or not the optional steps were filled in. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.updateOnboarding { it.copy(step = STEP_COUNT) }
            settings.setOnboardingCompleted(true)
            onDone()
        }
    }

    private fun persist() {
        val snapshot = _state.value
        viewModelScope.launch {
            settings.updateOnboarding { current ->
                current.copy(
                    storageChoice = snapshot.storageChoice,
                    folderUri = snapshot.folderUri,
                    folderName = snapshot.folderName,
                    genres = snapshot.genres,
                    contentTypes = snapshot.contentTypes,
                )
            }
        }
    }

    private fun folderName(uri: Uri): String {
        val fromDocument = runCatching {
            DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')
        }.getOrNull().orEmpty()
        return fromDocument.ifBlank {
            uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
        }.ifBlank { "MangaOS" }
    }

    companion object {
        const val STEP_COUNT = 6
        const val STEP_LANGUAGE = 0
        const val STEP_THEME = 1
        const val STEP_STORAGE = 2
        const val STEP_GENRES = 3
        const val STEP_TYPES = 4
        const val STEP_NOTIFICATIONS = 5
    }
}
