package com.seca.messages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * A picture behind one conversation (#47).
 *
 * The picture is copied into this app's own storage rather than remembered as a link to the
 * gallery. A link would need a permission that has to outlive the picker, and the picture it
 * points at belongs to another app, which may move or delete it: a conversation whose
 * background vanished one day would be a puzzle nobody could solve. A copy is a few hundred
 * kilobytes and answers both.
 *
 * It is scaled down on the way in, to about a tall phone screen. A modern camera picture is
 * twelve megapixels, which is four times the pixels a background can show and sixty times the
 * bytes it needs.
 */
object ConversationBackground {

    /** The picture behind this conversation, or null where there is none. */
    fun of(context: Context, threadId: Long): File? =
        fileFor(context, threadId).takeIf { it.isFile && it.length() > 0 }

    /** Copies [uri] in, scaled down, and returns the file it landed in, or null if it could not be read. */
    fun set(context: Context, threadId: Long, uri: Uri): File? {
        val picture = decodeScaled(context, uri) ?: return null
        val file = fileFor(context, threadId)
        val written = runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        }.isSuccess
        picture.recycle()
        if (!written) {
            file.delete()
            return null
        }
        return file
    }

    /** Takes the picture away, leaving the conversation as every other one looks. */
    fun clear(context: Context, threadId: Long) {
        runCatching { fileFor(context, threadId).delete() }
    }

    /** Every picture kept, so a conversation deleted elsewhere does not leave one behind. */
    fun forget(context: Context, threadId: Long) = clear(context, threadId)

    private fun fileFor(context: Context, threadId: Long): File =
        File(File(context.filesDir, FOLDER), "$threadId.jpg")

    /**
     * Reads [uri] at about the size a screen can show.
     *
     * Measured first with `inJustDecodeBounds`, which reads the header alone, so a picture too
     * large to hold in memory is never held in memory.
     */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > WIDEST) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private const val FOLDER = "backgrounds"

    /** About a tall phone screen; beyond this a background is bytes nobody sees. */
    private const val WIDEST = 1600

    private const val QUALITY = 88
}
