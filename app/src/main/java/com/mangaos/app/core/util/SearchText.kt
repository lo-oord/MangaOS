package com.mangaos.app.core.util

/**
 * Search normalization shared by every field in the app. Arabic diacritics and tatweel are
 * ignored so a query matches whether or not the user types them, and the same comparison is
 * used for Latin and Japanese titles.
 */
object SearchText {

    private val arabicDiacritics = Regex("[\u0610-\u061A\u064B-\u065F\u0670\u06D6-\u06ED]")

    fun normalize(value: String): String = value
        .lowercase()
        .replace(arabicDiacritics, "")
        .replace("\u0640", "")
        .trim()

    fun matches(haystack: String?, query: String): Boolean {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return true
        return normalize(haystack.orEmpty()).contains(normalizedQuery)
    }
}
