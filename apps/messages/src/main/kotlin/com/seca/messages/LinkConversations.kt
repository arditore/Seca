package com.seca.messages

import android.content.Context
import android.net.Uri
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.link.LinkPeers
import com.seca.core.link.SecaLink
import com.seca.core.link.message.LinkPayload
import com.seca.messages.link.LinkMedia
import com.seca.messages.link.LinkMessage
import com.seca.messages.link.LinkMessages
import com.seca.messages.link.LinkService
import com.seca.messages.link.LinkStatus
import com.seca.messages.link.LinkTimers
import com.seca.messages.sms.Conversation
import com.seca.messages.sms.ConversationNotice
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
import com.seca.messages.sms.MessagesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Seca Link inside the conversations: its messages shown among the SMS, and
 * what the owner writes sent encrypted whenever the contact can receive it.
 */
class LinkConversations(context: Context) {

    private val appContext = context.applicationContext
    private val link = SecaLink(appContext)
    private val store = LinkMessages(appContext)
    private val media = LinkMedia(appContext)
    private val timers = LinkTimers.of(appContext)
    private val numbers = PhoneNumbers(PhoneNumbers.detectRegion(appContext))

    private fun keyOf(address: String): String? = numbers.toE164(address)

    /** Starts or stops listening to the relays, with Seca Link itself. */
    fun listen(on: Boolean) {
        if (on) LinkService.start(appContext) else LinkService.stop(appContext)
    }

    /** Emits when SMS, Seca Link messages, or what Seca Link knows of a contact change. */
    fun changes(repository: MessagesRepository): Flow<Unit> =
        merge(repository.changes(), LinkMessages.changes, LinkPeers.changes)

    /** A conversation's messages, SMS and Seca Link together in time order, reloaded whenever either changes. */
    fun conversation(threadId: Long, address: String, repository: MessagesRepository): Flow<List<Message>> = flow {
        emit(load(threadId, address, repository))
        changes(repository).conflate().collect { emit(load(threadId, address, repository)) }
    }.flowOn(Dispatchers.IO)

    private suspend fun load(threadId: Long, address: String, repository: MessagesRepository): List<Message> {
        // Messages whose time is up never show again.
        sweepExpired()
        val sms = if (threadId < 0) emptyList() else repository.messages(threadId)
        val number = keyOf(address)
        val encrypted = number?.let(store::forNumber).orEmpty().map { it.toMessage(threadId, address) }
        val peer = number?.let { link.peers[it] }
        return withLinkNotices(
            messages = (sms + encrypted).sortedBy { it.date },
            connectedAt = peer?.connectedAt ?: 0L,
            leftAt = peer?.leftAt ?: 0L,
            threadId = threadId,
            address = address,
        )
    }

    /** The conversation list, with each conversation's latest Seca Link message and unread ones counted in. */
    fun withLatest(conversations: List<Conversation>): List<Conversation> {
        val latest = store.latestByNumber()
        if (latest.isEmpty()) return conversations
        val unread = store.unreadCounts()
        return conversations.map { conversation ->
            val number = keyOf(conversation.address) ?: return@map conversation
            val newest = latest[number]
            val merged = if (newest != null && newest.date > conversation.date) {
                conversation.copy(snippet = previewOf(appContext, newest), date = newest.date, outgoing = newest.outgoing)
            } else {
                conversation
            }
            merged.copy(unread = merged.unread + (unread[number] ?: 0))
        }.sortedByDescending { it.date }
    }

    /**
     * Sends [text] encrypted through Seca Link, as an answer to the message
     * [replyTo] when given; false when the contact is not connected, and it must
     * go by SMS.
     */
    suspend fun sendText(address: String, text: String, replyTo: String? = null): Boolean {
        val number = keyOf(address) ?: return false
        val body = text.trim()
        if (body.isEmpty() || !link.canSend(number)) return false
        val now = System.currentTimeMillis()
        val message = LinkMessage(
            id = UUID.randomUUID().toString(),
            number = number,
            body = body,
            date = now,
            status = LinkStatus.Sending,
            read = true,
            replyTo = replyTo,
            expiresAt = LinkTimers.expiryAt(timers[number], now),
        )
        store.add(message)
        deliver(message)
        return true
    }

    /** Sends the photo at [uri] through Seca Link; false when the contact is not connected or the photo cannot be read. */
    suspend fun sendPhoto(address: String, uri: Uri): Boolean {
        val number = keyOf(address) ?: return false
        if (!link.canSend(number)) return false
        val bytes = withContext(Dispatchers.Default) { media.prepare(uri) } ?: return false
        return sendMedia(number, bytes, LinkMedia.IMAGE)
    }

    /** Sends the voice message recorded at [path] through Seca Link; false when it cannot go. */
    suspend fun sendVoice(address: String, path: String): Boolean {
        val number = keyOf(address) ?: return false
        if (!link.canSend(number)) return false
        val bytes = runCatching { File(path).readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() && it.size <= LinkMedia.MAX_BYTES } ?: return false
        return sendMedia(number, bytes, LinkMedia.VOICE)
    }

    private suspend fun sendMedia(number: String, bytes: ByteArray, mime: String): Boolean {
        val now = System.currentTimeMillis()
        val message = LinkMessage(
            id = UUID.randomUUID().toString(),
            number = number,
            body = "",
            date = now,
            status = LinkStatus.Sending,
            read = true,
            media = mime,
            expiresAt = LinkTimers.expiryAt(timers[number], now),
        )
        media.save(number, message.id, mime, bytes)
        store.add(message)
        deliver(message)
        return true
    }

    suspend fun retry(id: String) {
        val message = store.byId(id) ?: return
        store.setStatus(listOf(id), LinkStatus.Sending)
        deliver(message)
    }

    private suspend fun deliver(message: LinkMessage) {
        val mime = message.media
        val expiresIn = if (message.expiresAt > 0) ((message.expiresAt - message.date) / MILLIS_PER_SECOND).toInt() else 0
        val sent = if (mime == null) {
            val payload = LinkPayload.Text(message.id, message.body, message.date, message.replyTo, expiresIn)
            link.send(message.number, payload) == SecaLink.Sent.Published
        } else {
            val file = media.fileOf(message.number, message.id, mime)
            // Piece after piece, so a relay that limits bursts still takes them all; the first failure stops it.
            file.exists() && media.split(message.id, file.readBytes(), message.date, mime, expiresIn).all { part ->
                link.send(message.number, part) == SecaLink.Sent.Published
            }
        }
        store.setStatus(listOf(message.id), if (sent) LinkStatus.Sent else LinkStatus.Failed)
    }

    /** Reacts to message [id] with [emoji], or takes the reaction back when it is the same one; the contact sees it too. */
    suspend fun react(id: String, emoji: String) {
        val message = store.byId(id) ?: return
        val chosen = emoji.takeIf { it != message.myReaction }
        store.setMyReaction(id, chosen)
        if (link.canSend(message.number)) link.send(message.number, LinkPayload.Reaction(id, chosen.orEmpty()))
    }

    /** How long the conversation with [address] keeps its new messages, in seconds; 0 keeps them. */
    fun timerOf(address: String): Int = keyOf(address)?.let { timers[it] } ?: 0

    /** Sets how long new messages are kept, on this phone and on the contact's. */
    suspend fun setTimer(address: String, seconds: Int) {
        val number = keyOf(address) ?: return
        timers.set(number, seconds)
        if (link.canSend(number)) link.send(number, LinkPayload.ExpiryTimer(seconds))
    }

    /** Lets go of the messages whose time is up, with their photos and recordings. */
    fun sweepExpired() {
        store.deleteExpired().forEach { message -> message.media?.let { media.delete(message.number, message.id, it) } }
    }

    /** Marks the conversation read, and tells the contact so when the owner lets read receipts go. */
    suspend fun markRead(address: String) {
        val number = keyOf(address) ?: return
        val ids = store.markRead(number)
        if (ids.isNotEmpty() && link.settings.readReceipts && link.canSend(number)) link.send(number, LinkPayload.Read(ids))
    }

    /** Tells the contact the owner is writing, when the owner allows it; at most every few seconds. */
    suspend fun typing(address: String) {
        if (!link.settings.typingIndicator) return
        val number = keyOf(address) ?: return
        val now = System.currentTimeMillis()
        if (now - (lastTyping[number] ?: 0L) < TYPING_EVERY_MILLIS || !link.canSend(number)) return
        lastTyping[number] = now
        link.send(number, LinkPayload.Typing)
    }

    fun delete(id: String) {
        store.byId(id)?.let { message -> message.media?.let { media.delete(message.number, message.id, it) } }
        store.delete(id)
    }

    fun deleteConversation(address: String) {
        val number = keyOf(address) ?: return
        store.forNumber(number).forEach { message -> message.media?.let { media.delete(number, message.id, it) } }
        store.deleteConversation(number)
    }

    private fun LinkMessage.toMessage(threadId: Long, address: String): Message {
        val file = media?.let { this@LinkConversations.media.fileOf(number, id, it).takeIf(File::exists)?.absolutePath }
        return Message(
            // Negative, so it never meets an SMS's id, and the same for the same message every time.
            id = -((runCatching { UUID.fromString(id).mostSignificantBits }.getOrElse { id.hashCode().toLong() }) and Long.MAX_VALUE) - 1,
            threadId = threadId,
            address = address,
            body = body,
            date = date,
            status = when (status) {
                LinkStatus.Sending -> MessageStatus.Sending
                LinkStatus.Sent -> MessageStatus.Sent
                LinkStatus.Delivered -> MessageStatus.Delivered
                LinkStatus.Read -> MessageStatus.Read
                LinkStatus.Failed -> MessageStatus.Failed
                LinkStatus.Received -> MessageStatus.Received
            },
            encrypted = true,
            linkId = id,
            image = file?.takeIf { media?.startsWith("image/") == true },
            audio = file?.takeIf { media?.startsWith("audio/") == true },
            replyTo = replyTo,
            myReaction = myReaction,
            theirReaction = theirReaction,
            expiresAt = expiresAt,
        )
    }

    companion object {
        private const val TYPING_EVERY_MILLIS = 4_000L
        private const val MILLIS_PER_SECOND = 1000L

        /** Below every message id, which are the SMS' own and the negatives Seca Link messages take. */
        private const val STARTED_NOTICE_ID = Long.MIN_VALUE
        private const val STOPPED_NOTICE_ID = Long.MIN_VALUE + 1

        private val lastTyping = ConcurrentHashMap<String, Long>()

        /**
         * [messages] with, each at the moment it happened, the notice that the
         * conversation became a Seca Link one and the notice that the contact
         * stopped having Seca Link. Nothing is added where nothing happened.
         */
        fun withLinkNotices(messages: List<Message>, connectedAt: Long, leftAt: Long, threadId: Long, address: String): List<Message> {
            val notices = listOfNotNull(
                noticeAt(STARTED_NOTICE_ID, ConversationNotice.LinkStarted, connectedAt, threadId, address),
                noticeAt(STOPPED_NOTICE_ID, ConversationNotice.LinkStopped, leftAt, threadId, address),
            )
            return if (notices.isEmpty()) messages else (messages + notices).sortedBy { it.date }
        }

        private fun noticeAt(id: Long, notice: ConversationNotice, at: Long, threadId: Long, address: String): Message? =
            if (at <= 0) {
                null
            } else {
                Message(
                    id = id,
                    threadId = threadId,
                    address = address,
                    body = "",
                    date = at,
                    status = MessageStatus.Received,
                    encrypted = true,
                    notice = notice,
                )
            }

        /** A message as a notification or the conversation list shows it. */
        fun previewOf(context: Context, message: LinkMessage): String = when {
            message.media?.startsWith("audio/") == true -> context.getString(R.string.preview_voice)
            message.media != null && message.body.isEmpty() -> context.getString(R.string.preview_photo)
            else -> message.body
        }
    }
}
