package com.mangaos.app.data.firebase

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File

/**
 * The account photo is stored in Firestore as base64, which the image loader cannot open as a
 * URI. It is written once to a private file named after the account, so the avatar is a normal
 * file URI everywhere it is drawn, and a photo change simply overwrites that file.
 */
object AvatarCache {

    fun write(context: Context, uid: String, base64: String): String? = runCatching {
        val directory = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(directory, "account_${uid.hashCode().toUInt()}.jpg")
        file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
        // A changing query keeps the image cache honest after the photo is replaced.
        Uri.fromFile(file).buildUpon()
            .appendQueryParameter("v", file.lastModified().toString())
            .build()
            .toString()
    }.getOrNull()

    fun clear(context: Context, uid: String) {
        runCatching {
            File(context.filesDir, "avatars/account_${uid.hashCode().toUInt()}.jpg").delete()
        }
    }
}
