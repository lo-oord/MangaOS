package eu.kanade.tachiyomi

import com.mangaos.app.BuildConfig

/**
 * Host application information exposed to Mihon extensions.
 *
 * This class is part of the extension API surface (extension-lib 1.3+) and is not included in
 * the published source-api artifact. Sources such as MangaDex call it directly, so the host
 * must provide it or the source class fails verification before it can return any catalogue.
 */
object AppInfo {

    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    fun getVersionName(): String = BuildConfig.VERSION_NAME

    /** Image MIME types the MangaOS reader can decode (extension-lib 1.5+). */
    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "image/avif",
        "image/jxl",
        "image/heif",
    )
}
