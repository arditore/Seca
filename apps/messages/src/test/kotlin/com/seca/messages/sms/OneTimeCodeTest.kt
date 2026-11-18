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

    /**
     * The message that started this: an appointment reminder asking to confirm, with a postcode
     * at the bottom. Both halves of the old rule matched — a keyword somewhere, digits
     * somewhere — and where the owner has asked for codes to be erased after a while, that is a
     * real message quietly deleted.
     */
    @Test
    fun `an appointment reminder is not a verification code`() {
        assertNull(
            OneTimeCode.find(
                "Bonjour, rappel RDV: mer 18/11 à 10h15. Merci de confirmer votre venue. " +
                    "Cabinet médical, 12 rue des Lilas, 35000 Rennes",
            ),
        )
    }

    /** Digits far from the word they are supposed to belong to are not that word's code. */
    @Test
    fun `digits at the other end of a long message do not count`() {
        assertNull(
            OneTimeCode.find(
                "Votre code a bien été utilisé, merci de votre confiance et à bientôt chez nous, " +
                    "nous vous rappelons que notre boutique se trouve au 75011 Paris",
            ),
        )
    }

    /** A real one still works when the digits come first, which many of them do. */
    @Test
    fun `a code before its word is still a code`() {
        assertEquals("904122", OneTimeCode.find("904122 — votre code de sécurité"))
    }

    /** The words that said nothing on their own, and now say nothing at all. */
    @Test
    fun `a message about a connection problem is not a login code`() {
        assertNull(OneTimeCode.find("Problème de connexion depuis 2026, on réessaie demain"))
    }
}
