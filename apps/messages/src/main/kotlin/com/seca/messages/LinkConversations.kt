package com.seca.messages

import android.content.Context
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.link.SecaLink
import com.seca.core.link.message.LinkPayload
import com.seca.messages.link.LinkMessage
import com.seca.messages.link.LinkMessages
import com.seca.messages.link.LinkService
import com.seca.messages.link.LinkStatus
import com.seca.messages.sms.Conversation
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
import com.seca.messages.sms.MessagesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
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
    private val numbers = PhoneNumbers(PhoneNumbers.detectRegion(appContext))

    private fun keyOf(address: String): String? = numbers.toE164(address)

    /** Starts or stops listening to the relays, with Seca Link itself. */
    fun listen(on: Boolean) {
        if (on) LinkService.start(appContext) else LinkService.stop(appContext)
    }

    /** Emits when SMS or Seca Link messages change. */
    fun changes(repository: MessagesRepository): Flow<Unit> = merge(repository.changes(), LinkMessages.changes)

    /** A conversation's messages, SMS and Seca Link together in time order, reloaded whenever either changes. */
    fun conversation(threadId: Long, address: String, repository: MessagesRepository): Flow<List<Message>> = flow {
        emit(load(threadId, address, repository))
        changes(repository).conflate().collect { emit(load(threadId, address, repository)) }
    }.flowOn(Dispatchers.IO)

    private suspend fun load(threadId: Long, address: String, repository: MessagesRepository): List<Message> {
        val sms = if (threadId < 0) emptyList() else repository.messages(threadId)
        val encrypted = keyOf(address)?.let(store::forNumber).orEmpty().map { it.toMessage(threadId, address) }
        return (sms + encrypted).sortedBy { it.date }
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
                conversation.copy(snippet = newest.body, date = newest.date, outgoing = newest.outgoing)
            } else {
                conversation
            }
            merged.copy(unread = merged.unread + (unread[number] ?: 0))
        }.sortedByDescending { it.date }
    }

    /** Sends [text] encrypted through Seca Link; false when the contact is not connected, and it must go by SMS. */
    suspend fun sendText(address: String, text: String): Boolean {
        val number = keyOf(address) ?: return false
        val body = text.trim()
        if (body.isEmpty() || !link.canSend(number)) return false
        val message = LinkMessage(UUID.randomUUID().toString(), number, body, System.currentTimeMillis(), LinkStatus.Sending, read = true)
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
        val sent = link.send(message.number, LinkPayload.Text(message.id, message.body, message.date))
        store.setStatus(listOf(message.id), if (sent == SecaLink.Sent.Published) LinkStatus.Sent else LinkStatus.Failed)
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

    fun delete(id: String) = store.delete(id)

    fun deleteConversation(address: String) {
        keyOf(address)?.let(store::deleteConversation)
    }

    private fun LinkMessage.toMessage(threadId: Long, address: String) = Message(
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
    )

    private companion object {
        const val TYPING_EVERY_MILLIS = 4_000L
        val lastTyping = ConcurrentHashMap<String, Long>()
    }
}
