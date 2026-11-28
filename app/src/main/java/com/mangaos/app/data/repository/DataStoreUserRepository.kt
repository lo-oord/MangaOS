package com.mangaos.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mangaos.app.domain.model.SessionType
import com.mangaos.app.domain.model.UserProfile
import com.mangaos.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Local session storage. There is no authentication yet: this only remembers which local
 * profile is active so the welcome flow is not repeated on every launch.
 */
class DataStoreUserRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : UserRepository {

    private object Keys {
        val id = stringPreferencesKey("user_id")
        val username = stringPreferencesKey("user_username")
        val displayName = stringPreferencesKey("user_display_name")
        val avatar = stringPreferencesKey("user_avatar")
        val email = stringPreferencesKey("user_email")
        val session = stringPreferencesKey("user_session")
        val country = stringPreferencesKey("user_country")
        val age = intPreferencesKey("user_age")
        val preferredLanguage = stringPreferencesKey("user_preferred_language")
        val verified = booleanPreferencesKey("user_verified")
    }

    override val profile: StateFlow<UserProfile?> = dataStore.data
        .map { preferences ->
            val id = preferences[Keys.id] ?: return@map null
            UserProfile(
                id = id,
                username = preferences[Keys.username] ?: "reader",
                displayName = preferences[Keys.displayName] ?: "Reader",
                avatarUri = preferences[Keys.avatar],
                email = preferences[Keys.email],
                sessionType = if (preferences[Keys.session] == "guest") SessionType.GUEST else SessionType.ACCOUNT,
                countryCode = preferences[Keys.country],
                age = preferences[Keys.age],
                preferredLanguage = preferences[Keys.preferredLanguage],
                verified = preferences[Keys.verified] ?: false,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val isGuest: Flow<Boolean> = profile.map { it?.sessionType == SessionType.GUEST }

    override fun startGuestSession() {
        if (profile.value != null) return
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.id] = "guest"
                preferences[Keys.username] = "guest"
                preferences[Keys.displayName] = "Guest"
                preferences[Keys.session] = "guest"
            }
        }
    }

    override fun createAccount(
        displayName: String,
        username: String,
        avatarUri: String?,
        email: String?,
        countryCode: String?,
        age: Int?,
        preferredLanguage: String?,
    ) {
        scope.launch {
            dataStore.edit { preferences ->
                preferences[Keys.id] = UUID.randomUUID().toString()
                preferences[Keys.username] = username
                preferences[Keys.displayName] = displayName
                preferences[Keys.session] = "account"
                writeProfile(preferences, avatarUri, email, countryCode, age, preferredLanguage)
            }
        }
    }

    override suspend fun updateProfile(
        displayName: String,
        username: String,
        avatarUri: String?,
        email: String?,
        countryCode: String?,
        age: Int?,
        preferredLanguage: String?,
    ) {
        dataStore.edit { preferences ->
            if (preferences[Keys.id] == null) return@edit
            preferences[Keys.username] = username
            preferences[Keys.displayName] = displayName
            writeProfile(preferences, avatarUri, email, countryCode, age, preferredLanguage)
        }
    }

    private fun writeProfile(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        avatarUri: String?,
        email: String?,
        countryCode: String?,
        age: Int?,
        preferredLanguage: String?,
    ) {
        if (avatarUri != null) preferences[Keys.avatar] = avatarUri else preferences.remove(Keys.avatar)
        if (email != null) preferences[Keys.email] = email else preferences.remove(Keys.email)
        if (countryCode != null) preferences[Keys.country] = countryCode else preferences.remove(Keys.country)
        if (age != null) preferences[Keys.age] = age else preferences.remove(Keys.age)
        if (preferredLanguage != null) {
            preferences[Keys.preferredLanguage] = preferredLanguage
        } else {
            preferences.remove(Keys.preferredLanguage)
        }
    }

    /**
     * Writes the account identity in one edit. The UID becomes the local id, so a library entry
     * saved on this device stays attached to the same account after signing in again.
     */
    override suspend fun adoptAccount(profile: UserProfile) {
        dataStore.edit { preferences ->
            preferences[Keys.id] = profile.id
            preferences[Keys.username] = profile.username
            preferences[Keys.displayName] = profile.displayName
            preferences[Keys.session] = if (profile.sessionType == SessionType.GUEST) "guest" else "account"
            preferences[Keys.verified] = profile.verified
            writeProfile(
                preferences,
                profile.avatarUri,
                profile.email,
                profile.countryCode,
                profile.age,
                profile.preferredLanguage,
            )
        }
    }

    override fun signOut() {
        scope.launch {
            dataStore.edit { preferences ->
                preferences.remove(Keys.id)
                preferences.remove(Keys.username)
                preferences.remove(Keys.displayName)
                preferences.remove(Keys.avatar)
                preferences.remove(Keys.email)
                preferences.remove(Keys.session)
                preferences.remove(Keys.country)
                preferences.remove(Keys.age)
                preferences.remove(Keys.preferredLanguage)
                preferences.remove(Keys.verified)
            }
        }
    }
}
