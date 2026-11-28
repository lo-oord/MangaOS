package com.mangaos.app.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Profile photos live inside the account document, so they have to be small. The photo is
 * decoded, scaled down, compressed and then shrunk again until it fits a hard byte budget that
 * leaves the rest of the 1 MiB document for real fields.
 */
object AvatarCodec {

    /** Base64 grows by ~4/3, so 90 KiB of JPEG stays well below a quarter of the document. */
    private const val TARGET_BYTES = 90_000
    private const val MAX_DIMENSION = 512
    private const val MIN_DIMENSION = 96
    private const val MIN_QUALITY = 35

    suspend fun encode(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val original = runCatching { decode(context, uri) }.getOrNull()
            ?: throw IllegalArgumentException("The selected image could not be read")
        val bytes = compressUntilSmallEnough(original)
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (!original.isRecycled) original.recycle()
        base64
    }

    /** Used by the preview before anything is uploaded. */
    suspend fun preview(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri) }.getOrNull()
    }

    fun decodeBase64(value: String?): Bitmap? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(value, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun compressUntilSmallEnough(original: Bitmap): ByteArray {
        var dimension = minOf(MAX_DIMENSION, maxOf(original.width, original.height))
        var quality = 82
        while (true) {
            val scaled = scale(original, dimension)
            val bytes = compress(scaled, quality)
            if (scaled !== original) scaled.recycle()
            if (bytes.size <= TARGET_BYTES) return bytes
            if (quality > MIN_QUALITY) {
                quality -= 12
            } else if (dimension > MIN_DIMENSION) {
                dimension = (dimension * 3) / 4
                quality = 70
            } else {
                return bytes
            }
        }
    }

    private fun scale(original: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(original.width, original.height)
        if (longest <= maxDimension) return original
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val width = (original.width * ratio).toInt().coerceAtLeast(1)
        val height = (original.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(original, width, height, true)
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private fun decode(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("Empty image stream")
    }
}
