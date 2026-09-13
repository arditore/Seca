package com.seca.core.link.nostr

import com.seca.core.link.LinkSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NostrEventTest {

    @Test
    fun `serializes for the id exactly as NIP-01 writes it`() {
        val text = NostrEvent.serializeForId("ab", 1_700_000_000, 30078, listOf(listOf("d", "x/y")), "a\"b\\c\nd/é")
        assertEquals("[0,\"ab\",1700000000,30078,[[\"d\",\"x/y\"]],\"a\\\"b\\\\c\\nd/é\"]", text)
    }

    @Test
    fun `other control characters stay raw for the id but are escaped on the wire`() {
        assertTrue(NostrEvent.serializeForId("p", 1, 1, emptyList(), Char(1).toString()).contains(Char(1).toString()))
        val event = NostrEvent("i", "p", 1, 1, emptyList(), Char(1).toString(), "s")
        assertTrue(event.toJson().contains("\\u0001"))
    }

    @Test
    fun `relay addresses are completed and only encrypted ones accepted`() {
        assertEquals("wss://nos.lol", LinkSettings.normalize(" nos.lol/ "))
        assertEquals("wss://relay.example.org:444/inbox", LinkSettings.normalize("WSS://Relay.Example.org:444/inbox"))
        assertNull(LinkSettings.normalize("ws://nos.lol"))
        assertNull(LinkSettings.normalize("https://nos.lol"))
        assertNull(LinkSettings.normalize("pas un relais"))
        assertNull(LinkSettings.normalize("localhost"))
    }
}
