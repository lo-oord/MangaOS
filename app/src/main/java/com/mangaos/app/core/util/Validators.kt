package com.mangaos.app.core.util

/** Local-only validation. Nothing here touches a server. */
object Validators {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val USERNAME_REGEX = Regex("^[A-Za-z0-9_.]+$")

    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 20
    const val VERIFICATION_CODE_LENGTH = 6
    const val MIN_AGE = 13

    fun isEmailValid(email: String): Boolean = EMAIL_REGEX.matches(email.trim())

    fun isPasswordValid(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    fun passwordsMatch(password: String, confirmation: String): Boolean = password == confirmation

    fun usernameError(username: String): UsernameError? {
        val value = username.trim()
        return when {
            value.isEmpty() -> UsernameError.EMPTY
            value.length < MIN_USERNAME_LENGTH || value.length > MAX_USERNAME_LENGTH -> UsernameError.LENGTH
            !USERNAME_REGEX.matches(value) -> UsernameError.CHARACTERS
            else -> null
        }
    }

    fun isVerificationCodeComplete(code: String): Boolean =
        code.length == VERIFICATION_CODE_LENGTH && code.all { it.isDigit() }

    enum class UsernameError { EMPTY, LENGTH, CHARACTERS }
}
