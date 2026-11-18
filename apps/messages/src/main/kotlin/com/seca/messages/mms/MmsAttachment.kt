package com.seca.messages.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.telephony.SmsManager
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

    suspend fun read(context: Context, uri: Uri, budget: Int): Attached =
        withContext(Dispatchers.IO) {
            val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = nameOf(context, uri)
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            // A picture that moves and already fits is sent exactly as it is. Through the
            // shrinker it would come out as a JPEG of its first frame — the animation thrown
            // away on the way out, as it used to be on the way in.
            if (bytes != null && moving(type) && bytes.size <= budget) {
                return@withContext Attached(MmsPdu.Part(type, name, "<$name>", bytes), null)
            }
            if (type.startsWith("image/")) {
                val shrunk = shrink(context, uri, budget)
                    ?: return@withContext Attached(null, TooLarge(name, bytes?.size ?: 0))
                return@withContext Attached(
                    MmsPdu.Part("image/jpeg", name.withExtension("jpg"), "<$name>", shrunk),
                    null,
                )
            }
            if (bytes == null) return@withContext Attached(null, TooLarge(name, 0))
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

    /**
     * Whether a picture is one that moves.
     *
     * A WebP may be either and nothing but its bytes says which, so it is asked rather than
     * assumed: a still WebP is better off through the shrinker, where it will fit.
     */
    private fun moving(type: String): Boolean = type == "image/gif"

    /** What the file is called, for the part's name and for saying which one was refused. */
    private fun nameOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"

    private fun String.withExtension(extension: String): String =
        if (endsWith(".$extension", ignoreCase = true)) this else "${substringBeforeLast('.')}.$extension"

    /**
     * What one message may carry on this phone, as the carrier says.
     *
     * Asked rather than assumed. The cap was a constant below the smallest in common use, which
     * is safe everywhere and wrong almost everywhere: a carrier allowing a megabyte had its
     * subscribers told a 650 KB file was too large, by their own phone, before anything was
     * sent. Android keeps each carrier's figure in the MMS configuration and `SmsManager` hands
     * it over without a permission.
     *
     * [FALLBACK] is kept for a phone that answers nothing, or answers something absurd, and the
     * headroom is taken off whatever comes back: the headers, the words and the base64 the
     * encoding adds all take room on top of the picture, and a message refused for being a few
     * bytes over teaches the owner nothing.
     */
    fun budgetOf(context: Context): Int {
        val said = runCatching {
            context.getSystemService(SmsManager::class.java)
                ?.carrierConfigValues
                ?.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, 0)
                ?: 0
        }.getOrDefault(0)
        // A figure is trusted even when it is smaller than the old constant: a carrier that
        // says 200 KB means it, and sending 280 KB in the face of that only moves the refusal
        // from this phone to its network, where it arrives as a message that failed for no
        // stated reason.
        if (said > 0) return (said * HEADROOM).toInt()
        return FALLBACK
    }

    /** What to allow when the phone will not say: below the smallest cap in common use. */
    const val FALLBACK: Int = 280 * 1024

    /** Room left over the attachment for the headers, the words, and what the encoding adds. */
    private const val HEADROOM = 0.85f

    private val EDGES = listOf(1600, 1200, 900, 640)
    private val QUALITIES = listOf(80, 65, 50)
}

/** A picked file, as the screen hands it over. */
internal fun String.asUri(): Uri = toUri()
