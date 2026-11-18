package com.seca.phone.screening

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemarketingTest {

    @Test
    fun `numbers in the ranges reserved for telemarketing are recognised`() {
        assertTrue(Telemarketing.isTelemarketing("+33162000000"))
        assertTrue(Telemarketing.isTelemarketing("+33949123456"))
    }

    @Test
    fun `ordinary French and foreign numbers are left alone`() {
        assertFalse(Telemarketing.isTelemarketing("+33612345678"))
        assertFalse(Telemarketing.isTelemarketing("+33145000000"))
        assertFalse(Telemarketing.isTelemarketing("+44162000000"))
    }

    /**
     * Where the switch is worth offering at all (#77).
     *
     * The ranges are France's, so everywhere else the filter is a switch that promises
     * something and never fires. By the SIM's country rather than the phone's language,
     * which gets a French speaker in Belgium and an English speaker in France both wrong.
     */
    @Test
    fun `the filter is offered in France and nowhere else`() {
        assertTrue(Telemarketing.offeredIn("FR"))
        assertTrue("the country can arrive in either case", Telemarketing.offeredIn("fr"))
        assertFalse(Telemarketing.offeredIn("BE"))
        assertFalse(Telemarketing.offeredIn("PL"))
        assertFalse(Telemarketing.offeredIn("TR"))
        assertFalse("an unknown country is not France", Telemarketing.offeredIn("ZZ"))
        assertFalse(Telemarketing.offeredIn(""))
    }
}
