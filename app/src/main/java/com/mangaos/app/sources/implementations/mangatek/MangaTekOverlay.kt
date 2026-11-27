package com.mangaos.app.sources.implementations.mangatek

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/**
 * MangaTek's translated text layer.
 *
 * A MangaTek page is not always one picture: the site keeps the translation of each speech
 * bubble as text with a position on the page, delivered separately from the page image (an
 * encrypted `overlayBlob` beside the page list, or the `overlay` answer of the reader's unlock
 * endpoint). Dropping those entries would silently delete the translation, so the bubble list
 * travels with the page and is painted over the page image before the reader or a download
 * ever sees it.
 *
 * This file holds the blob decryption and the painter; which pages carry overlays is decided
 * by [com.mangaos.app.sources.implementations.MangaTekSource].
 */
object MangaTekOverlay {

    /** Key the site uses for the overlay blob it embeds in the reader page. */
    private const val EMBEDDED_KEY =
        "ff453871399fe268588a0936b45376022d85ed0fd1292001d5102f6a30291dc1"

    /** Marker the page URL's fragment carries, so the image pipeline can recognise a page. */
    const val FRAGMENT_PREFIX = "mangatek-overlay="

    class Bubble(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val angle: Float,
        val color: String?,
        val strokeColor: String?,
        val fontSizePx: Float,
        val lineHeight: Float,
        val strokeWidthPx: Float,
        val textAlign: String?,
    )

    /**
     * The site's blob is `ivHex:ctHex:tagHex`, AES-GCM with a fixed key. The decrypted payload
     * is `{"pages":[{"page_number":1,"overlays":[…]}]}`.
     */
    fun decryptEmbedded(blob: String): List<PageOverlay> {
        val parts = blob.split(":")
        require(parts.size == 3) { "Unexpected overlay format" }
        val iv = parts[0].hexToBytes()
        val ciphertext = parts[1].hexToBytes()
        val tag = parts[2].hexToBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(EMBEDDED_KEY.hexToBytes(), "AES"),
            GCMParameterSpec(tag.size * 8, iv),
        )
        val plain = cipher.doFinal(ciphertext + tag)
        return parsePages(String(plain, Charsets.UTF_8))
    }

    fun parsePages(json: String): List<PageOverlay> {
        val root = runCatching {
            com.mangaos.app.sources.network.SourceHttp.JSON.parseToJsonElement(json) as? JsonObject
        }.getOrNull() ?: return emptyList()
        val pages = root["pages"] as? JsonArray ?: return emptyList()
        return pages.mapNotNull { element ->
            val page = element as? JsonObject ?: return@mapNotNull null
            val number = (page["page_number"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            val overlays = (page["overlays"] as? JsonArray).orEmpty().mapNotNull { bubble ->
                (bubble as? JsonObject)?.toBubble()
            }
            PageOverlay(number, overlays)
        }
    }

    private fun JsonObject.toBubble(): Bubble? {
        val text = (this["text"] as? JsonPrimitive)?.contentOrNull ?: return null
        if (text.isBlank()) return null
        return Bubble(
            text = text,
            x = float("x"),
            y = float("y"),
            width = float("w"),
            height = float("h"),
            angle = float("angle"),
            color = (this["color"] as? JsonPrimitive)?.contentOrNull,
            strokeColor = (this["stroke_color"] as? JsonPrimitive)?.contentOrNull,
            fontSizePx = float("font_size_px").takeIf { it > 0f } ?: 16f,
            lineHeight = float("line_height").takeIf { it > 0f } ?: 1.2f,
            strokeWidthPx = float("stroke_width_px"),
            textAlign = (this["text_align"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun JsonObject.float(key: String): Float =
        (this[key] as? JsonPrimitive)?.let { it.floatOrNull ?: it.doubleOrNull?.toFloat() } ?: 0f

    fun encodeBubbles(bubbles: List<Bubble>): String = buildString {
        append('[')
        bubbles.forEachIndexed { index, bubble ->
            if (index > 0) append(',')
            append('{')
            append("\"text\":").append(quote(bubble.text)).append(',')
            append("\"x\":").append(bubble.x).append(',')
            append("\"y\":").append(bubble.y).append(',')
            append("\"w\":").append(bubble.width).append(',')
            append("\"h\":").append(bubble.height).append(',')
            append("\"angle\":").append(bubble.angle).append(',')
            append("\"font_size_px\":").append(bubble.fontSizePx).append(',')
            append("\"line_height\":").append(bubble.lineHeight).append(',')
            append("\"stroke_width_px\":").append(bubble.strokeWidthPx)
            bubble.color?.let { append(",\"color\":").append(quote(it)) }
            bubble.strokeColor?.let { append(",\"stroke_color\":").append(quote(it)) }
            bubble.textAlign?.let { append(",\"text_align\":").append(quote(it)) }
            append('}')
        }
        append(']')
    }

    fun decodeBubbles(json: String): List<Bubble> {
        val array = runCatching {
            com.mangaos.app.sources.network.SourceHttp.JSON.parseToJsonElement(json) as? JsonArray
        }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element -> (element as? JsonObject)?.toBubble() }
    }

    /**
     * Paints the bubbles onto the page image, keeping their position, size, alignment and
     * rotation. Styles the site sends are honoured (colour, outline, size, line height); the
     * font stays the app's Arabic font so the text keeps its shaping on every device.
     */
    fun paint(image: ByteArray, bubbles: List<Bubble>): ByteArray? {
        if (bubbles.isEmpty()) return null
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(
                image,
                0,
                image.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                },
            )
        }.getOrNull() ?: return null
        return runCatching {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            bubbles.forEach { bubble -> canvas.drawBubble(bubble, paint, bitmap.width, bitmap.height) }
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        }.also { bitmap.recycle() }.getOrNull()
    }

    private fun Canvas.drawBubble(bubble: Bubble, paint: Paint, width: Int, height: Int) {
        val imageWidth = width.toFloat()
        val imageHeight = height.toFloat()
        val left = min(imageWidth - bubble.width, bubble.x).coerceAtLeast(0f)
        val top = min(imageHeight - bubble.height, bubble.y).coerceAtLeast(0f)
        val boxWidth = min(imageWidth - left, bubble.width)
        val boxHeight = min(imageHeight - top, bubble.height)
        if (boxWidth <= 0f || boxHeight <= 0f) return

        val maxTextWidth = max(1f, boxWidth - 2f)
        var size = bubble.fontSizePx.coerceAtLeast(1f)
        var lines = wrap(bubble.text, paint, size, maxTextWidth)
        repeat(4) {
            val scale = fitScale(lines, paint, maxTextWidth, boxHeight, bubble.lineHeight, size)
            if (scale >= 0.999f) return@repeat
            size = (size * scale).coerceAtLeast(1f)
            lines = wrap(bubble.text, paint, size, maxTextWidth)
        }

        paint.textSize = size
        paint.style = if (bubble.strokeWidthPx > 0f && bubble.strokeColor != null) {
            Paint.Style.STROKE
        } else {
            Paint.Style.FILL
        }
        paint.strokeWidth = bubble.strokeWidthPx
        paint.color = parseColor(bubble.strokeColor, Color.BLACK)
        paint.textAlign = when (bubble.textAlign) {
            "left" -> Paint.Align.LEFT
            "right" -> Paint.Align.RIGHT
            else -> Paint.Align.CENTER
        }
        val pitch = bubble.lineHeight * size
        val totalHeight = pitch * lines.size
        val startY = top + (boxHeight - totalHeight) / 2f + size * 0.8f
        val startX = when (paint.textAlign) {
            Paint.Align.LEFT -> left + 1f
            Paint.Align.RIGHT -> left + boxWidth - 1f
            else -> left + boxWidth / 2f
        }

        save()
        if (bubble.angle != 0f) {
            rotate(bubble.angle, left + boxWidth / 2f, top + boxHeight / 2f)
        }
        if (paint.style == Paint.Style.STROKE) {
            lines.forEachIndexed { index, line -> drawText(line, startX, startY + index * pitch, paint) }
            paint.style = Paint.Style.FILL
        }
        paint.color = parseColor(bubble.color, Color.BLACK)
        lines.forEachIndexed { index, line -> drawText(line, startX, startY + index * pitch, paint) }
        restore()
    }

    private fun fitScale(
        lines: List<String>,
        paint: Paint,
        maxWidth: Float,
        boxHeight: Float,
        lineHeight: Float,
        size: Float,
    ): Float {
        paint.textSize = size
        val widest = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val height = lines.size * lineHeight * size
        val widthScale = if (widest > maxWidth && widest > 0f) maxWidth / widest else 1f
        val heightScale = if (height > boxHeight && height > 0f) boxHeight / height else 1f
        return min(widthScale, heightScale).coerceIn(0.05f, 1f)
    }

    private fun wrap(text: String, paint: Paint, size: Float, maxWidth: Float): List<String> {
        paint.textSize = size
        val lines = ArrayList<String>()
        text.split('\n').forEach { paragraph ->
            var line = StringBuilder()
            paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                    line = StringBuilder(candidate)
                } else {
                    lines += line.toString()
                    line = StringBuilder(word)
                }
            }
            lines += line.toString()
        }
        return lines.ifEmpty { listOf(text) }
    }

    private fun parseColor(value: String?, fallback: Int): Int {
        if (value.isNullOrBlank()) return fallback
        return runCatching { Color.parseColor(value) }.getOrElse {
            // Some pages send "rgb(0,0,0)".
            val rgb = Regex("""rgba?\((\d+),\s*(\d+),\s*(\d+)""").find(value) ?: return fallback
            val (r, g, b) = rgb.destructured
            Color.rgb(r.toInt(), g.toInt(), b.toInt())
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string" }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

data class PageOverlay(val pageNumber: Int, val bubbles: List<MangaTekOverlay.Bubble>)
