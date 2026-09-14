package com.seca.messages.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.link.SecaLink
import com.seca.core.link.handshake.Handshake

/** Seca Link's discreet invitations, sent and received as data SMS on their own port. */
internal object LinkSms {

    /** The number as Seca Link names a contact; null for what is not a phone number, such as a short code. */
    fun keyOf(context: Context, address: String): String? =
        PhoneNumbers(PhoneNumbers.detectRegion(context)).toE164(address)

    /** Sends [payload] to [address] as one data SMS, which a phone without Seca never shows. */
    fun send(context: Context, address: String, payload: ByteArray) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(SmsManager::class.java) ?: return
        runCatching { manager.sendDataMessage(address, null, Handshake.PORT, payload, null, null) }
    }

    /** Invites [address] to Seca Link when it makes sense: Link on, no session yet, not invited this month. */
    suspend fun inviteIfDue(context: Context, address: String) {
        val number = keyOf(context, address) ?: return
        val invitation = runCatching { SecaLink(context).invitationFor(number) }.getOrNull() ?: return
        send(context, address, invitation)
    }
}
