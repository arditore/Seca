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
