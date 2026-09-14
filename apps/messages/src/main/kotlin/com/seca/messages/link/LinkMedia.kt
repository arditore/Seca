package com.seca.messages.link

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.seca.core.link.message.LinkPayload
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Photos and voice messages sent and received through Seca Link, in this
 * app's own storage.
 *
 * A photo is drawn again before it leaves: smaller, and rid of the location
 * and camera details a picture file carries. Everything then travels in pieces
 * small enough for every relay, each encrypted in its own envelope.
 */
class LinkMedia(context: Context) {

    private val folder = File(context.applicationContext.noBackupFilesDir, FOLDER)
    private val parts = File(folder, PARTS)
    private val resolver = context.applicationContext.contentResolver

    /** Where message [id] with [number] keeps its file; named so no contact can overwrite another's. */
    fun fileOf(number: String, id: String, mime: String): File {
        val name = digest("$number/$id".toByteArray()).toHexString()
        return File(folder, name + if (mime.startsWith("audio/")) ".ogg" else ".webp")
    }

    /** The photo at [uri], resized and re-encoded; null when it cannot be read or stays too large. */
    fun prepare(uri: Uri): ByteArray? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val scale = min(1f, MAX_EDGE.toFloat() / max(info.size.width, info.size.height))
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        var quality = START_QUALITY
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                out.toByteArray()
            }
            quality -= QUALITY_STEP
        } while (bytes.size > TARGET_BYTES && quality >= MIN_QUALITY)
        bitmap.recycle()
        bytes.takeIf { it.size <= MAX_BYTES }
    }.getOrNull()

    fun save(number: String, id: String, mime: String, bytes: ByteArray) {
        folder.mkdirs()
        fileOf(number, id, mime).writeBytes(bytes)
    }

    fun delete(number: String, id: String, mime: String) {
        fileOf(number, id, mime).delete()
    }

    /** The pieces [bytes] travel in, each with the whole file's digest. */
    fun split(id: String, bytes: ByteArray, sentAt: Long, mime: String, expiresInSeconds: Int): List<LinkPayload.MediaPart> {
        val size = LinkPayload.MEDIA_PART_BYTES
        val count = max(1, (bytes.size + size - 1) / size)
        val whole = digest(bytes)
        return List(count) { index ->
            val data = bytes.copyOfRange(index * size, min(bytes.size, (index + 1) * size))
            LinkPayload.MediaPart(id, index, count, sentAt, mime, whole, data, expiresInSeconds)
        }
    }

    /**
     * Keeps one piece received from [number]. Returns true once every piece is
     * in and the file put back together matches its digest; false while pieces
     * are missing, when they do not add up, or for a kind of file Seca does not open.
     */
    fun accept(number: String, part: LinkPayload.MediaPart): Boolean = synchronized(lock) {
        if (part.mime != IMAGE && part.mime != VOICE) return false
        val pending = File(parts, digest("$number/${part.id}".toByteArray()).toHexString()).apply { mkdirs() }
        File(pending, part.index.toString()).writeBytes(part.data)
        val pieces = (0 until part.count).map { File(pending, it.toString()) }
        if (!pieces.all(File::exists)) return false
        val whole = ByteArrayOutputStream().use { out ->
            pieces.forEach { out.write(it.readBytes()) }
            out.toByteArray()
        }
        pending.deleteRecursively()
        forgetStaleParts()
        if (!MessageDigest.isEqual(digest(whole), part.digest)) return false
        save(number, part.id, part.mime, whole)
        true
    }

    /** A file whose last pieces never came is dropped after a week. */
    private fun forgetStaleParts() {
        val limit = System.currentTimeMillis() - STALE_MILLIS
        parts.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
    }

    private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        const val IMAGE = "image/webp"
        const val VOICE = "audio/ogg"

        /** The most a photo or a voice message may weigh once ready to leave. */
        const val MAX_BYTES = LinkPayload.MEDIA_PART_BYTES * LinkPayload.MAX_MEDIA_PARTS

        private const val FOLDER = "link-media"
        private const val PARTS = "parts"
        private const val MAX_EDGE = 1600
        private const val TARGET_BYTES = 400 * 1024
        private const val START_QUALITY = 80
        private const val QUALITY_STEP = 10
        private const val MIN_QUALITY = 40
        private const val STALE_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val lock = Any()
    }
}
