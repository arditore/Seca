package com.seca.messages.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OneTimeCodeTest {

    @Test
    fun `finds the code of a verification message`() {
        assertEquals("482913", OneTimeCode.find("Votre code de vérification : 482 913. Ne le partagez pas."))
        assertEquals("7351", OneTimeCode.find("7351 is your login code"))
    }

    @Test
    fun `leaves ordinary messages alone`() {
        assertNull(OneTimeCode.find("On se retrouve à 1830 devant la gare"))
        assertNull(OneTimeCode.find("Commande 123456 expédiée"))
    }
}
