package com.mangaos.app.data.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Baseline for the new-chapter check: which chapter ids were already known for every work and
 * the highest chapter number seen. The first sync of a work only writes this baseline, which
 * is what keeps a fresh install from announcing the whole back catalogue.
 */
class ChapterWatchStore(context: Context) {

    data class Watch(
        val knownIds: Set<String>,
        val lastNumber: Float?,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun watch(mangaKey: String): Watch? {
        val raw = prefs.getString(key(mangaKey), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val array = json.optJSONArray("ids") ?: JSONArray()
            val ids = (0 until array.length())
                .mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
                .toSet()
            Watch(
                knownIds = ids,
                lastNumber = if (json.has("lastNumber")) json.optDouble("lastNumber").toFloat() else null,
            )
        }.getOrNull()
    }

    fun save(mangaKey: String, watch: Watch) {
        val payload = JSONObject().apply {
            put("ids", JSONArray(watch.knownIds.toList()))
            watch.lastNumber?.let { put("lastNumber", it.toDouble()) }
        }
        prefs.edit().putString(key(mangaKey), payload.toString()).apply()
    }

    fun remove(mangaKey: String) {
        prefs.edit().remove(key(mangaKey)).apply()
    }

    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_CHECK, value).apply()
        }

    var lastCheckResult: String?
        get() = prefs.getString(KEY_LAST_RESULT, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_RESULT, value).apply()
        }

    private fun key(mangaKey: String): String = "watch_$mangaKey"

    private companion object {
        const val PREFS = "mangaos_chapter_watch"
        const val KEY_LAST_CHECK = "last_check_at"
        const val KEY_LAST_RESULT = "last_check_result"
    }
}
