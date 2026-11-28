package com.mangaos.app.data.firebase

import com.google.firebase.auth.FirebaseAuthException

/** Why an account operation failed, already reduced to something a screen can explain. */
enum class AuthReason {
    INVALID_EMAIL,
    WRONG_PASSWORD,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    TOO_MANY_REQUESTS,
    NETWORK,
    NEEDS_LINK,
    CREDENTIAL_IN_USE,
    ALREADY_LINKED,
    RECENT_LOGIN_REQUIRED,
    PASSWORD_MISSING,
    USER_DISABLED,
    USER_NOT_FOUND,
    EMAIL_NOT_VERIFIED,
    NOT_CONFIGURED,
    CANCELLED,
    GENERIC,
}

class AuthFailure(
    val reason: AuthReason,
    detail: String? = null,
) : Exception(detail ?: reason.name)

/**
 * Firebase reports failures as string codes; mapping them here keeps every screen free of
 * provider-specific wording and stops raw technical text from reaching the reader.
 */
fun FirebaseAuthException.toAuthReason(): AuthReason = when (errorCode) {
    "ERROR_INVALID_EMAIL" -> AuthReason.INVALID_EMAIL
    "ERROR_WRONG_PASSWORD" -> AuthReason.WRONG_PASSWORD
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthReason.EMAIL_IN_USE
    "ERROR_WEAK_PASSWORD" -> AuthReason.WEAK_PASSWORD
    "ERROR_TOO_MANY_REQUESTS" -> AuthReason.TOO_MANY_REQUESTS
    "ERROR_NETWORK_REQUEST_FAILED" -> AuthReason.NETWORK
    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> AuthReason.NEEDS_LINK
    "ERROR_CREDENTIAL_ALREADY_IN_USE" -> AuthReason.CREDENTIAL_IN_USE
    "ERROR_PROVIDER_ALREADY_LINKED" -> AuthReason.ALREADY_LINKED
    "ERROR_REQUIRES_RECENT_LOGIN" -> AuthReason.RECENT_LOGIN_REQUIRED
    "ERROR_NO_SUCH_PROVIDER" -> AuthReason.PASSWORD_MISSING
    "ERROR_USER_DISABLED" -> AuthReason.USER_DISABLED
    "ERROR_USER_NOT_FOUND" -> AuthReason.USER_NOT_FOUND
    "ERROR_EMAIL_NOT_VERIFIED" -> AuthReason.EMAIL_NOT_VERIFIED
    "ERROR_OPERATION_NOT_ALLOWED" -> AuthReason.NOT_CONFIGURED
    "ERROR_INVALID_CREDENTIAL" -> AuthReason.WRONG_PASSWORD
    else -> AuthReason.GENERIC
}
