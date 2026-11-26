package tachiyomi.i18n

import com.mangaos.app.R
import dev.icerock.moko.resources.StringResource

/**
 * Compatibility surface for the resource references compiled into `core-common`.
 *
 * The publisher ships its i18n module as a private runtime artifact (it is not published to
 * any repository), but the network interceptors in `core-common` link against `MR.strings`.
 * Extensions never use these strings; they are only shown by the host when a source hits a
 * Cloudflare challenge or needs an installed WebView. Providing the same object with our own
 * translated strings keeps those code paths loadable without shipping the private module.
 */
object MR {
    object strings {
        val information_webview_required = StringResource(R.string.info_webview_required)
        val information_cloudflare_bypass_failure =
            StringResource(R.string.info_cloudflare_bypass_failure)
    }
}
