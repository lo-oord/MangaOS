package com.mangaos.app.data.firebase

import com.google.firebase.auth.FirebaseUser
import com.mangaos.app.di.ServiceLocator
import com.mangaos.app.domain.model.SessionType
import com.mangaos.app.domain.model.UserProfile
import com.mangaos.app.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Binds the Firebase session to the app: it creates the account document, keeps the local
 * profile in step with it, runs the cloud sync and returns the single [AccountState] screens read.
 *
 * Signing out stops the listeners and the periodic check but never deletes anything in the cloud.
 */
class AccountSession(
    private val scope: CoroutineScope,
    private val accounts: AccountRepository,
    private val cloudSync: CloudSync,
    private val users: UserRepository,
    private val scheduleWork: (String?) -> Unit,
    private val registerDevice: (String) -> Unit,
    private val clearDevice: () -> Unit,
) {

    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private var authJob: Job? = null
    private var bindJob: Job? = null
    private var activeUid: String? = null

    fun start() {
        if (authJob != null) return
        authJob = scope.launch {
            FirebaseAuthManager.authState().collect { user -> bind(user) }
        }
    }

    fun stop() {
        authJob?.cancel()
        authJob = null
        bindJob?.cancel()
        bindJob = null
        cloudSync.stop()
    }

    private fun bind(user: FirebaseUser?) {
        bindJob?.cancel()
        if (user == null) {
            activeUid = null
            cloudSync.stop()
            clearDevice()
            scheduleWork(null)
            _state.value = AccountState(loading = false)
            // A signed-out account must land on authentication; a guest keeps their local session.
            scope.launch {
                val current = users.profile.value
                if (current != null && current.sessionType == SessionType.ACCOUNT) users.signOut()
            }
            return
        }

        activeUid = user.uid
        cloudSync.start(user.uid)
        registerDevice(user.uid)
        scheduleWork(user.uid)
        // The identity is known before the document is: publishing it right away keeps the root
        // host from showing an unresolved session while the account document is being read.
        _state.value = AccountState(
            loading = true,
            uid = user.uid,
            email = user.email,
            emailVerified = user.isEmailVerified,
            hasPassword = FirebaseAuthManager.hasPassword,
            hasGoogle = FirebaseAuthManager.hasGoogle,
        )
        bindJob = scope.launch {
            var createAttempts = 0
            combine(
                accounts.account(user.uid),
                accounts.badge(user.uid),
            ) { account, verified -> account to verified }
                .collect { (account, verified) ->
                    // The snapshot listener is the read the app needs anyway; the account is
                    // only created when that read says it is genuinely missing, which removes a
                    // second read on every sign-in. A transient failure is retried by the next
                    // emission instead of being reported as a completed account.
                    if (account == null && createAttempts < MAX_CREATE_ATTEMPTS) {
                        createAttempts++
                        runCatching {
                            accounts.ensureAccount(user.uid, user.email, user.displayName)
                        }
                        return@collect
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        uid = user.uid,
                        email = user.email ?: account?.email,
                        emailVerified = FirebaseAuthManager.isEmailVerified,
                        hasPassword = FirebaseAuthManager.hasPassword,
                        hasGoogle = FirebaseAuthManager.hasGoogle,
                        account = account,
                        verified = verified,
                    )
                    mirrorVerification(user.uid, account, verified)
                    adoptProfile(user, account, verified)
                }
        }
    }

    /**
     * Writes the badge state into `users/{uid}.isVerified` when — and only when — it disagrees.
     * The badge document remains the source of truth and the rules refuse any other value, so
     * this can never grant verification; it only keeps the visible field in step.
     */
    private suspend fun mirrorVerification(uid: String, account: AccountDoc?, verified: Boolean) {
        if (account == null) return
        if (account.isVerified == verified) return
        runCatching { accounts.updateAccount(uid, mapOf(FIELD_VERIFIED to verified)) }
    }

    /** Keeps the drawer, cards and profile writing from one local profile row. */
    private suspend fun adoptProfile(user: FirebaseUser, account: AccountDoc?, verified: Boolean) {
        val display = account?.displayName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "reader"
        users.adoptAccount(
            UserProfile(
                id = user.uid,
                username = account?.username?.takeIf { it.isNotBlank() } ?: display.take(20),
                displayName = display,
                avatarUri = account?.photo?.let { base64 ->
                    AvatarCache.write(ServiceLocator.context(), user.uid, base64)
                },
                email = user.email,
                sessionType = SessionType.ACCOUNT,
                countryCode = account?.country,
                age = account?.age,
                preferredLanguage = account?.language,
                verified = verified,
            ),
        )
    }

    /** Refreshes provider and verification flags after linking, password or verification. */
    suspend fun refresh() {
        runCatching { FirebaseAuthManager.reload() }
        val user = FirebaseAuthManager.user ?: return
        // A confirmed address is what opens the setup and the app, so the reloaded value is
        // published immediately instead of waiting for the next auth event.
        _state.value = _state.value.copy(
            emailVerified = user.isEmailVerified,
            hasPassword = FirebaseAuthManager.hasPassword,
            hasGoogle = FirebaseAuthManager.hasGoogle,
        )
    }

    private companion object {
        const val MAX_CREATE_ATTEMPTS = 3
        const val FIELD_VERIFIED = "isVerified"
    }
}
