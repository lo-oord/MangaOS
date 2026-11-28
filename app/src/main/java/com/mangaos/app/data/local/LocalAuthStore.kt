package com.mangaos.app.data.local

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The app's local account store, until a hosted authentication backend is connected.
 *
 * It is a real credential store, not a placeholder: passwords are salted and hashed, signing in
 * verifies them, a duplicate address is refused, and the reset flow replaces the password. The
 * session itself stays in [com.mangaos.app.data.repository.DataStoreUserRepository], so a signed
 * out user keeps their library and settings but sees the authentication screen again.
 */
class LocalAuthStore(context: Context) {

    private val prefs = context.getSharedPreferences("mangaos_accounts", Context.MODE_PRIVATE)

    data class Account(
        val email: String,
        val displayName: String,
        val username: String,
        val salt: String,
        val hash: String,
    )

    sealed interface Result {
        data class Success(val account: Account) : Result
        data object EmailTaken : Result
        data object UnknownEmail : Result
        data object WrongPassword : Result
        data object Invalid : Result
    }

    fun account(email: String): Account? = accounts().firstOrNull { it.email.equals(email.trim(), true) }

    fun register(email: String, password: String, displayName: String, username: String): Result {
        val clean = email.trim()
        if (clean.isBlank() || password.isBlank()) return Result.Invalid
        val existing = accounts()
        if (existing.any { it.email.equals(clean, true) }) return Result.EmailTaken
        val salt = UUID.randomUUID().toString()
        val account = Account(
            email = clean,
            displayName = displayName.trim().ifBlank { clean.substringBefore('@') },
            username = username.trim().ifBlank { clean.substringBefore('@').take(20) },
            salt = salt,
            hash = hash(password, salt),
        )
        write(existing + account)
        return Result.Success(account)
    }

    fun signIn(email: String, password: String): Result {
        val account = account(email) ?: return Result.UnknownEmail
        return if (account.hash == hash(password, account.salt)) {
            Result.Success(account)
        } else {
            Result.WrongPassword
        }
    }

    /** Replaces the password of an existing account; the address itself must exist. */
    fun resetPassword(email: String, newPassword: String): Result {
        val clean = email.trim()
        if (clean.isBlank() || newPassword.isBlank()) return Result.Invalid
        val existing = accounts()
        val index = existing.indexOfFirst { it.email.equals(clean, true) }
        if (index < 0) return Result.UnknownEmail
        val salt = UUID.randomUUID().toString()
        val updated = existing[index].copy(salt = salt, hash = hash(newPassword, salt))
        write(existing.toMutableList().also { it[index] = updated })
        return Result.Success(updated)
    }

    private fun accounts(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                Account(
                    email = entry.optString("email"),
                    displayName = entry.optString("displayName"),
                    username = entry.optString("username"),
                    salt = entry.optString("salt"),
                    hash = entry.optString("hash"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject().apply {
                    put("email", account.email)
                    put("displayName", account.displayName)
                    put("username", account.username)
                    put("salt", account.salt)
                    put("hash", account.hash)
                },
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts"
    }
}
