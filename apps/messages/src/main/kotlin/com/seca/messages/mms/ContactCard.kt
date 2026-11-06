package com.seca.messages.mms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a contact the owner picked into a card any phone can open.
 *
 * The card is not written here. Android already holds every contact as a vCard, and asking it
 * for that one rather than rebuilding it means the fields nobody thinks about — a second
 * address, a nickname, a company — travel too, and that the phone at the other end is handed
 * exactly the format its own contacts app reads.
 *
 * Nothing is read beyond the contact that was picked: the system's own picker hands back one
 * row, and that row's lookup key is the only thing asked for.
 */
internal object ContactCard {

    /** The card to attach, or null when the contact could not be read or is somehow enormous. */
    suspend fun read(context: Context, picked: Uri): MmsPdu.Part? = withContext(Dispatchers.IO) {
        val found = details(context, picked) ?: return@withContext null
        val (lookup, name) = found
        val card = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookup)
        val bytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(card, "r")?.use { descriptor ->
                descriptor.createInputStream().use { it.readBytes() }
            }
        }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty() || bytes.size > MmsAttachment.BUDGET) return@withContext null
        val file = "${fileName(name)}.vcf"
        MmsPdu.Part(CONTENT_TYPE, file, "<$file>", bytes)
    }

    private fun details(context: Context, picked: Uri): Pair<String, String>? = runCatching {
        context.contentResolver.query(
            picked,
            arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) to (it.getString(1) ?: "") else null }
    }.getOrNull()

    /**
     * A name the receiving phone can write to a disk.
     *
     * Someone called "Marie / Travail" would otherwise hand the other phone a file name with a
     * path separator in it, and what happens then is the other phone's business, not ours.
     */
    private fun fileName(name: String): String = name
        .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        .trim()
        .take(NAME_LIMIT)
        .ifBlank { "contact" }

    /** What phones have always sent a contact as; `text/vcard` is the newer name and is read less widely. */
    private const val CONTENT_TYPE = "text/x-vcard"
    private const val NAME_LIMIT = 40
}
