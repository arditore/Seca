package com.seca.messages.sms

import android.provider.Telephony.Sms
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The status byte a network sends back, read as the three things it can mean.
 *
 * The boundaries are the point: they come from the GSM standard, not from a guess, and a report
 * read one notch out would show a message as delivered that the network is still carrying — or,
 * worse, as delivered when the network has given up on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeliveryReportsTest {

    @Test
    fun `under thirty-two, the message reached the phone`() {
        assertEquals(Sms.STATUS_COMPLETE, DeliveryReports.classify(0))
        assertEquals(Sms.STATUS_COMPLETE, DeliveryReports.classify(31))
    }

    @Test
    fun `thirty-two to sixty-three, it is still on its way`() {
        assertEquals(Sms.STATUS_PENDING, DeliveryReports.classify(32))
        assertEquals(Sms.STATUS_PENDING, DeliveryReports.classify(63))
    }

    @Test
    fun `sixty-four and over, the network gave up`() {
        assertEquals(Sms.STATUS_FAILED, DeliveryReports.classify(64))
        assertEquals(Sms.STATUS_FAILED, DeliveryReports.classify(127))
    }

    @Test
    fun `a report that cannot be read says nothing, as a carrier sending none does`() {
        assertEquals(Sms.STATUS_NONE, DeliveryReports.statusOf(null, null))
        assertEquals(Sms.STATUS_NONE, DeliveryReports.statusOf(byteArrayOf(0, 1, 2), "3gpp"))
    }
}
