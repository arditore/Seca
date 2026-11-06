package com.seca.messages.sms

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import com.seca.messages.R
import com.seca.messages.mms.MmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** How many messages a search shows at most. */
private const val SEARCH_LIMIT = 50

/** One conversation: the latest message with a number, and how many are still unread. */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val unread: Int,
    /** Whether the latest message is one the owner sent. */
    val outgoing: Boolean,
)

enum class MessageStatus { Received, Sending, Sent, Delivered, Read, Failed }

/** What a conversation says of itself, in place of a message: it became a Seca Link one, or stopped being one. */
enum class ConversationNotice { LinkStarted, LinkStopped }

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val status: MessageStatus,
    /** Travelled encrypted through Seca Link rather than by SMS. */
    val encrypted: Boolean = false,
    /** The id both phones know a Seca Link message by; null for an SMS. */
    val linkId: String? = null,
    /** The photo a Seca Link message carries, as a file on this phone; null for text. */
    val image: String? = null,
    /** The voice message a message carries: a file for Seca Link, a part for a multimedia one. */
    val audio: String? = null,
    /** The type of something attached that is neither a picture nor a sound: a card, a document. */
    val attachment: String? = null,
    /** The Seca Link id of the message this one answers. */
    val replyTo: String? = null,
    val myReaction: String? = null,
    val theirReaction: String? = null,
    /** When the message goes from both phones, in milliseconds; 0 keeps it. */
    val expiresAt: Long = 0L,
    /** Not a message but a notice: what happened to the conversation itself, at the moment it happened. */
    val notice: ConversationNotice? = null,
) {
    val outgoing: Boolean get() = status != MessageStatus.Received
}

/** A message as a backup carries it. */
data class BackupMessage(
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val type: Int,
    val read: Boolean,
)

/**
 * The phone's text messages, as Android keeps them for every messaging app.
 *
 * Anyone with the permission can read them; only the default messaging app
 * may write, which it must do itself for what it receives and sends. Nothing
 * is copied anywhere else.
 */
class MessagesRepository(private val context: Context) {

    private val resolver = context.contentResolver

    /** Every conversation, most recent first. */
    suspend fun conversations(): List<Conversation> = withContext(Dispatchers.IO) {
        val latest = LinkedHashMap<Long, Conversation>()
        val unread = HashMap<Long, Int>()
        resolver.query(
            Sms.CONTENT_URI,
            arrayOf(Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.READ),
            null,
            null,
            "${Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val thread = c.getLong(0)
                val type = c.getInt(4)
                if (type == Sms.MESSAGE_TYPE_INBOX && c.getInt(5) == 0) unread[thread] = (unread[thread] ?: 0) + 1
                if (thread !in latest) {
                    latest[thread] = Conversation(
                        threadId = thread,
                        address = c.getString(1).orEmpty(),
                        snippet = c.getString(2).orEmpty(),
                        date = c.getLong(3),
                        unread = 0,
                        outgoing = type != Sms.MESSAGE_TYPE_INBOX,
                    )
                }
            }
        }
        // Picture messages are kept in their own table, and a conversation's newest message may
        // well be one of them: without this, the list would show a text from last week under a
        // photo that arrived this morning, or no conversation at all where only photos were sent.
        val pictures = MmsReader.latestPerThread(resolver)
        val picturesUnread = MmsReader.unreadPerThread(resolver)
        pictures.forEach { (thread, message) ->
            val text = latest[thread]
            if (text == null || message.date > text.date) {
                latest[thread] = Conversation(
                    threadId = thread,
                    address = message.address.ifEmpty { text?.address.orEmpty() },
                    snippet = message.body.ifBlank { context.getString(R.string.photo) },
                    date = message.date,
                    unread = 0,
                    outgoing = message.outgoing,
                )
            }
        }
        latest.values
            .map { it.copy(unread = (unread[it.threadId] ?: 0) + (picturesUnread[it.threadId] ?: 0)) }
            .sortedByDescending(Conversation::date)
    }

    /** The messages of one conversation, oldest first, as a chat reads. */
    suspend fun messages(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        val texts = readMessages("${Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Sms.DATE} ASC")
        val pictures = MmsReader.messages(resolver, threadId)
        if (pictures.isEmpty()) texts else (texts + pictures).sortedBy(Message::date)
    }

    /** Messages containing [text], newest first, across every conversation. */
    suspend fun search(text: String, limit: Int = SEARCH_LIMIT): List<Message> = withContext(Dispatchers.IO) {
        // Percent signs and underscores typed by the owner are searched for as they are.
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        readMessages("${Sms.BODY} LIKE ? ESCAPE '\\'", arrayOf("%$escaped%"), "${Sms.DATE} DESC LIMIT $limit")
    }

    /** Messages received before [before], newest first, for erasing old verification codes. */
    suspend fun receivedBefore(before: Long): List<Message> = withContext(Dispatchers.IO) {
        readMessages(
            "${Sms.TYPE} = ? AND ${Sms.DATE} < ?",
            arrayOf(Sms.MESSAGE_TYPE_INBOX.toString(), before.toString()),
            "${Sms.DATE} DESC",
        )
    }

    private fun readMessages(selection: String, args: Array<String>, order: String): List<Message> =
        resolver.query(
            Sms.CONTENT_URI,
            arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.STATUS),
            selection,
            args,
            order,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Message(
                            id = c.getLong(0),
                            threadId = c.getLong(1),
                            address = c.getString(2).orEmpty(),
                            body = c.getString(3).orEmpty(),
                            date = c.getLong(4),
                            status = statusOf(c.getInt(5), c.getInt(6)),
                        ),
                    )
                }
            }
        }.orEmpty()

    /** Every message on the phone, for a backup. */
    suspend fun allForBackup(): List<BackupMessage> = withContext(Dispatchers.IO) {
        resolver.query(
            Sms.CONTENT_URI,
            arrayOf(Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.DATE_SENT, Sms.TYPE, Sms.READ),
            null,
            null,
            "${Sms.DATE} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        BackupMessage(
                            address = c.getString(0).orEmpty(),
                            body = c.getString(1).orEmpty(),
                            date = c.getLong(2),
                            dateSent = c.getLong(3),
                            type = c.getInt(4),
                            read = c.getInt(5) == 1,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /**
     * Puts messages from a backup back, skipping those already on the phone.
     * Only the default messaging app may write them. Returns how many came back.
     */
    suspend fun restore(messages: List<BackupMessage>): Int = withContext(Dispatchers.IO) {
        val present = HashSet<String>()
        resolver.query(Sms.CONTENT_URI, arrayOf(Sms.ADDRESS, Sms.DATE, Sms.BODY), null, null, null)?.use { c ->
            while (c.moveToNext()) present += "${c.getString(0)}|${c.getLong(1)}|${c.getString(2)}"
        }
        var added = 0
        messages.forEach { message ->
            val key = "${message.address}|${message.date}|${message.body}"
            if (key in present) return@forEach
            val values = ContentValues().apply {
                put(Sms.ADDRESS, message.address)
                put(Sms.BODY, message.body)
                put(Sms.DATE, message.date)
                put(Sms.DATE_SENT, message.dateSent)
                put(Sms.TYPE, message.type)
                put(Sms.READ, if (message.read) 1 else 0)
                put(Sms.SEEN, 1)
            }
            if (runCatching { resolver.insert(Sms.CONTENT_URI, values) }.getOrNull() != null) {
                added++
                present += key
            }
        }
        added
    }

    /** The conversation with [address], created when there is none yet. */
    suspend fun threadIdFor(address: String): Long? = withContext(Dispatchers.IO) {
        runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull()
    }

    suspend fun markRead(threadId: Long) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(Sms.READ, 1)
                put(Sms.SEEN, 1)
            }
            runCatching {
                resolver.update(Sms.CONTENT_URI, values, "${Sms.THREAD_ID} = ? AND ${Sms.READ} = 0", arrayOf(threadId.toString()))
            }
            // The same for the picture messages, or the conversation would keep claiming unread
            // ones the owner has just been looking at.
            runCatching {
                resolver.update(Mms.CONTENT_URI, values, "${Mms.THREAD_ID} = ? AND ${Mms.READ} = 0", arrayOf(threadId.toString()))
            }
        }
    }

    suspend fun deleteMessage(id: Long) {
        withContext(Dispatchers.IO) {
            val uri =
                if (MmsReader.isMms(id)) MmsReader.uriOf(id)
                else Uri.withAppendedPath(Sms.CONTENT_URI, id.toString())
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    suspend fun deleteConversation(threadId: Long) {
        withContext(Dispatchers.IO) {
            runCatching { resolver.delete(Sms.CONTENT_URI, "${Sms.THREAD_ID} = ?", arrayOf(threadId.toString())) }
            // Deleting a conversation means all of it: the picture messages go as well, or they
            // would come back on their own the moment the list was read again.
            runCatching { resolver.delete(Mms.CONTENT_URI, "${Mms.THREAD_ID} = ?", arrayOf(threadId.toString())) }
        }
    }

    /** Stores a message that just arrived, unread. Null when this app is not the default one. */
    /** Marks one stored message read, such as a Seca Link notice nobody needs to open. */
    fun markStoredRead(uri: Uri) {
        val values = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        runCatching { resolver.update(uri, values, null, null) }
    }

    fun storeIncoming(address: String, body: String, sentAt: Long, subscriptionId: Int): Uri? {
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.DATE_SENT, sentAt)
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
            put(Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        return runCatching { resolver.insert(Sms.Inbox.CONTENT_URI, values) }.getOrNull()
    }

    /** Stores a message about to be sent, in the outbox until the network confirms it. */
    fun storeOutgoing(address: String, body: String): Uri? {
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        return runCatching { resolver.insert(Sms.Outbox.CONTENT_URI, values) }.getOrNull()
    }

    /** Moves a sent message to the sent folder, or marks it failed. */
    /**
     * Writes what the network said about a message arriving.
     *
     * The column is Android's own, so a report Seca receives is visible to every other
     * messaging app on the phone, as a message Seca sent is.
     */
    fun setDeliveryStatus(uri: Uri, status: Int) {
        val values = ContentValues().apply { put(Sms.STATUS, status) }
        runCatching { resolver.update(uri, values, null, null) }
    }

    fun setSent(uri: Uri, sent: Boolean) {
        val values = ContentValues().apply {
            put(Sms.TYPE, if (sent) Sms.MESSAGE_TYPE_SENT else Sms.MESSAGE_TYPE_FAILED)
        }
        runCatching { resolver.update(uri, values, null, null) }
    }

    /** Emits whenever a message is added, changed or removed, by any app. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    /**
     * What a message is, from how it was filed and what the network said afterwards.
     *
     * [delivery] is the carrier's word, and most of the time there is none: a carrier that
     * sends no report, or an owner who never asked for one, leaves STATUS_NONE behind, and
     * the message stays at "Sent", which is all anyone here actually knows.
     */
    private fun statusOf(type: Int, delivery: Int = Sms.STATUS_NONE): MessageStatus = when {
        type == Sms.MESSAGE_TYPE_INBOX -> MessageStatus.Received
        type == Sms.MESSAGE_TYPE_FAILED -> MessageStatus.Failed
        type == Sms.MESSAGE_TYPE_OUTBOX || type == Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.Sending
        // The network gave up on a message the phone did send: that is a failure to show.
        delivery == Sms.STATUS_FAILED -> MessageStatus.Failed
        delivery == Sms.STATUS_COMPLETE -> MessageStatus.Delivered
        else -> MessageStatus.Sent
    }
}
