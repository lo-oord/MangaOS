package com.mangaos.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mangaos.app.domain.model.Manga
import com.mangaos.app.domain.model.MangaStatus
import com.mangaos.app.domain.model.ReadingProgress
import com.mangaos.app.domain.repository.LibraryRepository
import com.mangaos.app.domain.repository.readChapterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reader's library survives restarts: works and reading progress live in DataStore.
 * The stored copy is the metadata snapshot taken when the work was saved, so the library
 * still opens when its source is unavailable.
 */
class DataStoreLibraryRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : LibraryRepository {

    private object Keys {
        val items = stringPreferencesKey("library_items")
        val progress = stringPreferencesKey("library_progress")
        val readChapters = stringPreferencesKey("library_read_chapters")
    }

    override val works: StateFlow<List<Manga>> = dataStore.data
        .map { decodeMangas(it[Keys.items]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val ids: StateFlow<Set<String>> = works
        .map { list -> list.mapTo(LinkedHashSet()) { it.key } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override val progress: StateFlow<Map<String, ReadingProgress>> = dataStore.data
        .map { decodeProgress(it[Keys.progress]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val readChapters: StateFlow<Set<String>> = dataStore.data
        .map { preferences -> decodeReadChapters(preferences[Keys.readChapters]) }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override suspend fun worksSnapshot(): List<Manga> =
        decodeMangas(dataStore.data.first()[Keys.items])

    override fun toggle(manga: Manga) {
        scope.launch {
            dataStore.edit { preferences ->
                val list = decodeMangas(preferences[Keys.items]).toMutableList()
                val index = list.indexOfFirst { it.key == manga.key }
                if (index >= 0) {
                    list.removeAt(index)
                } else {
                    list.add(0, manga)
                }
                preferences[Keys.items] = encodeMangas(list)
            }
        }
    }

    override fun add(manga: Manga) {
        scope.launch {
            dataStore.edit { preferences ->
                val list = decodeMangas(preferences[Keys.items]).toMutableList()
                list.removeAll { it.key == manga.key }
                list.add(0, manga)
                preferences[Keys.items] = encodeMangas(list)
            }
        }
    }

    override fun remove(mangaId: String) {
        scope.launch {
            dataStore.edit { preferences ->
                val list = decodeMangas(preferences[Keys.items]).filterNot { it.key == mangaId }
                preferences[Keys.items] = encodeMangas(list)
                val progress = decodeProgress(preferences[Keys.progress]).toMutableMap()
                progress.remove(mangaId)
                preferences[Keys.progress] = encodeProgress(progress)
                val read = decodeReadChapters(preferences[Keys.readChapters])
                    .filterNot { it.startsWith("$mangaId\u0001") }
                    .toSet()
                preferences[Keys.readChapters] = encodeReadChapters(read)
            }
        }
    }

    override fun isSaved(mangaKey: String): Boolean = mangaKey in ids.value

    override fun setProgress(mangaKey: String, progress: ReadingProgress) {
        scope.launch {
            dataStore.edit { preferences ->
                val map = decodeProgress(preferences[Keys.progress]).toMutableMap()
                map[mangaKey] = progress
                preferences[Keys.progress] = encodeProgress(map)
            }
        }
    }

    override fun markChapterRead(mangaKey: String, chapterId: String) {
        scope.launch {
            dataStore.edit { preferences ->
                val read = decodeReadChapters(preferences[Keys.readChapters]).toMutableSet()
                if (read.add(readChapterKey(mangaKey, chapterId))) {
                    preferences[Keys.readChapters] = encodeReadChapters(read)
                }
            }
        }
    }

    private fun encodeReadChapters(read: Set<String>): String = JSONArray(read.toList()).toString()

    private fun decodeReadChapters(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun encodeMangas(mangas: List<Manga>): String {
        val array = JSONArray()
        mangas.forEach { manga ->
            array.put(
                JSONObject().apply {
                    put("id", manga.id)
                    put("sourceId", manga.sourceId)
                    put("title", manga.title)
                    manga.altTitle?.let { put("altTitle", it) }
                    manga.description?.let { put("description", it) }
                    if (manga.genres.isNotEmpty()) put("genres", JSONArray(manga.genres))
                    put("status", manga.status.name)
                    manga.rating?.let { put("rating", it) }
                    manga.author?.let { put("author", it) }
                    manga.artist?.let { put("artist", it) }
                    manga.year?.let { put("year", it) }
                    manga.originalLanguage?.let { put("originalLanguage", it) }
                    manga.coverUrl?.let { put("coverUrl", it) }
                    manga.latestChapter?.let { put("latestChapter", it) }
                    manga.latestChapterId?.let { put("latestChapterId", it) }
                    manga.updatedAt?.let { put("updatedAt", it) }
                    manga.url?.let { put("url", it) }
                    manga.sourceName?.let { put("sourceName", it) }
                },
            )
        }
        return array.toString()
    }

    private fun decodeMangas(raw: String?): List<Manga> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val sourceId = json.optString("sourceId")
                Manga(
                    id = id,
                    sourceId = sourceId,
                    title = json.optString("title"),
                    altTitle = json.stringOrNull("altTitle"),
                    description = json.stringOrNull("description"),
                    genres = json.optJSONArray("genres")?.let { genres ->
                        (0 until genres.length()).mapNotNull { genres.optString(it).takeIf(String::isNotBlank) }
                    } ?: emptyList(),
                    status = runCatching { MangaStatus.valueOf(json.optString("status")) }
                        .getOrDefault(MangaStatus.UNKNOWN),
                    rating = json.takeIf { it.has("rating") }?.optDouble("rating"),
                    author = json.stringOrNull("author"),
                    artist = json.stringOrNull("artist"),
                    year = json.takeIf { it.has("year") }?.optInt("year"),
                    originalLanguage = json.stringOrNull("originalLanguage"),
                    coverUrl = json.stringOrNull("coverUrl"),
                    latestChapter = json.stringOrNull("latestChapter"),
                    latestChapterId = json.stringOrNull("latestChapterId"),
                    updatedAt = json.takeIf { it.has("updatedAt") }?.optLong("updatedAt"),
                    url = json.stringOrNull("url"),
                    sourceName = json.stringOrNull("sourceName"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeProgress(progress: Map<String, ReadingProgress>): String {
        val json = JSONObject()
        progress.forEach { (key, value) ->
            json.put(
                key,
                JSONObject().apply {
                    put("chapterId", value.chapterId)
                    put("chapterNumber", value.chapterNumber)
                    put("updatedAt", value.updatedAt)
                },
            )
        }
        return json.toString()
    }

    private fun decodeProgress(raw: String?): Map<String, ReadingProgress> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    val value = json.optJSONObject(key) ?: return@forEach
                    val chapterId = value.optString("chapterId")
                    if (chapterId.isNotEmpty()) {
                        put(
                            key,
                            ReadingProgress(
                                chapterId = chapterId,
                                chapterNumber = value.optString("chapterNumber"),
                                updatedAt = value.optLong("updatedAt"),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
