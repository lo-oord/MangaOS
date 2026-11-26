package com.mangaos.app.core.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumbersTest {

    @Test
    fun `arabic-indic digits become western`() {
        assertEquals("1234567890", "\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u0660".westernDigits())
        assertEquals("Chapter 12", "Chapter \u0661\u0662".westernDigits())
    }

    @Test
    fun `extended arabic-indic digits become western`() {
        assertEquals("2025", "\u06F2\u06F0\u06F2\u06F5".westernDigits())
    }

    @Test
    fun `plain text is untouched`() {
        val text = "One Piece 1089"
        assertEquals(text, text.westernDigits())
    }

    @Test
    fun `numbers are always western regardless of the active language`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-u-nu-latn"))
            assertEquals("1234567890", Numbers.integer(1234567890L))
            assertEquals("8.5", Numbers.decimal(8.45, 1))
            assertEquals("42%", Numbers.percent(0.42f))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `display locale keeps the language while forcing latin digits`() {
        listOf("ar", "en", "ja").forEach { language ->
            val locale = LocaleManager.displayLocale(language)
            assertEquals(language, locale.language)
            assertEquals("latn", locale.getUnicodeLocaleType("nu"))
            assertTrue(locale.country.isEmpty())
        }
    }

    @Test
    fun `pages and chapters stay western in a sentence`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(LocaleManager.displayLocale("ar"))
            val sentence = String.format(Locale.US, "الفصل %d من %d", 123, 250)
            assertEquals("الفصل 123 من 250", sentence)
        } finally {
            Locale.setDefault(original)
        }
    }
}
