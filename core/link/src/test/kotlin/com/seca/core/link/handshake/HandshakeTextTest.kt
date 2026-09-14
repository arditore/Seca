package com.seca.core.link.handshake

import com.seca.core.link.LinkSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeTextTest {

    private val invite = Handshake(
        type = Handshake.Type.Invite,
        nostrPublicKey = "ab".repeat(32),
        identityHash = "0123456789abcdef",
        relays = LinkSettings.DefaultRelays.take(3),
    )

    @Test
    fun `a handshake written in a text message reads back the same`() {
        assertEquals(invite, Handshake.fromText(invite.text()))
        val accept = invite.copy(type = Handshake.Type.Accept)
        assertEquals(accept, Handshake.fromText(accept.text()))
    }

    @Test
    fun `it fits in a single text message with the default relays`() {
        assertTrue(invite.text().length <= 160)
    }

    @Test
    fun `an ordinary message carries no handshake`() {
        assertNull(Handshake.fromText("On se voit à 18 h ?"))
        assertNull(Handshake.fromText("seca-link:pas-une-invitation"))
    }
}
