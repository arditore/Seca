package com.seca.messages.sms

import com.seca.core.contacts.NumberCountry
import com.seca.core.contacts.NumberNote
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which senders get a notification per message rather than a conversation.
 *
 * The rule decides whether three one-time codes arrive as three notifications or as one
 * paragraph, so it is worth pinning down: a service has one side, a conversation has two.
 */
class ServiceSenderTest {

    private val france = NumberNote.Country(NumberCountry(region = "FR", displayName = "France", isHome = true))

    @Test
    fun `a sender written in letters is a service`() {
        assertTrue(isServiceSender("AX-ADHAAR-S", known = false, note = null))
        assertTrue(isServiceSender("AMAZON", known = false, note = null))
    }

    @Test
    fun `a short code is a service`() {
        assertTrue(isServiceSender("36180", known = false, note = NumberNote.Short))
    }

    @Test
    fun `someone in the contacts is never a service`() {
        assertFalse(isServiceSender("AX-ADHAAR-S", known = true, note = null))
        assertFalse(isServiceSender("36180", known = true, note = NumberNote.Short))
    }

    @Test
    fun `an ordinary number is a conversation`() {
        assertFalse(isServiceSender("+33612345678", known = false, note = france))
    }

    @Test
    fun `a number nobody can parse stays a conversation`() {
        // A stranger with a foreign number is still a person; splitting their messages would be
        // the worse mistake of the two.
        assertFalse(isServiceSender("+99912345678", known = false, note = NumberNote.Unknown))
    }
}

/**
 * India's own conventions, told to us by a user there (issue #13).
 *
 * They are applied on an Indian SIM and nowhere else: eight digits is an ordinary phone number
 * in Norway or Singapore, and treating one as a service would split a person's messages apart.
 */
class IndianSenderTest {

    @Test
    fun `a registered Indian sender is a service, whatever its classification`() {
        listOf("AX-ADHAAR-S", "JD-HDFCBK-T", "AD-MYNTRA-P", "VD-GOVIND-G").forEach { sender ->
            assertTrue(sender, isServiceSender(sender, known = false, note = null, region = "IN"))
        }
    }

    @Test
    fun `a foreign service writing to India is a service`() {
        // Google, Steam and the like: eight or five digits beginning with a five.
        assertTrue(isServiceSender("57575625", known = false, note = NumberNote.Unknown, region = "IN"))
        assertTrue(isServiceSender("57575", known = false, note = NumberNote.Unknown, region = "IN"))
    }

    @Test
    fun `an Indian person is not a service`() {
        // Indian mobile numbers begin with 6, 7, 8 or 9.
        listOf("+916312345678", "9012345678", "7712345678").forEach { number ->
            assertFalse(number, isServiceSender(number, known = false, note = NumberNote.Unknown, region = "IN"))
        }
    }

    @Test
    fun `eight digits elsewhere is somebody's phone number`() {
        // Norway, Denmark, Singapore, Hong Kong: eight digits is an ordinary number.
        assertFalse(isServiceSender("57575625", known = false, note = NumberNote.Unknown, region = "NO"))
        assertFalse(isServiceSender("57575625", known = false, note = NumberNote.Unknown, region = null))
    }
}
