package com.mangaos.app.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App-level language selection. [applyTo] runs in Activity#attachBaseContext, so the
 * whole process (resources, layout direction, dialogs) follows the chosen language.
 *
 * The value is mirrored into a tiny SharedPreferences file because it has to be read
 * synchronously before the DataStore instance is available.
 */
object LocaleManager {

    const val ARABIC = "ar"
    const val ENGLISH = "en"
    const val JAPANESE = "ja"

    val supportedLanguages = listOf(ARABIC, ENGLISH, JAPANESE)

    /** MangaOS starts in Arabic, whatever the device language is. */
    const val DEFAULT_LANGUAGE = ARABIC

    private const val PREFS_NAME = "mangaos_locale"
    private const val KEY_LANGUAGE = "language"

    fun isSupported(language: String?): Boolean = language in supportedLanguages

    fun deviceLanguage(): String = when (Locale.getDefault().language) {
        ARABIC -> ARABIC
        JAPANESE -> JAPANESE
        else -> ENGLISH
    }

    fun readLanguage(context: Context): String {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null)
        return if (isSupported(stored)) stored!! else DEFAULT_LANGUAGE
    }

    fun cacheLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun applyTo(context: Context): Context {
        val language = readLanguage(context)
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}
