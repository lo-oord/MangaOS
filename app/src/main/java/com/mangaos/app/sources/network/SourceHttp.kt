package com.mangaos.app.sources.network

import com.mangaos.app.domain.model.SourceFailure
import com.mangaos.app.sources.core.SourceException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Small network layer shared by the native sources. It owns the OkHttp client, the retry
 * policy and the JSON helpers, but no parsing rules: every source parses its own payload.
 *
 * Requests are cancellable: leaving a screen or starting a new search cancels the pending
 * call instead of letting an outdated response overwrite newer results.
 */
class SourceHttp(
    private val userAgent: String,
    private val defaultHeaders: Map<String, String> = emptyMap(),
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun text(url: String, headers: Map<String, String> = emptyMap()): String {
        var last: Throwable? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                return execute(url, headers)
            } catch (error: IOException) {
                last = error
                if (attempt < ATTEMPTS - 1) kotlinx.coroutines.delay(250L * (attempt + 1))
            } catch (error: SourceException) {
                // A 4xx is the site's answer, not a transient problem: do not retry it.
                throw error
            }
        }
        throw last.toSourceException()
    }

    suspend fun json(url: String, headers: Map<String, String> = emptyMap()): JsonElement {
        val payload = text(url, headers)
        return try {
            JSON.parseToJsonElement(payload)
        } catch (error: Exception) {
            throw SourceException(SourceFailure.PARSING, "Invalid JSON from $url", error)
        }
    }

    private suspend fun execute(url: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "ar,en;q=0.8,ja;q=0.6")
            .apply {
                defaultHeaders.forEach { (key, value) -> header(key, value) }
                headers.forEach { (key, value) -> header(key, value) }
            }
            .get()
            .build()
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = runCatching { it.body.string() }.getOrElse { error ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(error)
                            }
                            return
                        }
                        val code = it.code
                        if (code !in 200..299) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    SourceException(
                                        SourceFailure.NETWORK,
                                        "HTTP $code for $url",
                                    ),
                                )
                            }
                            return
                        }
                        if (body.isBlank()) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    SourceException(SourceFailure.EMPTY, "Empty response from $url"),
                                )
                            }
                            return
                        }
                        if (continuation.isActive) continuation.resume(body)
                    }
                }
            })
        }
    }

    private fun Throwable?.toSourceException(): SourceException = when (this) {
        is SourceException -> this
        is UnknownHostException -> SourceException(SourceFailure.OFFLINE, "Host unreachable", this)
        is SocketTimeoutException -> SourceException(SourceFailure.NETWORK, "Request timed out", this)
        is IOException -> SourceException(SourceFailure.NETWORK, message, this)
        null -> SourceException(SourceFailure.NETWORK, "Request failed")
        else -> SourceException(SourceFailure.NETWORK, message, this)
    }

    companion object {
        private const val ATTEMPTS = 2
        val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * MangaDex rejects browser user agents with a bot challenge; its API requires a client
         * that identifies itself. Sites that check for a mobile browser use [ANDROID_USER_AGENT].
         */
        const val APP_USER_AGENT = "MangaOS/1.0 (Android)"

        const val ANDROID_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.array(): JsonArray? = this as? JsonArray
fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).obj()
fun JsonObject?.array(key: String): JsonArray? = this?.get(key).array()

fun JsonObject?.string(key: String): String? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject?.long(key: String): Long? = when (val value = this?.get(key) as? JsonPrimitive) {
    null -> null
    else -> value.longOrNull ?: value.contentOrNull?.toLongOrNull()
}

fun JsonObject?.double(key: String): Double? = when (val value = this?.get(key) as? JsonPrimitive) {
    null -> null
    else -> value.doubleOrNull ?: value.contentOrNull?.toDoubleOrNull()
}

fun JsonObject?.bool(key: String): Boolean? =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

fun JsonObject?.stringList(key: String): List<String> = this?.array(key)
    ?.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element.string("name") ?: element.string("title")
            else -> null
        }
    }
    .orEmpty()
