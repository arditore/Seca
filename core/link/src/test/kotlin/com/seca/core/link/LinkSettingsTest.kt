package com.seca.core.link

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkSettingsTest {

    private val relays = listOf("wss://relay.damus.io", "wss://nos.lol", "wss://relay.primal.net", "wss://nostr.mom")

    @Test
    fun `invitations name first the relays that really keep the keys`() {
        // As read on 2026-09-15: two of the four default relays served no pre-key bundle at all.
        val kept = listOf("wss://nostr.mom", "wss://nos.lol")
        assertEquals(
            listOf("wss://nos.lol", "wss://nostr.mom", "wss://relay.damus.io", "wss://relay.primal.net"),
            LinkSettings.orderForHandshake(relays, kept),
        )
    }

    @Test
    fun `keeps the owner's order while no relay is known to keep the keys`() {
        assertEquals(relays, LinkSettings.orderForHandshake(relays, emptyList()))
    }

    @Test
    fun `ignores a relay that kept the keys but is no longer in the list`() {
        assertEquals(relays, LinkSettings.orderForHandshake(relays, listOf("wss://gone.example")))
    }
}
