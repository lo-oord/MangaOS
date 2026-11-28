package com.mangaos.app.data.firebase

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Mirrors the device's stored state to `users/{uid}/state/prefs` and applies remote edits back.
 *
 * Every synced key carries its own timestamp, so two devices editing different preferences both
 * keep their change; only the same key is resolved last-writer-wins. A remote write is never
 * echoed back: the collector is paused while a remote snapshot is being applied, and the local
 * baseline is refreshed afterwards.
 */
class CloudSync(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clientId: String,
) {

    enum class Status { IDLE, SYNCING, SYNCED, OFFLINE }

    private val db = FirebaseFirestore.getInstance()
    private var uid: String? = null
    private var registration: ListenerRegistration? = null
    private var collector: Job? = null
    private var uploader: Job? = null
    private var applyingRemote = false
    private var remoteReady = false
    private var baseline: Map<String, Any?> = emptyMap()
    private val stamps = mutableMapOf<String, Long>()

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun start(uid: String) {
        stop()
        this.uid = uid
        _status.value = Status.SYNCING
        registration = document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                _status.value = Status.OFFLINE
                return@addSnapshotListener
            }
            val fields = snapshot?.get(FIELD_FIELDS) as? Map<*, *> ?: emptyMap<Any, Any>()
            val remoteStamps = snapshot?.get(FIELD_STAMPS) as? Map<*, *> ?: emptyMap<Any, Any>()
            scope.launch {
                if (fields.isEmpty() && !remoteReady) {
                    // Nothing stored yet (a brand new account): this device's state becomes the
                    // starting point instead of waiting for the reader to change something.
                    remoteReady = true
                    val local = syncedMap()
                    baseline = local
                    upload(local)
                    return@launch
                }
                applyRemote(fields, remoteStamps)
                remoteReady = true
            }
        }
        collector = scope.launch {
            dataStore.data
                .map { preferences -> syncedValues(preferences) }
                .distinctUntilChanged()
                .collect { remote ->
                    if (applyingRemote || !remoteReady) return@collect
                    val changed = remote.filter { (key, value) -> baseline[key] != value }
                    baseline = remote
                    if (changed.isEmpty()) return@collect
                    uploader?.cancel()
                    uploader = launch {
                        delay(DEBOUNCE_MILLIS)
                        upload(changed)
                    }
                }
        }
    }

    fun stop() {
        registration?.remove()
        registration = null
        collector?.cancel()
        collector = null
        uploader?.cancel()
        uploader = null
        uid = null
        baseline = emptyMap()
        // Per-account state must not survive the account: a stamp left over from the previous
        // reader would make this account's first snapshot look already applied.
        stamps.clear()
        applyingRemote = false
        remoteReady = false
        if (_status.value != Status.IDLE) _status.value = Status.IDLE
    }

    private suspend fun upload(changed: Map<String, Any?>) {
        val current = uid ?: return
        val now = System.currentTimeMillis()
        _status.value = Status.SYNCING
        try {
            document(current).set(
                mapOf(
                    FIELD_FIELDS to changed,
                    FIELD_STAMPS to changed.keys.associateWith { now },
                    "clientId" to clientId,
                    "updatedAt" to now,
                ),
                SetOptions.merge(),
            ).awaitUnit()
            changed.keys.forEach { key -> stamps[key] = now }
            _status.value = Status.SYNCED
        } catch (error: Exception) {
            // A denied write (rules) and a lost connection look the same to the reader, so the
            // reason is logged instead of being dropped.
            Log.w(TAG, "Preference upload failed for ${current.take(6)}…", error)
            _status.value = Status.OFFLINE
        }
    }

    private suspend fun applyRemote(fields: Map<*, *>, remoteStamps: Map<*, *>) {
        if (fields.isEmpty()) return
        applyingRemote = true
        try {
            val newer = fields.entries.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                val remoteStamp = (remoteStamps[entry.key] as? Number)?.toLong() ?: 0L
                if (remoteStamp <= (stamps[key] ?: 0L)) null else key to entry.value
            }
            if (newer.isEmpty()) return
            dataStore.edit { preferences ->
                newer.forEach { (key, value) ->
                    applyValue(preferences, key, value)
                    stamps[key] = (remoteStamps[key] as? Number)?.toLong() ?: 0L
                }
            }
            baseline = syncedMap()
            _status.value = Status.SYNCED
        } finally {
            applyingRemote = false
        }
    }

    private fun document(uid: String) = db.collection("users").document(uid)
        .collection("state").document("prefs")

    /** Account identity stays local; everything the reader changes is mirrored. */
    private fun isSynced(key: String): Boolean =
        !key.startsWith("user_") &&
            !key.startsWith("cloud_sync") &&
            !key.startsWith("local_auth") &&
            !key.startsWith("install_")

    private suspend fun syncedMap(): Map<String, Any?> = syncedValues(dataStore.data.first())

    /** Preference keys are typed; the cloud document needs their names and plain values. */
    private fun syncedValues(preferences: Preferences): Map<String, Any?> =
        preferences.asMap().entries
            .filter { entry -> isSynced(entry.key.name) }
            .associate { entry -> entry.key.name to toRemote(entry.value) }

    private fun toRemote(value: Any?): Any? = when (value) {
        is Set<*> -> value.filterIsInstance<String>().sorted()
        is Float -> value.toDouble()
        else -> value
    }

    private fun applyValue(preferences: MutablePreferences, key: String, value: Any?) {
        when (value) {
            is Boolean -> preferences[booleanPreferencesKey(key)] = value
            is Int -> preferences[intPreferencesKey(key)] = value
            is Long -> preferences[longPreferencesKey(key)] = value
            is Double -> preferences[doublePreferencesKey(key)] = value
            is Float -> preferences[doublePreferencesKey(key)] = value.toDouble()
            is String -> preferences[stringPreferencesKey(key)] = value
            is List<*> -> preferences[stringSetPreferencesKey(key)] =
                value.filterIsInstance<String>().toSet()
            null -> Unit
        }
    }

    private companion object {
        const val TAG = "CloudSync"
        const val FIELD_FIELDS = "fields"
        const val FIELD_STAMPS = "stamps"
        const val DEBOUNCE_MILLIS = 700L
    }
}
