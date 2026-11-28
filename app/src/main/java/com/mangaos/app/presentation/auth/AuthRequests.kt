package com.mangaos.app.presentation.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request to open the account shell for a specific reason: a guest who taps "sign in" should
 * land on the address step of sign-in, and one who taps "create account" on the same step of
 * sign-up, instead of having to choose again on the welcome page.
 *
 * The root host owns the shell, so screens only record the request here; it is consumed once and
 * cleared, which keeps the intent from firing again after a sign-out.
 */
object AuthRequests {

    private val _pending = MutableStateFlow<AuthMode?>(null)
    val pending: StateFlow<AuthMode?> = _pending.asStateFlow()

    fun request(mode: AuthMode) {
        _pending.value = mode
    }

    fun consume() {
        _pending.value = null
    }
}
