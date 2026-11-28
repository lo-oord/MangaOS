package com.mangaos.app.data.sources

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mangaos.app.domain.model.SourceInfo
import com.mangaos.app.domain.repository.SourceRepository
import com.mangaos.app.sources.core.MangaSource
import com.mangaos.app.sources.registry.NativeSourceRegistry
import com.mangaos.app.sources.registry.SourceDefinition
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the bundled sources: which ones are enabled, their lazily created instances and their
 * last failure. Nothing here touches the network, so a source never runs just because the app
 * started or because the Sources screen was opened.
 *
 * Enabled state is persisted in DataStore and survives restarts. Disabling a source never
 * deletes the user's library, reading history or downloads; it only stops new requests.
 *
 * Exactly one server is active at a time: the primary server is the default, the backup starts
 * off, and turning either one on turns the other off, so requests never split between the two.
 */

class NativeSourceManager(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : SourceRepository {

    private val definitions = NativeSourceRegistry.definitions
    private val instances = ConcurrentHashMap<String, MangaSource>()

    /** The server the app opens on and the one a disabled pair falls back to. */
    private val defaultId = NativeSourceRegistry.DEFAULT_ID
    private val defaultEnabledIds = setOf(defaultId)

    private val _enabledIds = MutableStateFlow(defaultEnabledIds)
    private val _failures = MutableStateFlow<Map<String, String>>(emptyMap())

    override val sources: StateFlow<List<SourceInfo>> = _enabledIds
        .map { enabled ->
            definitions.map { definition -> definition.toInfo(enabled.contains(definition.id)) }
        }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            definitions.map { it.toInfo(it.id in defaultEnabledIds) },
        )

    /**
     * The primary server is the default source. Ordering the enabled list with it first makes
     * "the first enabled source" mean the primary one everywhere, instead of whatever name
     * happened to sort first alphabetically.
     */
    override val defaultSourceId: String? = defaultId

    override val enabledSources: StateFlow<List<SourceInfo>> = sources
        .map { list ->
            list.filter { it.enabled }
                .sortedWith(compareBy({ it.id != defaultSourceId }, { it.name.lowercase() }))
        }
        .stateIn(scope, SharingStarted.Eagerly, sources.value.filter { it.enabled })

    override val failures: StateFlow<Map<String, String>> = _failures.asStateFlow()

    init {
        scope.launch { restoreEnabledState() }
    }

    private suspend fun restoreEnabledState() {
        val stored = withContext(Dispatchers.IO) {
            runCatching { dataStore.data.first()[ENABLED_KEY] }.getOrNull()
        }
        val enabled = if (stored == null) defaultEnabledIds else normalize(stored)
        _enabledIds.value = enabled
        // Rewritten even when nothing changed, so a store written by an older build (which kept
        // both servers on) is left in the one-at-a-time shape from the next launch onwards.
        persist(enabled)
    }

    /**
     * One server at a time. A stored set that holds both (or two ids of an older build) keeps
     * the first definition, which is the primary server; an explicitly empty set stays empty.
     */
    private fun normalize(stored: Set<String>): Set<String> {
        val known = stored.filter { id -> definitions.any { it.id == id } }
        return when {
            known.isNotEmpty() -> setOf(definitions.first { it.id in known }.id)
            stored.isEmpty() -> emptySet()
            else -> defaultEnabledIds
        }
    }

    override fun setEnabled(sourceId: String, enabled: Boolean) {
        if (definitions.none { it.id == sourceId }) return
        // Enabling a server replaces the active one instead of adding to it, so the primary and
        // the backup can never both answer at the same time.
        val next = if (enabled) setOf(sourceId) else _enabledIds.value - sourceId
        if (next == _enabledIds.value) return
        _enabledIds.value = next
        // Failures of a server that is no longer active are irrelevant and would keep a stale
        // error chip on its row.
        _failures.value = _failures.value.filterKeys { it in next }
        scope.launch { persist(next) }
    }

    override fun isEnabled(sourceId: String): Boolean = sourceId in _enabledIds.value

    /** Creates the implementation on first use, then reuses it for this process. */
    override fun source(sourceId: String): MangaSource? {
        val definition = definitions.firstOrNull { it.id == sourceId } ?: return null
        return instances.computeIfAbsent(sourceId) { definition.factory() }
    }

    override fun reportFailure(sourceId: String, message: String?) {
        _failures.value = if (message == null) {
            _failures.value - sourceId
        } else {
            _failures.value + (sourceId to message)
        }
    }

    private suspend fun persist(ids: Set<String>) {
        withContext(Dispatchers.IO) {
            runCatching {
                dataStore.edit { preferences -> preferences[ENABLED_KEY] = ids }
            }
        }
    }

    private fun SourceDefinition.toInfo(enabled: Boolean): SourceInfo = SourceInfo(
        id = id,
        name = name,
        lang = lang,
        baseUrl = baseUrl,
        description = description,
        version = version,
        capabilities = capabilities,
        enabled = enabled,
        iconRes = iconRes,
    )

    private companion object {
        val ENABLED_KEY = stringSetPreferencesKey("native_sources_enabled")
    }
}
