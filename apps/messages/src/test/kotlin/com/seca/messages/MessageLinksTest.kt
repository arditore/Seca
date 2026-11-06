package com.seca.messages

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a message offers to open, and — more importantly — what it does not.
 *
 * The failures worth guarding against are the ones that open something nobody wrote: half a web
 * address because a number inside it looked like a phone number, or a price turned into a call
 * button. Those are silent, and the owner only finds out when the dialer opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MessageLinksTest {

    @Test
    fun `a web address is offered whole, scheme and all`() {
        assertEquals(
            listOf("https://s.cos.com/FhByf0y"),
            linksIn("Cliquez ici pour vous désabonner: https://s.cos.com/FhByf0y"),
        )
    }

    @Test
    fun `an address written without its scheme still opens`() {
        assertEquals(listOf("https://f-droid.org"), linksIn("Seca is on f-droid.org now"))
    }

    @Test
    fun `a link is never cut in half by the digits inside it`() {
        // The run of digits in the path would be a phone number on its own; inside an address
        // it is part of the address, and splitting it would open neither.
        assertEquals(listOf("https://example.org/0612345678/x"), linksIn("see https://example.org/0612345678/x"))
    }

    @Test
    fun `a price is not a phone number`() {
        assertEquals(emptyList<String>(), linksIn("Ne manquez pas votre reprise de 131 EUR avant le 03/10"))
    }

    @Test
    fun `a real number is offered to the dialer, without its spaces`() {
        assertEquals(listOf("tel:+33612345678"), linksIn("Rappelez-moi au +33 6 12 34 56 78"))
    }

    @Test
    fun `an e-mail address goes to the mail app`() {
        assertEquals(listOf("mailto:someone@example.org"), linksIn("write to someone@example.org"))
    }

    @Test
    fun `a message with nothing to open offers nothing`() {
        assertEquals(emptyList<String>(), linksIn("Coucou, tu es dispo ce soir ?"))
    }
}
