package com.mangaos.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Keeps one app-private copy of a work's cover. Notifications and the offline library read it
 * from disk, so a missing network or an evicted image cache cannot hide a downloaded work.
 * Every failure is non-fatal: the caller simply keeps the remote URL.
 */
class CoverStore(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val root: File get() = File(context.filesDir, "covers")

    fun localCover(mangaKey: String): String? {
        val file = File(root, "${hash(mangaKey)}.img")
        return if (file.exists() && file.length() > 0L) file.toURI().toString() else null
    }

    suspend fun cache(
        mangaKey: String,
        url: String?,
        headers: Map<String, String> = emptyMap(),
    ): String? = withContext(Dispatchers.IO) {
        localCover(mangaKey)?.let { return@withContext it }
        val remote = url?.takeIf { it.isNotBlank() } ?: return@withContext null
        runCatching {
            val request = Request.Builder()
                .url(remote)
                .apply {
                    headers.forEach { (key, value) ->
                        if (key.isNotBlank() && value.isNotBlank()) header(key, value)
                    }
                }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                if (!root.exists() && !root.mkdirs()) return@runCatching null
                val target = File(root, "${hash(mangaKey)}.img")
                val temporary = File(root, "${hash(mangaKey)}.part")
                temporary.outputStream().use { output -> body.byteStream().copyTo(output) }
                if (temporary.length() <= 0L) {
                    temporary.delete()
                    return@runCatching null
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete()
                    return@runCatching null
                }
                target.toURI().toString()
            }
        }.getOrNull()
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
}
