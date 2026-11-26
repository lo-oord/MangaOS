package com.mangaos.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        val session = stringPreferencesKey("user_session")
        val country = stringPreferencesKey("user_country")
        val age = intPreferencesKey("user_age")
        val preferredLanguage = stringPreferencesKey("user_preferred_language")
    }

    override val profile: StateFlow<UserProfile?> = dataStore.data
        .map { preferences ->
            val id = preferences[Keys.id] ?: return@map null
            UserProfile(
                id = id,
                username = preferences[Keys.username] ?: "reader",
                displayName = preferences[Keys.displayName] ?: "Reader",
                avatarUri = preferences[Keys.avatar],
                sessionType = if (preferences[Keys.session] == "guest") SessionType.GUEST else SessionType.ACCOUNT,
                countryCode = preferences[Keys.country],
                age = preferences[Keys.age],
                preferredLanguage = preferences[Keys.preferredLanguage],
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
                if (avatarUri != null) preferences[Keys.avatar] = avatarUri else preferences.remove(Keys.avatar)
                if (countryCode != null) preferences[Keys.country] = countryCode else preferences.remove(Keys.country)
                if (age != null) preferences[Keys.age] = age else preferences.remove(Keys.age)
                if (preferredLanguage != null) {
                    preferences[Keys.preferredLanguage] = preferredLanguage
                } else {
                    preferences.remove(Keys.preferredLanguage)
                }
            }
        }
    }

    override fun signOut() {
        scope.launch {
            dataStore.edit { preferences ->
                preferences.remove(Keys.id)
                preferences.remove(Keys.username)
                preferences.remove(Keys.displayName)
                preferences.remove(Keys.avatar)
                preferences.remove(Keys.session)
                preferences.remove(Keys.country)
                preferences.remove(Keys.age)
                preferences.remove(Keys.preferredLanguage)
            }
        }
    }
}
