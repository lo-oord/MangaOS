package com.mangaos.app.core.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Rewrites Arabic-Indic digits as Western ones, leaving every other character as is. Used for
 * display values that arrive as text (chapter numbers from a source, for example).
 */
fun String.westernDigits(): String {
    val needsWork = any {
        it in '\u0660'..'\u0669' || it in '\u06F0'..'\u06F9'
    }
    if (!needsWork) return this
    return buildString(length) {
        for (character in this@westernDigits) {
            append(
                when (character) {
                    in '\u0660'..'\u0669' -> '0' + (character - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (character - '\u06F0')
                    else -> character
                },
            )
        }
    }
}

/**
 * One place that decides how numbers look. MangaOS shows Western digits (0-9) in every
 * language, including Arabic, while the surrounding text keeps its own language, alignment
 * and direction. Only the display is affected: stored ids, file names, chapter keys and
 * arithmetic are never touched.
 */
object Numbers {

    /** Formatting locale for every number the app renders. */
    val Western: Locale = Locale.US

    /** Integer text, always Western. */
    fun integer(value: Long): String = String.format(Western, "%d", value)

    /** Fixed-point text with a Western decimal separator. */
    fun decimal(value: Double, decimals: Int = 1): String =
        String.format(Western, "%.${decimals}f", value)

    fun decimal(value: Float, decimals: Int = 1): String = decimal(value.toDouble(), decimals)

    /** Percentage text such as `42%`. */
    fun percent(fraction: Float): String =
        String.format(Western, "%d%%", (fraction.coerceIn(0f, 1f) * 100f).toInt())
}

/**
 * Formats a resource the same way [android.content.res.Resources.getString] does, but with a
 * fixed number locale. `Resources` formats arguments with the *content* locale, which for
 * Arabic means Arabic-Indic digits; reading the raw pattern and formatting it ourselves keeps
 * the translated text and RTL layout intact while forcing `1234567890`.
 *
 * Pure-number string arguments (chapter numbers coming from a source, for example) are
 * normalised as well. Titles, names and any other text are passed through untouched.
 */
fun Context.appString(@StringRes id: Int, vararg args: Any): String {
    val pattern = resources.getText(id).toString()
    if (args.isEmpty()) return pattern
    val normalized = args.map { argument ->
        if (argument is String && argument.isNumericDisplay()) argument.westernDigits() else argument
    }
    return String.format(Numbers.Western, pattern, *normalized.toTypedArray())
}

@Composable
fun appString(@StringRes id: Int, vararg args: Any): String =
    LocalContext.current.appString(id, *args)

/** True when a string is nothing but a displayed number (digits plus . , - and spaces). */
private fun String.isNumericDisplay(): Boolean =
    isNotBlank() && all { character ->
        character.isDigit() || character == '.' || character == ',' || character == '-' || character == ' '
    }
