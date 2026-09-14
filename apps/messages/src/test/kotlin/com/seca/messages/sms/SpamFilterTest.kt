package com.seca.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpamFilterTest {

    @Test
    fun `a commercial text offering STOP to a 36xxx number is advertising`() {
        assertTrue(SpamFilter.isCommercial("+33612345678", "Soldes -50 % ce week-end ! STOP au 36180"))
        assertTrue(SpamFilter.isCommercial("38100", "Votre offre fibre vous attend. Stop SMS 36111"))
        assertTrue(SpamFilter.isCommercial("BOUTIQUE", "Nouveautés en magasin. STOP 36063"))
    }

    @Test
    fun `a brand or short code offering to unsubscribe is advertising`() {
        assertTrue(SpamFilter.isCommercial("MAGASIN", "Pour vous désinscrire, répondez ARRET"))
        assertTrue(SpamFilter.isCommercial("36555", "Pour ne plus recevoir nos offres, cliquez ici"))
    }

    @Test
    fun `ordinary texts are not`() {
        assertFalse(SpamFilter.isCommercial("+33612345678", "On se voit à 18h ? Sinon stop, je rentre"))
        assertFalse(SpamFilter.isCommercial("+33612345678", "Je me suis désinscrit du club, tu viens quand même ?"))
        assertFalse(SpamFilter.isCommercial("BANQUE", "Votre code de connexion est 482913"))
    }
}
