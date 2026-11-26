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
    }

    /** Persist the listing memos in one write; details fills in the rest later. */
    fun saveMemos(sourceId: Long, mangas: List<SManga>) {
        val changed = mangas.mapNotNull { manga ->
            val memo = manga.memo
            if (memo.isEmpty()) return@mapNotNull null
            val key = keyPrefix(sourceId, manga.url) + KEY_MEMO
            val value = memo.toString()
            if (preferences.getString(key, null) == value) null else key to value
        }
        if (changed.isEmpty()) return
        preferences.edit().apply {
            for ((key, value) in changed) putString(key, value)
        }.apply()
    }

    fun save(sourceId: Long, mangaUrl: String, manga: SManga) {
        val prefix = keyPrefix(sourceId, mangaUrl)
        val memo = manga.memo.toString()
        val strategy = manga.update_strategy.name
        if (preferences.getString(prefix + KEY_MEMO, null) == memo &&
            preferences.getString(prefix + KEY_UPDATE_STRATEGY, null) == strategy &&
            preferences.contains(prefix + KEY_INITIALIZED) &&
            preferences.getBoolean(prefix + KEY_INITIALIZED, false) == manga.initialized
        ) {
            return
        }
        preferences.edit()
            .putString(prefix + KEY_MEMO, memo)
            .putString(prefix + KEY_UPDATE_STRATEGY, strategy)
            .putBoolean(prefix + KEY_INITIALIZED, manga.initialized)
            .apply()
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
    }

    private fun readMemo(key: String): JsonObject? =
        preferences.getString(key, null)?.let { encoded ->
            runCatching { json.parseToJsonElement(encoded).jsonObject }.getOrNull()
        }

    private fun keyPrefix(sourceId: Long, mangaUrl: String): String =
        sha256("$sourceId\n$mangaUrl") + "."

    private fun chapterMemoKey(sourceId: Long, mangaUrl: String, chapterUrl: String): String =
        keyPrefix(sourceId, "$mangaUrl\n$chapterUrl") + KEY_MEMO

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val STORAGE_NAME = "mihon_source_metadata"
        const val KEY_MEMO = "memo"
        const val KEY_UPDATE_STRATEGY = "update_strategy"
        const val KEY_INITIALIZED = "initialized"
    }
}
