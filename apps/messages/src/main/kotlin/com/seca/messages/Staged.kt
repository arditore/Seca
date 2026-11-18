package com.seca.messages

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns

/**
 * Something picked and waiting in the composer, until the owner presses send (#69).
 *
 * Picking used to send, immediately, and the picker closing was the message leaving. Holding it
 * here instead is what lets a photo carry a caption written afterwards, a second one be added
 * beside it, and the wrong one be taken back out.
 *
 * [isCard] because a contact is the one kind that is not a file to be read and shrunk: it is a
 * row in the contacts provider that a vCard is made from at the moment of sending.
 */
internal data class Staged(val uri: Uri, val name: String, val isCard: Boolean = false)

/** What to call a picked file in the composer, which is what the owner recognises it by. */
internal fun stagedFile(context: Context, uri: Uri, isCard: Boolean = false): Staged {
    val column = if (isCard) ContactsContract.Contacts.DISPLAY_NAME else OpenableColumns.DISPLAY_NAME
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: ""
    return Staged(uri, name, isCard)
}
