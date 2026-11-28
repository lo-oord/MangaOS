package com.mangaos.app.data.repository

/**
 * The account document stores the profile photo as a small base64 JPEG. Coil reads a `data:`
 * URI directly, so no file is written and no placeholder URL is invented.
 */
fun String?.toAvatarDataUri(): String? =
    takeIf { !it.isNullOrBlank() }?.let { "data:image/jpeg;base64,$it" }
