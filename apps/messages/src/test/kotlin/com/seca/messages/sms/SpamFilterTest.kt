package com.seca.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What gets filed away as advertising.
 *
 * Two worlds meet here: France, where the law makes every commercial text carry a way out, and
 * India, where the regulator classifies the sender itself. The second is the stronger signal —
 * it says what a message is before anyone reads a word of it.
 */
class SpamFilterTest {

    @Test
    fun `a French commercial text names its way out`() {
        assertTrue(SpamFilter.isCommercial("36180", "Profitez de nos offres ! STOP au 36180"))
        assertTrue(SpamFilter.isCommercial("PROMOS", "Nos soldes commencent. Pour ne plus recevoir nos messages, désinscription ici"))
    }

    @Test
    fun `an Indian promotional sender is advertising, whatever it writes`() {
        assertTrue(SpamFilter.isCommercial("AD-MYNTRA-P", "Flat 50% off this weekend only"))
        assertTrue(SpamFilter.isCommercial("am-flipkt-p", "Big billion days are here"))
    }

    @Test
    fun `an Indian bank or government sender is never advertising`() {
        // The word that would otherwise file it away is in the text on purpose.
        assertFalse(SpamFilter.isCommercial("JD-HDFCBK-T", "341112 is your OTP. To unsubscribe from alerts, call us."))
        assertFalse(SpamFilter.isCommercial("AX-ADHAAR-S", "597588 is OTP for Aadhaar. Ne plus recevoir: visit uidai.gov.in"))
        assertFalse(SpamFilter.isCommercial("VD-GOVIND-G", "Votre rendez-vous est confirmé. Désabonnement possible."))
    }

    @Test
    fun `an ordinary person is never advertising`() {
        assertFalse(SpamFilter.isCommercial("+33612345678", "On se voit à 18h ? Je ne veux plus recevoir de pluie."))
    }

    @Test
    fun `a brand offering a link to unsubscribe, with no STOP number, is advertising`() {
        // Seen on a real phone: a clothing brand whose way out is a link rather than the 36xxx
        // number French law is usually read as requiring. The sender is a name, not a number,
        // and it offers to unsubscribe — which together is enough.
        assertTrue(
            SpamFilter.isCommercial(
                "COS",
                "Un cachemire emblematiquement COS. Decouvrir : https://s.cos.com/lnGuyhG" +
                    "Cliquez ici pour vous desabonner: https://s.cos.com/FhByf0y",
            ),
        )
    }

    @Test
    fun `the same wording with its accents is advertising too`() {
        assertTrue(SpamFilter.isCommercial("COS", "Cliquez ici pour vous désabonner: https://s.cos.com/FhByf0y"))
    }

    @Test
    fun `a one-time code that also offers a way out is not advertising to the sweep`() {
        // Caught on a phone: the sweep over older messages offered to file an Uber verification
        // code, because the code arrives with the same "STOP to 38696" footer every commercial
        // text carries. Burying a code someone is waiting for is worse than leaving an
        // advertisement in the inbox, so the code is found first and wins.
        val body = "[Uber] Votre code : 0894. Ne le divulguez jamais. STOP ALL au 38696 pour vous desabonner."

        assertTrue("the wording alone does read as commercial", SpamFilter.isCommercial("Uber", body))
        assertNotNull("so the code has to be what saves it", OneTimeCode.find(body))
    }
}
