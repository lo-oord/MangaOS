package com.mangaos.app.core.ui

import com.mangaos.app.core.ui.components.LanguageSwitch
import com.mangaos.app.core.util.LocaleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overlay names the language the user picked, in that language. The name is derived from
 * the language tag itself, so it can never disagree with the resources being applied.
 */
class LanguageSwitchTest {

    @Test
    fun `the message names the chosen language in its own script`() {
        assertEquals("English", LanguageSwitch(LocaleManager.ENGLISH).displayName)
        assertEquals("العربية", LanguageSwitch(LocaleManager.ARABIC).displayName)
        assertEquals("日本語", LanguageSwitch(LocaleManager.JAPANESE).displayName)
    }

    @Test
    fun `every supported language has a real native name`() {
        LocaleManager.supportedLanguages.forEach { code ->
            val switch = LanguageSwitch(code)
            assertEquals(code, switch.code)
            assertTrue("$code has no native name", switch.displayName.isNotBlank())
        }
    }
}
