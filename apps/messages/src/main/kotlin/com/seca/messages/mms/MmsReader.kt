package com.seca.messages.mms

import android.content.ContentResolver
import android.net.Uri
import android.provider.Telephony.Mms
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
import androidx.core.net.toUri

/**
 * Reads the multimedia messages Android keeps, so a conversation shows them next to its texts.
 *
 * They live in their own tables, and a message is spread across them: the row itself, its
 * addresses, and one row per part — the words in one, the photo in another. What comes out here
 * is the same [Message] an SMS produces, so the conversation screen never has to know which of
 * the two it is drawing.
 */
internal object MmsReader {

    /** The messages of one conversation, oldest first, as [Message] like any other. */
    fun messages(resolver: ContentResolver, threadId: Long): List<Message> =
        rows(resolver, "${Mms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Mms.DATE} ASC")
            .map { it.message(resolver) }

    /** The newest multimedia message of each conversation, for the list of conversations. */
    fun latestPerThread(resolver: ContentResolver): Map<Long, Message> =
        rows(resolver, null, null, "${Mms.DATE} DESC")
            // Newest first, so the first row of a conversation is the one the list shows. Only
            // those are then looked up in full: a phone holds hundreds of these, and each one
            // asks its own questions of the store — the rest would be read for nothing.
            .distinctBy(Row::threadId)
            .associate { it.threadId to it.message(resolver) }

    /** How many multimedia messages each conversation has that nobody has read yet. */
    fun unreadPerThread(resolver: ContentResolver): Map<Long, Int> = runCatching {
        resolver.query(
            Mms.CONTENT_URI,
            arrayOf(Mms.THREAD_ID),
            "${Mms.READ} = 0 AND ${Mms.MESSAGE_BOX} = ? AND ${Mms.MESSAGE_TYPE} != ?",
            arrayOf(Mms.MESSAGE_BOX_INBOX.toString(), TYPE_NOTIFICATION_IND.toString()),
            null,
        )?.use { c ->
            val counts = HashMap<Long, Int>()
            while (c.moveToNext()) {
                val thread = c.getLong(0)
                counts[thread] = (counts[thread] ?: 0) + 1
            }
            counts
        }
    }.getOrNull().orEmpty()

    /** True when [id] is a multimedia message's, so it is not looked for among the texts. */
    fun isMms(id: Long): Boolean = id >= ID_OFFSET

    /** Where a multimedia message lives, for an id this reader handed out. */
    fun uriOf(id: Long): Uri = Uri.withAppendedPath(Mms.CONTENT_URI, (id - ID_OFFSET).toString())

    /** One row of the table, before its parts and its addresses have been looked up. */
    private class Row(val id: Long, val threadId: Long, val date: Long, val box: Int, val subject: String) {

        fun message(resolver: ContentResolver): Message {
            val parts = partsOf(resolver, id)
            return Message(
                // Ids are shared with the SMS table, so a multimedia message is numbered apart:
                // two messages must never answer to the same id.
                id = ID_OFFSET + id,
                threadId = threadId,
                address = addressOf(resolver, id, box).orEmpty(),
                body = parts.text.ifBlank { subject },
                // Dates are in seconds here and in milliseconds everywhere else.
                date = date * MILLIS_PER_SECOND,
                status = statusOf(box),
                image = parts.image,
            )
        }
    }

    private fun rows(
        resolver: ContentResolver,
        selection: String?,
        args: Array<String>?,
        order: String,
    ): List<Row> = runCatching {
        resolver.query(
            Mms.CONTENT_URI,
            arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE, Mms.MESSAGE_BOX, Mms.SUBJECT, Mms.MESSAGE_TYPE),
            selection,
            args,
            order,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    // The row that merely announced a message is not a message: it is shown only
                    // once it has been fetched, and until then the conversation says nothing.
                    if (c.getInt(5) == TYPE_NOTIFICATION_IND) continue
                    add(Row(c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3), c.getString(4).orEmpty()))
                }
            }
        }
    }.getOrNull().orEmpty()

    private class Parts(val text: String, val image: String?)

    private fun partsOf(resolver: ContentResolver, messageId: Long): Parts = runCatching {
        resolver.query(
            PART_URI,
            arrayOf("_id", "ct", "text"),
            "mid = ?",
            arrayOf(messageId.toString()),
            "seq ASC",
        )?.use { c ->
            val words = StringBuilder()
            var image: String? = null
            while (c.moveToNext()) {
                val type = c.getString(1).orEmpty()
                when {
                    type.startsWith("text/") -> c.getString(2)?.takeIf { it.isNotBlank() }?.let {
                        if (words.isNotEmpty()) words.append('\n')
                        words.append(it)
                    }
                    // The presentation part describes a slideshow; it is not something to show.
                    type == SMIL -> Unit
                    image == null && type.startsWith("image/") -> image = "$PART_URI/${c.getLong(0)}"
                }
            }
            Parts(words.toString(), image)
        }
    }.getOrNull() ?: Parts("", null)

    /** Who wrote it, or who it went to, which is the same person in a conversation of two. */
    private fun addressOf(resolver: ContentResolver, messageId: Long, box: Int): String? = runCatching {
        val wanted = if (box == Mms.MESSAGE_BOX_INBOX) ADDRESS_FROM else ADDRESS_TO
        resolver.query(
            "${Mms.CONTENT_URI}/$messageId/$ADDRESSES".toUri(),
            arrayOf("address", "type"),
            null,
            null,
            null,
        )?.use { c ->
            var fallback: String? = null
            while (c.moveToNext()) {
                val address = c.getString(0)?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS } ?: continue
                if (c.getInt(1) == wanted) return@use address
                if (fallback == null) fallback = address
            }
            fallback
        }
    }.getOrNull()

    private fun statusOf(box: Int): MessageStatus = when (box) {
        Mms.MESSAGE_BOX_INBOX -> MessageStatus.Received
        Mms.MESSAGE_BOX_OUTBOX, Mms.MESSAGE_BOX_DRAFTS -> MessageStatus.Sending
        Mms.MESSAGE_BOX_FAILED -> MessageStatus.Failed
        else -> MessageStatus.Sent
    }

    /**
     * Where a multimedia message's ids start, out of the way of the texts.
     *
     * The two kinds live in different tables, each numbering from one, and the conversation
     * screen holds them in one list. Without this, the first text and the first picture message
     * would both be number one, and whichever the screen drew second would replace the other.
     */
    const val ID_OFFSET = 1_000_000_000_000L

    private val PART_URI: Uri = "content://mms/part".toUri()
    private const val ADDRESSES = "addr"
    private const val SMIL = "application/smil"
    private const val MILLIS_PER_SECOND = 1000L
    private const val ADDRESS_FROM = 137
    private const val ADDRESS_TO = 151
    private const val INSERT_ADDRESS = "insert-address-token"
    private const val TYPE_NOTIFICATION_IND = 130
}
