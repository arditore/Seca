package com.seca.messages.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/** Why something could not be attached, in the owner's words. */
internal class TooLarge(val name: String, val bytes: Int)

/** What came back from reading a picked file: a part to send, or the reason it cannot be sent. */
internal class Attached(val part: MmsPdu.Part?, val refused: TooLarge?)

/**
 * Turns a file the owner picked into a part a carrier will carry.
 *
 * Carriers cap a multimedia message, usually between 300 KB and 1 MB, and the cap is enforced by
 * rejecting the message rather than by warning anyone. A photograph straight from a modern
 * camera is ten times that, so pictures are shrunk until they fit. Anything that is not a
 * picture is sent as it is or refused out loud: a video cannot be re-encoded here without
 * turning a message into a minute of work and a hot phone, and quietly sending a broken one
 * would be worse.
 */
internal object MmsAttachment {

    suspend fun read(context: Context, uri: Uri, budget: Int = BUDGET): Attached =
        withContext(Dispatchers.IO) {
            val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = nameOf(context, uri)
            if (type.startsWith("image/")) {
                val shrunk = shrink(context, uri, budget)
                    ?: return@withContext Attached(null, TooLarge(name, 0))
                return@withContext Attached(
                    MmsPdu.Part("image/jpeg", name.withExtension("jpg"), "<$name>", shrunk),
                    null,
                )
            }
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext Attached(null, TooLarge(name, 0))
            if (bytes.size > budget) {
                Attached(null, TooLarge(name, bytes.size))
            } else {
                Attached(MmsPdu.Part(type, name, "<$name>", bytes), null)
            }
        }

    /**
     * A picture small enough to send, or null when it cannot be read at all.
     *
     * Tried at falling sizes and qualities rather than computed: the size of a JPEG cannot be
     * known before it is written, and a picture of a page of text compresses nothing like a
     * picture of a face.
     */
    private fun shrink(context: Context, uri: Uri, budget: Int): ByteArray? {
        EDGES.forEach { edge ->
            QUALITIES.forEach { quality ->
                val bytes = encode(context, uri, edge, quality) ?: return null
                if (bytes.size <= budget) return bytes
            }
        }
        // Smallest it goes: sent anyway, since a carrier that refuses it says so, and a picture
        // of a whiteboard at the lowest setting is still what the owner asked to send.
        return encode(context, uri, EDGES.last(), QUALITIES.last())
    }

    private fun encode(context: Context, uri: Uri, edge: Int, quality: Int): ByteArray? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = min(1f, edge.toFloat() / max(info.size.width, info.size.height))
            if (scale < 1f) {
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).toInt()),
                    max(1, (info.size.height * scale).toInt()),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        ByteArrayOutputStream().use { out ->
            decoded.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** What the file is called, for the part's name and for saying which one was refused. */
    private fun nameOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    private fun String.withExtension(extension: String): String =
        if (endsWith(".$extension", ignoreCase = true)) this else "${substringBeforeLast('.')}.$extension"

    /**
     * What one message may carry.
     *
     * Below the smallest cap in common use rather than at it: the headers, the words and the
     * encoding all take room on top of the picture, and a message refused for being a few bytes
     * over teaches the owner nothing.
     */
    const val BUDGET: Int = 280 * 1024

    private val EDGES = listOf(1600, 1200, 900, 640)
    private val QUALITIES = listOf(80, 65, 50)
}

/** A picked file, as the screen hands it over. */
internal fun String.asUri(): Uri = toUri()
