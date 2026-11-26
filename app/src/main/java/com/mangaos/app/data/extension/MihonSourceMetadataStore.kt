package com.mangaos.app.data.extension

import android.content.Context
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Mihon extensions keep a JSON `memo` on manga and chapter objects. Modern Keiyoushi sources
 * put the site's own id there and require it back in `getMangaUpdate` / `getPageList`; the
 * MangaOS domain model only carries the source URL. Persist the memo out of band, exactly as
 * DropSauce does, so listing -> details -> chapters -> pages keeps the extension's state.
 */
internal class MihonSourceMetadataStore(context: Context) {

    private val preferences = context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
    private val json = Json

    fun restore(sourceId: Long, mangaUrl: String, manga: SManga) {
        val prefix = keyPrefix(sourceId, mangaUrl)
        readMemo(prefix + KEY_MEMO)?.let { manga.memo = it }
        preferences.getString(prefix + KEY_UPDATE_STRATEGY, null)
            ?.let { runCatching { UpdateStrategy.valueOf(it) }.getOrNull() }
            ?.let { manga.update_strategy = it }
        if (preferences.contains(prefix + KEY_INITIALIZED)) {
            manga.initialized = preferences.getBoolean(prefix + KEY_INITIALIZED, manga.initialized)
        }
        // Sources such as TeamX read some metadata again while parsing a details page.
        // Restoring the last listing values prevents an empty placeholder from breaking it.
        if (manga.title.isBlank()) {
            preferences.getString(prefix + KEY_TITLE, null)?.let { manga.title = it }
        }
        if (manga.thumbnail_url.isNullOrBlank()) {
            preferences.getString(prefix + KEY_THUMBNAIL, null)?.let { manga.thumbnail_url = it }
        }
        if (manga.description.isNullOrBlank()) {
            preferences.getString(prefix + KEY_DESCRIPTION, null)?.let { manga.description = it }
        }
        if (manga.author.isNullOrBlank()) {
            preferences.getString(prefix + KEY_AUTHOR, null)?.let { manga.author = it }
        }
        if (manga.artist.isNullOrBlank()) {
            preferences.getString(prefix + KEY_ARTIST, null)?.let { manga.artist = it }
        }
        if (manga.genre.isNullOrBlank()) {
            preferences.getString(prefix + KEY_GENRE, null)?.let { manga.genre = it }
        }
    }

    /** Persist listing metadata before the details request needs it. */
    fun saveMemos(sourceId: Long, mangas: List<SManga>) {
        val editor = preferences.edit()
        var changed = false
        mangas.forEach { manga ->
            val prefix = keyPrefix(sourceId, manga.url)
            fun put(key: String, value: String?) {
                if (value.isNullOrBlank()) return
                if (preferences.getString(prefix + key, null) == value) return
                editor.putString(prefix + key, value)
                changed = true
            }
            if (manga.memo.isNotEmpty()) put(KEY_MEMO, manga.memo.toString())
            put(KEY_TITLE, manga.title)
            put(KEY_THUMBNAIL, manga.thumbnail_url)
            put(KEY_DESCRIPTION, manga.description)
            put(KEY_AUTHOR, manga.author)
            put(KEY_ARTIST, manga.artist)
            put(KEY_GENRE, manga.genre)
        }
        if (changed) editor.apply()
    }

    fun save(sourceId: Long, mangaUrl: String, manga: SManga) {
        val prefix = keyPrefix(sourceId, mangaUrl)
        val editor = preferences.edit()
        editor.putString(prefix + KEY_MEMO, manga.memo.toString())
        editor.putString(prefix + KEY_UPDATE_STRATEGY, manga.update_strategy.name)
        editor.putBoolean(prefix + KEY_INITIALIZED, manga.initialized)
        manga.title.takeIf { it.isNotBlank() }?.let { editor.putString(prefix + KEY_TITLE, it) }
        manga.thumbnail_url?.takeIf { it.isNotBlank() }?.let { editor.putString(prefix + KEY_THUMBNAIL, it) }
        manga.description?.takeIf { it.isNotBlank() }?.let { editor.putString(prefix + KEY_DESCRIPTION, it) }
        manga.author?.takeIf { it.isNotBlank() }?.let { editor.putString(prefix + KEY_AUTHOR, it) }
        manga.artist?.takeIf { it.isNotBlank() }?.let { editor.putString(prefix + KEY_ARTIST, it) }
        manga.genre?.takeIf { it.isNotBlank() }?.let { editor.putString(prefix + KEY_GENRE, it) }
        editor.apply()
    }

    fun restoreChapterMemo(sourceId: Long, mangaUrl: String, chapterUrl: String): JsonObject? =
        readMemo(chapterMemoKey(sourceId, mangaUrl, chapterUrl))

    fun saveChapterMemos(sourceId: Long, mangaUrl: String, memos: Map<String, JsonObject>) {
        val changed = memos.mapNotNull { (chapterUrl, memo) ->
            val key = chapterMemoKey(sourceId, mangaUrl, chapterUrl)
            val value = memo.toString()
            if (preferences.getString(key, null) == value) null else key to value
        }
        if (changed.isEmpty()) return
        preferences.edit().apply {
            for ((key, value) in changed) putString(key, value)
        }.apply()
    }

    fun restoreChapter(sourceId: Long, mangaUrl: String, chapter: SChapter) {
        restoreChapterMemo(sourceId, mangaUrl, chapter.url)?.let { chapter.memo = it }
        val prefix = chapterPrefix(sourceId, mangaUrl, chapter.url)
        preferences.getString(prefix + KEY_NAME, null)?.let { chapter.name = it }
        if (preferences.contains(prefix + KEY_NUMBER)) {
            chapter.chapter_number = preferences.getFloat(prefix + KEY_NUMBER, chapter.chapter_number)
        }
        preferences.getString(prefix + KEY_SCANLATOR, null)?.let { chapter.scanlator = it }
    }

    /** Page lists in some sources read the chapter name/number, not only its URL. */
    fun saveChapterMeta(sourceId: Long, mangaUrl: String, chapters: List<SChapter>) {
        val editor = preferences.edit()
        var changed = false
        chapters.forEach { chapter ->
            val prefix = chapterPrefix(sourceId, mangaUrl, chapter.url)
            fun put(key: String, value: String?) {
                if (value.isNullOrBlank()) return
                if (preferences.getString(prefix + key, null) == value) return
                editor.putString(prefix + key, value)
                changed = true
            }
            put(KEY_NAME, chapter.name)
            put(KEY_SCANLATOR, chapter.scanlator)
            if (chapter.chapter_number >= 0f) {
                editor.putFloat(prefix + KEY_NUMBER, chapter.chapter_number)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private fun readMemo(key: String): JsonObject? =
        preferences.getString(key, null)?.let { encoded ->
            runCatching { json.parseToJsonElement(encoded).jsonObject }.getOrNull()
        }

    private fun keyPrefix(sourceId: Long, mangaUrl: String): String =
        sha256("$sourceId\n$mangaUrl") + "."

    private fun chapterPrefix(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
        keyPrefix(sourceId, "$mangaUrl\n$chapterUrl")

    private fun chapterMemoKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
        chapterPrefix(sourceId, mangaUrl, chapterUrl) + KEY_MEMO

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val STORAGE_NAME = "mihon_source_metadata"
        const val KEY_MEMO = "memo"
        const val KEY_UPDATE_STRATEGY = "update_strategy"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_TITLE = "title"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_DESCRIPTION = "description"
        const val KEY_AUTHOR = "author"
        const val KEY_ARTIST = "artist"
        const val KEY_GENRE = "genre"
        const val KEY_NAME = "chapter_name"
        const val KEY_NUMBER = "chapter_number"
        const val KEY_SCANLATOR = "chapter_scanlator"
    }
}
