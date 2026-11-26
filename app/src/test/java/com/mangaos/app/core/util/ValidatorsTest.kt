package com.mangaos.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `valid emails are accepted`() {
        assertTrue(Validators.isEmailValid("reader@mangaos.app"))
        assertTrue(Validators.isEmailValid("a.b+tag@sub.example.co"))
        assertTrue(Validators.isEmailValid("  spaced@example.com  "))
    }

    @Test
    fun `malformed emails are rejected`() {
        assertFalse(Validators.isEmailValid(""))
        assertFalse(Validators.isEmailValid("reader"))
        assertFalse(Validators.isEmailValid("reader@"))
        assertFalse(Validators.isEmailValid("@example.com"))
        assertFalse(Validators.isEmailValid("reader@example"))
        assertFalse(Validators.isEmailValid("reader example@mail.com"))
    }

    @Test
    fun `password needs the minimum length`() {
        assertFalse(Validators.isPasswordValid("1234567"))
        assertTrue(Validators.isPasswordValid("12345678"))
    }

    @Test
    fun `passwords must match exactly`() {
        assertTrue(Validators.passwordsMatch("secret123", "secret123"))
        assertFalse(Validators.passwordsMatch("secret123", "Secret123"))
    }

    @Test
    fun `username error ordering`() {
        assertEquals(Validators.UsernameError.EMPTY, Validators.usernameError("   "))
        assertEquals(Validators.UsernameError.LENGTH, Validators.usernameError("ab"))
        assertEquals(
            Validators.UsernameError.LENGTH,
            Validators.usernameError("a".repeat(Validators.MAX_USERNAME_LENGTH + 1)),
        )
        assertEquals(Validators.UsernameError.CHARACTERS, Validators.usernameError("bad name"))
        assertEquals(Validators.UsernameError.CHARACTERS, Validators.usernameError("bad-name"))
        assertNull(Validators.usernameError("manga_reader.01"))
    }

    @Test
    fun `verification code must be six digits`() {
        assertTrue(Validators.isVerificationCodeComplete("123456"))
        assertFalse(Validators.isVerificationCodeComplete("12345"))
        assertFalse(Validators.isVerificationCodeComplete("12345a"))
        assertFalse(Validators.isVerificationCodeComplete(""))
    }
}
