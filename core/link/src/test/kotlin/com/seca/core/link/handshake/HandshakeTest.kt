package com.seca.core.link.handshake

import com.seca.core.link.LinkSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeTest {

    private val key = "ab".repeat(32)
    private val hash = "0123456789abcdef"

    @Test
    fun `an invitation comes back as it was sent, default and spelled-out relays alike`() {
        val sent = Handshake(Handshake.Type.Invite, key, hash, listOf(LinkSettings.DefaultRelays[1], "wss://relay.example.org"))
        val bytes = sent.encode()
        assertEquals(sent, Handshake.decode(bytes))
        assertTrue(bytes.size <= Handshake.MAX_PAYLOAD)
    }

    @Test
    fun `relays that would not fit in one SMS are left out`() {
        val long = (1..3).map { "wss://" + "r$it".padEnd(60, 'x') + ".org" }
        val bytes = Handshake(Handshake.Type.Accept, key, hash, long).encode()
        assertTrue(bytes.size <= Handshake.MAX_PAYLOAD)
        assertEquals(1, Handshake.decode(bytes)?.relays?.size)
    }

    @Test
    fun `anything else is not an invitation`() {
        assertNull(Handshake.decode(byteArrayOf(1, 2, 3)))
        val bytes = Handshake(Handshake.Type.Invite, key, hash, emptyList()).encode()
        bytes[0] = 0
        assertNull(Handshake.decode(bytes))
    }
}
