package com.mangaos.app.presentation.auth

import androidx.annotation.StringRes
import com.mangaos.app.R
import com.mangaos.app.data.firebase.AuthReason

/** Every account failure becomes one short, translated sentence. */
@StringRes
fun AuthReason.messageRes(): Int = when (this) {
    AuthReason.INVALID_EMAIL -> R.string.auth_error_email_invalid
    AuthReason.WRONG_PASSWORD -> R.string.auth_error_wrong_password
    AuthReason.EMAIL_IN_USE -> R.string.auth_error_email_taken
    AuthReason.WEAK_PASSWORD -> R.string.auth_error_password_weak
    AuthReason.TOO_MANY_REQUESTS -> R.string.auth_error_too_many
    AuthReason.NETWORK -> R.string.auth_error_network
    AuthReason.NEEDS_LINK -> R.string.auth_error_needs_link
    AuthReason.CREDENTIAL_IN_USE -> R.string.auth_error_credential_in_use
    AuthReason.ALREADY_LINKED -> R.string.auth_error_already_linked
    AuthReason.RECENT_LOGIN_REQUIRED -> R.string.auth_error_recent_login
    AuthReason.PASSWORD_MISSING -> R.string.auth_error_password_missing
    AuthReason.USER_DISABLED -> R.string.auth_error_disabled
    AuthReason.USER_NOT_FOUND -> R.string.auth_error_no_account
    AuthReason.EMAIL_NOT_VERIFIED -> R.string.auth_error_email_not_verified
    AuthReason.NOT_CONFIGURED, AuthReason.GENERIC -> R.string.auth_error_generic
    AuthReason.CANCELLED -> R.string.auth_error_cancelled
}
