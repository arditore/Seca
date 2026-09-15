package com.seca.messages

import com.seca.messages.sms.ConversationNotice
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkNoticeTest {

    private fun sms(id: Long, date: Long) =
        Message(id = id, threadId = THREAD, address = ADDRESS, body = "sms $id", date = date, status = MessageStatus.Received)

    @Test
    fun `marks the moment the conversation became a Seca Link one`() {
        val messages = listOf(sms(1, 1_000), sms(2, 3_000))
        val shown = LinkConversations.withLinkNotices(messages, connectedAt = 2_000, leftAt = 0, threadId = THREAD, address = ADDRESS)
        assertEquals(listOf(1_000L, 2_000L, 3_000L), shown.map { it.date })
        assertEquals(ConversationNotice.LinkStarted, shown[1].notice)
    }

    @Test
    fun `marks the moment the contact stopped having Seca Link`() {
        val messages = listOf(sms(1, 1_000), sms(2, 5_000))
        val shown = LinkConversations.withLinkNotices(messages, connectedAt = 2_000, leftAt = 4_000, threadId = THREAD, address = ADDRESS)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 5_000L), shown.map { it.date })
        assertEquals(listOf(ConversationNotice.LinkStarted, ConversationNotice.LinkStopped), shown.mapNotNull { it.notice })
        assertEquals(shown.size, shown.map { it.id }.distinct().size)
    }

    @Test
    fun `shows no notice before a session opened`() {
        val messages = listOf(sms(1, 1_000))
        assertEquals(messages, LinkConversations.withLinkNotices(messages, connectedAt = 0, leftAt = 0, threadId = THREAD, address = ADDRESS))
    }

    private companion object {
        const val THREAD = 7L
        const val ADDRESS = "+33612345678"
    }
}
