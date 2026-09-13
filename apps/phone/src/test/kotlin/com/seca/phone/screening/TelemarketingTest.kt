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
}
