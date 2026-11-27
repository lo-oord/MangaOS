package com.mangaos.app.sources.images

import com.mangaos.app.sources.implementations.mangatek.MangaTekOverlay
import java.io.IOException
import java.net.URLDecoder
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Paints the translated text of a MangaTek page onto the page image.
 *
 * The source marks such a page by carrying its bubble list in the URL fragment, which no HTTP
 * request sends. This interceptor runs in the image pipeline shared by the reader and the
 * download queue, so a downloaded chapter keeps its translation too instead of losing the
 * text layer.
 */
object OverlayImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        if (!fragment.startsWith(MangaTekOverlay.FRAGMENT_PREFIX)) return chain.proceed(request)

        val bubbles = runCatching {
            val json = URLDecoder.decode(
                fragment.removePrefix(MangaTekOverlay.FRAGMENT_PREFIX),
                "UTF-8",
            )
            MangaTekOverlay.decodeBubbles(json)
        }.getOrDefault(emptyList())
        if (bubbles.isEmpty()) return chain.proceed(stripFragment(request))

        val clean = stripFragment(request)
        val response = chain.proceed(clean)
        if (!response.isSuccessful) return response

        return try {
            val original = response.body.bytes()
            val painted = MangaTekOverlay.paint(original, bubbles)
            response.newBuilder()
                .body((painted ?: original).toResponseBody("image/png".toMediaType()))
                .build()
        } catch (error: Exception) {
            throw IOException("Could not paint the page's translated text", error)
        }
    }

    private fun stripFragment(request: okhttp3.Request): okhttp3.Request =
        request.newBuilder().url(request.url.newBuilder().fragment(null).build()).build()
}
