package com.seca.messages.sms

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Conversation avatars as files this app hands out by content URI.
 *
 * Android refuses a bitmap as the icon of the long-lived shortcut a conversation
 * notification needs — "Bitmaps are not allowed in long-lived shortcuts" — and
 * without that shortcut Android draws the notification as a plain one, with the
 * app's icon in place of the contact. The files stay in this app's own cache,
 * and only whoever Android grants the URI to can read them.
 */
internal object AvatarFiles {

    private const val DIRECTORY = "avatars"
    private const val AUTHORITY = ".avatars"
    private const val QUALITY = 100
    private const val RADIX = 16

    /** The avatar of the conversation with [key], as a content URI; null when it cannot be written. */
    fun uriOf(context: Context, key: String, avatar: Bitmap): Uri? = runCatching {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val bytes = ByteArrayOutputStream().also { avatar.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }.toByteArray()
        val prefix = hex(key.hashCode()) + "-"
        val name = prefix + hex(bytes.contentHashCode()) + ".png"
        val file = File(directory, name)
        if (!file.exists()) {
            file.writeBytes(bytes)
            // A new photo gets a new name, so nothing keeps showing the old one, which is then dropped.
            directory.listFiles { candidate -> candidate.name.startsWith(prefix) && candidate.name != name }?.forEach { it.delete() }
        }
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY, file)
    }.getOrNull()

    private fun hex(value: Int): String = value.toUInt().toString(RADIX)
}
