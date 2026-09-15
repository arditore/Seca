package com.seca.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeerTest {

    @Test
    fun `a contact whose session did not open is tried again ten minutes later`() {
        val peer = LinkPeer("+33612345678", attemptedAt = START)
        assertFalse(peer.retryDue(now = START + 9 * MINUTE))
        assertTrue(peer.retryDue(now = START + 10 * MINUTE))
    }

    @Test
    fun `each failure doubles the wait, up to twelve hours`() {
        assertEquals(10 * MINUTE, retryDelayMillis(failedAttempts = 0))
        assertEquals(20 * MINUTE, retryDelayMillis(failedAttempts = 1))
        assertEquals(40 * MINUTE, retryDelayMillis(failedAttempts = 2))
        assertEquals(12 * HOUR, retryDelayMillis(failedAttempts = 30))
    }

    @Test
    fun `a contact that keeps failing waits longer before the next try`() {
        val peer = LinkPeer("+33612345678", attemptedAt = START, failedAttempts = 3)
        assertFalse(peer.retryDue(now = START + 79 * MINUTE))
        assertTrue(peer.retryDue(now = START + 80 * MINUTE))
    }

    @Test
    fun `keys published in the last day mean the contact is still there`() {
        // Seca Link publishes its keys again every five hours: a whole day of silence is not a quiet evening.
        val now = START + 10 * DAY
        assertFalse(keysLookAbandoned(publishedAt = now - 23 * HOUR, now = now))
        assertTrue(keysLookAbandoned(publishedAt = now - 25 * HOUR, now = now))
    }

    @Test
    fun `no keys at all on the relays means the contact left Seca Link`() {
        assertTrue(keysLookAbandoned(publishedAt = 0, now = START))
    }

    private companion object {
        const val START = 1_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
