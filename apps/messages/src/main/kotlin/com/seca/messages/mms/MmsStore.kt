package com.seca.messages.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.util.Log
import androidx.core.net.toUri

/**
 * Writes multimedia messages where Android keeps them.
 *
 * A message belongs to the phone, not to this app: it goes into the same tables every other
 * messaging app reads, so switching away from Seca does not take yesterday's photos with it.
 * That is the whole reason to store them properly rather than in a private database.
 */
internal object MmsStore {

    private const val LOG_TAG = "SecaMms"

    /** Android's own numbers for what a row is: the push that announced a message, or the message. */
    private const val TYPE_NOTIFICATION_IND = 130
    private const val TYPE_RETRIEVE_CONF = 132

    /**
     * Records that a message is waiting, before it is fetched.
     *
     * Worth storing on its own: if the fetch fails — no signal, a carrier that is slow, a phone
     * that is switched off — the row is what remembers that something is owed, and what another
     * app would see too.
     */
    fun saveNotification(context: Context, notification: MmsPdu.Notification): Uri? {
        val from = notification.from ?: return null
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadOf(context, from))
            put(Mms.DATE, System.currentTimeMillis() / MILLIS_PER_SECOND)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, TYPE_NOTIFICATION_IND)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            notification.transactionId?.let { put(Mms.TRANSACTION_ID, it) }
            notification.contentLocation?.let { put(Mms.CONTENT_LOCATION, it) }
            notification.subject?.let { put(Mms.SUBJECT, it) }
            put(Mms.MESSAGE_SIZE, notification.size)
            put(Mms.EXPIRY, notification.expiry)
        }
        return insert(context, Mms.CONTENT_URI, values)
    }

    /**
     * Stores a fetched message and everything in it, then takes away the notification row that
     * announced it, so the conversation shows one message rather than a message and its shadow.
     */
    fun saveRetrieved(context: Context, retrieved: MmsPdu.Retrieved): Uri? {
        val from = retrieved.from ?: retrieved.to.firstOrNull() ?: return null
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadOf(context, from))
            put(Mms.DATE, (retrieved.sentAt.takeIf { it > 0 } ?: System.currentTimeMillis()) / MILLIS_PER_SECOND)
            put(Mms.DATE_SENT, retrieved.sentAt / MILLIS_PER_SECOND)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_INBOX)
            put(Mms.MESSAGE_TYPE, TYPE_RETRIEVE_CONF)
            put(Mms.READ, 0)
            put(Mms.SEEN, 0)
            put(Mms.MMS_VERSION, MMS_VERSION_1_2)
            retrieved.messageId?.let { put(Mms.MESSAGE_ID, it) }
            retrieved.transactionId?.let { put(Mms.TRANSACTION_ID, it) }
            retrieved.subject?.let { put(Mms.SUBJECT, it) }
        }
        val message = insert(context, Mms.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(message)
        retrieved.parts.forEachIndexed { index, part -> savePart(context, id, index, part) }
        saveAddress(context, id, from, ADDRESS_FROM)
        retrieved.to.forEach { saveAddress(context, id, it, ADDRESS_TO) }
        retrieved.transactionId?.let { forgetNotification(context, it) }
        return message
    }

    /**
     * Writes a message on its way out, before the carrier has taken it.
     *
     * Stored first so the conversation shows it at once, and so that a send which never comes
     * back leaves something behind rather than a message that was typed and then vanished. It
     * sits in the outbox until the carrier answers.
     */
    fun saveOutgoing(context: Context, outgoing: Outgoing, transactionId: String): Uri? {
        val to = outgoing.to.firstOrNull() ?: return null
        val values = ContentValues().apply {
            put(Mms.THREAD_ID, threadOf(context, to))
            put(Mms.DATE, System.currentTimeMillis() / MILLIS_PER_SECOND)
            put(Mms.MESSAGE_BOX, Mms.MESSAGE_BOX_OUTBOX)
            put(Mms.MESSAGE_TYPE, MmsComposer.TYPE_SEND_REQ)
            put(Mms.READ, 1)
            put(Mms.SEEN, 1)
            put(Mms.TRANSACTION_ID, transactionId)
        }
        val message = insert(context, Mms.CONTENT_URI, values) ?: return null
        val id = ContentUris.parseId(message)
        var index = 0
        outgoing.text.takeIf { it.isNotBlank() }?.let { words ->
            savePart(context, id, index++, MmsPdu.Part("text/plain", "text.txt", "<text>", words.toByteArray()))
        }
        outgoing.attachments.forEach { savePart(context, id, index++, it) }
        outgoing.to.forEach { saveAddress(context, id, it, ADDRESS_TO) }
        return message
    }

    /** Moves a sent message out of the outbox, into sent or failed. */
    fun markSent(context: Context, message: Uri, sent: Boolean) {
        val values = ContentValues().apply {
            put(Mms.MESSAGE_BOX, if (sent) Mms.MESSAGE_BOX_SENT else Mms.MESSAGE_BOX_FAILED)
        }
        runCatching { context.contentResolver.update(message, values, null, null) }
            .onFailure { Log.w(LOG_TAG, "box not changed", it) }
    }

    /**
     * One piece of a message. Text goes in a column, because that is where every reader looks
     * for it; anything else is written as a file the provider hands back by URI.
     */
    private fun savePart(context: Context, messageId: Long, index: Int, part: MmsPdu.Part) {
        val uri = "${Mms.CONTENT_URI}/$messageId/part".toUri()
        val values = ContentValues().apply {
            put("mid", messageId)
            put("seq", index)
            put("ct", part.contentType)
            part.contentId?.let { put("cid", it) }
            part.name?.let {
                put("name", it)
                put("cl", it)
            }
            if (part.isText) {
                put("chset", UTF_8_CHARSET)
                put("text", part.text.orEmpty())
            }
        }
        val saved = insert(context, uri, values) ?: return
        if (part.isText || part.data.isEmpty()) return
        runCatching {
            context.contentResolver.openOutputStream(saved)?.use { it.write(part.data) }
        }.onFailure { Log.w(LOG_TAG, "part not written", it) }
    }

    private fun saveAddress(context: Context, messageId: Long, address: String, type: Int) {
        val values = ContentValues().apply {
            put("msg_id", messageId)
            put("address", address)
            put("type", type)
            put("charset", UTF_8_CHARSET)
        }
        insert(context, "${Mms.CONTENT_URI}/$messageId/addr".toUri(), values)
    }

    /** The push row for a message that has now arrived in full. */
    private fun forgetNotification(context: Context, transactionId: String) {
        runCatching {
            context.contentResolver.delete(
                Mms.CONTENT_URI,
                "${Mms.TRANSACTION_ID} = ? AND ${Mms.MESSAGE_TYPE} = ?",
                arrayOf(transactionId, TYPE_NOTIFICATION_IND.toString()),
            )
        }.onFailure { Log.w(LOG_TAG, "notification row kept", it) }
    }

    /** The conversation a message belongs to, created if this is the first word from that number. */
    fun threadOf(context: Context, address: String): Long =
        runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrDefault(0L)

    private fun insert(context: Context, uri: Uri, values: ContentValues): Uri? = runCatching {
        context.contentResolver.insert(uri, values)
    }.onFailure { Log.w(LOG_TAG, "not stored in $uri", it) }.getOrNull()

    private const val MILLIS_PER_SECOND = 1000L
    private const val MMS_VERSION_1_2 = 0x12
    private const val UTF_8_CHARSET = 106
    private const val ADDRESS_FROM = 137
    private const val ADDRESS_TO = 151
}
