package com.seca.messages.sms

import org.junit.Assert.assertFalse
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
}
