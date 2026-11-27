package com.mangaos.app.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources

/**
 * A context whose resources follow the app language chosen in Settings. Compose resolves
 * `stringResource` through `LocalContext`, so switching this context inside the composition
 * re-renders every label instantly — no Activity restart, no lost scroll position.
 *
 * Everything that needs the Activity itself (window flags, orientation, insets) unwraps with
 * [findActivity] instead of casting the context, because this wrapper is not an Activity.
 */
class LocalizedContext(base: Context, private val localized: Context) : ContextWrapper(base) {
    override fun getResources(): Resources = localized.resources

    companion object {
        fun of(base: Context, language: String): LocalizedContext {
            val locale = LocaleManager.displayLocale(language)
            val configuration = Configuration(base.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return LocalizedContext(base, base.createConfigurationContext(configuration))
        }
    }
}

/** The hosting Activity behind any chain of ContextWrappers, when there is one. */
fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
