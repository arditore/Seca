package com.seca.messages.mms

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands a part of a multimedia message to whichever app can open it.
 *
 * The part lives in the telephony store, where another app has no right to read it, so it is
 * copied into one folder of this app's cache and handed over as a file this app grants access
 * to — for that one app, and for as long as it is open. A contact card goes to the contacts
 * app, which reads every field of it; anything else goes wherever the owner chooses.
 */
internal object SharedPart {

    /**
     * The bytes of a part, read from the store.
     *
     * Two ways, because the store keeps parts two ways. Anything binary is a file and opens as
     * a stream; anything the store decided was text — a contact card among them — lives in a
     * column of the row itself, and asking for a stream of it gets nothing at all.
     */
    suspend fun bytes(context: Context, at: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(at.toUri())?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: column(context, at, "text")?.toByteArray(Charsets.UTF_8)
    }

    /** What the part is called, for naming the copy and for saying what arrived. */
    suspend fun nameOf(context: Context, at: String): String? = withContext(Dispatchers.IO) {
        column(context, at, "name") ?: column(context, at, "cl")
    }

    private fun column(context: Context, at: String, name: String): String? = runCatching {
        context.contentResolver.query(at.toUri(), arrayOf(name), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.takeIf { value -> value.isNotBlank() } else null
        }
    }.getOrNull()

    /**
     * Opens the part in another app. False when nothing on the phone can read it.
     *
     * The copy is left behind on purpose: an app opened this way reads the file after this
     * returns, and deleting it here would hand over a name with nothing behind it. The folder
     * is this app's own cache, which Android empties when it needs the room.
     */
    suspend fun open(context: Context, at: String, name: String, type: String): Boolean {
        val bytes = bytes(context, at) ?: return false
        val shared = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, FOLDER).apply { mkdirs() }.resolve(safely(name))
                file.writeBytes(bytes)
                FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
            }.getOrNull()
        } ?: return false
        return hand(context, shared, type)
    }

    private fun hand(context: Context, shared: Uri, type: String): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(shared, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /** A name that stays inside the folder it was meant for, whatever the other phone called it. */
    private fun safely(name: String): String = name
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .filter { it.isLetterOrDigit() || it in " -_." }
        .trim()
        .take(NAME_LIMIT)
        .ifBlank { "attachment" }

    /** The one folder the file provider will hand anything out of. */
    private const val FOLDER = "mms"
    private const val NAME_LIMIT = 60
}
