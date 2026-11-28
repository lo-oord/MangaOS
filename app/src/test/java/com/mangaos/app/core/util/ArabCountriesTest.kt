package com.mangaos.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The country picker offers the Arab states only, and every code in it is a real ISO code. */
class ArabCountriesTest {

    @Test
    fun `the list holds the twenty-two Arab states and no duplicate`() {
        assertEquals(22, ArabCountries.codes.size)
        assertEquals(ArabCountries.codes.size, ArabCountries.codes.toSet().size)
    }

    @Test
    fun `every code is two uppercase letters`() {
        ArabCountries.codes.forEach { code ->
            assertTrue(code, Regex("^[A-Z]{2}$").matches(code))
        }
    }

    @Test
    fun `non-arab and unknown codes are refused`() {
        assertFalse(ArabCountries.contains("US"))
        assertFalse(ArabCountries.contains("FR"))
        assertFalse(ArabCountries.contains("XX"))
        assertFalse(ArabCountries.contains(""))
        assertFalse(ArabCountries.contains(null))
    }

    @Test
    fun `arab codes are accepted in any case`() {
        assertTrue(ArabCountries.contains("sa"))
        assertTrue(ArabCountries.contains("EG"))
    }
}
