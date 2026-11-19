package com.seca.messages.sms

import android.provider.Telephony.Sms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * How a message is filed once a part has reported back.
 *
 * A text of more than 160 characters, or of more than 70 with an accent anywhere in it, leaves
 * as several parts. Each part answers on its own and in no fixed order, so this decision is
 * taken once per part on the same message. Taking whichever answer came last meant one part
 * failing was undone by the next part succeeding, and a message that never arrived whole sat
 * there looking sent (#75).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SentMarkTest {

    @Test
    fun `a part that went marks the message sent`() {
        assertEquals(Sms.MESSAGE_TYPE_SENT, MessagesRepository.sentMark(Sms.MESSAGE_TYPE_OUTBOX, sent = true))
    }

    @Test
    fun `a part that did not go marks the message failed`() {
        assertEquals(Sms.MESSAGE_TYPE_FAILED, MessagesRepository.sentMark(Sms.MESSAGE_TYPE_OUTBOX, sent = false))
    }

    @Test
    fun `a part succeeding after one failed leaves the failure standing`() {
        assertNull(MessagesRepository.sentMark(Sms.MESSAGE_TYPE_FAILED, sent = true))
    }

    @Test
    fun `a part failing after one succeeded fails the message`() {
        assertEquals(Sms.MESSAGE_TYPE_FAILED, MessagesRepository.sentMark(Sms.MESSAGE_TYPE_SENT, sent = false))
    }

    @Test
    fun `with no way to read how it is filed, the answer at hand is taken`() {
        assertEquals(Sms.MESSAGE_TYPE_SENT, MessagesRepository.sentMark(null, sent = true))
        assertEquals(Sms.MESSAGE_TYPE_FAILED, MessagesRepository.sentMark(null, sent = false))
    }
}
