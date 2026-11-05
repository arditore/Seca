package com.seca.messages.sms

import android.content.Context
import android.provider.Telephony.Sms
import android.telephony.SmsMessage
import androidx.core.content.edit

/**
 * Whether the network is asked to say that a text arrived.
 *
 * A delivery report is a real part of SMS and costs the other person nothing: it comes from the
 * network, not from their phone, so unlike a read receipt it tells them nothing about you and you
 * nothing about them beyond the fact that the message got there.
 *
 * **Off by default**, and that is the careful choice rather than the lazy one. Many carriers never
 * send a report at all, some send one meaning only that the network accepted the message, and a
 * few charge for it. Turned on where nothing comes back, every message would sit at "Sent" looking
 * stuck; left off, "Sent" is all the app ever claimed to know.
 */
object DeliveryReports {

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, on) }
    }

    /**
     * What a report says, as the provider records it.
     *
     * The report arrives as a PDU of the network's own, whose status byte follows the GSM
     * standard: under 32 the message reached the phone, 32 to 63 it is still on its way, 64 and
     * over the network gave up. A report that cannot be read at all is treated as nothing having
     * been said, which is also what a carrier that sends none leaves behind.
     */
    fun statusOf(pdu: ByteArray?, format: String?): Int {
        val report = pdu ?: return Sms.STATUS_NONE
        val status = runCatching { SmsMessage.createFromPdu(report, format)?.status }.getOrNull()
            ?: return Sms.STATUS_NONE
        return classify(status)
    }

    /** The standard's status byte, as the three outcomes the provider keeps. */
    internal fun classify(status: Int): Int = when {
        status < PENDING_FROM -> Sms.STATUS_COMPLETE
        status < FAILED_FROM -> Sms.STATUS_PENDING
        else -> Sms.STATUS_FAILED
    }

    private const val PREFS = "seca_delivery"
    private const val KEY_ENABLED = "enabled"

    /** Where the GSM status byte stops meaning "arrived" and starts meaning "on its way". */
    private const val PENDING_FROM = 32

    /** And where it starts meaning the network gave up. */
    private const val FAILED_FROM = 64
}
