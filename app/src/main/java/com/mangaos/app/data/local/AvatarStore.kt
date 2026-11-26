package com.mangaos.app.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a photo-picker image into app-private storage so the avatar survives process
 * death and does not depend on a temporary content-URI grant. There is no cloud upload.
 */
object AvatarStore {

    private const val DIRECTORY = "avatars"

    suspend fun save(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "avatar_${System.currentTimeMillis()}.jpg")
        val stream = context.contentResolver.openInputStream(source)
            ?: error("Cannot open the selected image")
        stream.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        Uri.fromFile(destination).toString()
    }

    suspend fun delete(context: Context, uriString: String?) = withContext(Dispatchers.IO) {
        val uri = uriString?.let(Uri::parse) ?: return@withContext
        if (uri.scheme != "file") return@withContext
        val file = uri.path?.let(::File) ?: return@withContext
        val allowedRoot = File(context.filesDir, DIRECTORY).canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return@withContext
        if (candidate.parentFile == allowedRoot && candidate.exists()) {
            candidate.delete()
        }
    }
}
