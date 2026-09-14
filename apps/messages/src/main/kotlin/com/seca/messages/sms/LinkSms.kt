package com.seca.messages.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.link.SecaLink
import com.seca.core.link.handshake.Handshake

/**
 * Seca Link's handshakes by SMS: discreet data SMS on their own port, and,
 * since some networks drop those, a short text message the owner can send.
 */
internal object LinkSms {

    /** How a handshake text reads in the conversation list. */
    const val NOTICE = "🔒 Seca Link"

    /** The number as Seca Link names a contact; null for what is not a phone number, such as a short code. */
    fun keyOf(context: Context, address: String): String? =
        PhoneNumbers(PhoneNumbers.detectRegion(context)).toE164(address)

    /**
     * Sends [handshake] to [address] as a data SMS, which a phone without Seca
     * never shows, and also as a text when [alsoText]: kept in the conversation
     * like any message sent, where Seca shows it as a notice.
     */
    fun send(context: Context, address: String, handshake: Handshake, alsoText: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(SmsManager::class.java) ?: return
        runCatching { manager.sendDataMessage(address, null, Handshake.PORT, handshake.encode(), null, null) }
        if (alsoText) SmsSender(context).send(address, handshake.text())
    }

    /** Invites [address] discreetly when it makes sense: Link on, no session yet, not invited this week. */
    suspend fun inviteIfDue(context: Context, address: String) {
        val number = keyOf(context, address) ?: return
        val invitation = runCatching { SecaLink(context).invitationFor(number) }.getOrNull() ?: return
        send(context, address, invitation, alsoText = false)
    }

    /**
     * The owner asks to encrypt the conversation with [address]: the invitation
     * leaves at once, also as a text, which every network carries. False when
     * Seca Link is off, the contact already connected, or [address] no number.
     */
    suspend fun inviteNow(context: Context, address: String): Boolean {
        val number = keyOf(context, address) ?: return false
        val invitation = runCatching { SecaLink(context).invitationFor(number, force = true) }.getOrNull() ?: return false
        send(context, address, invitation, alsoText = true)
        return true
    }

    /** A handshake from [address], by data SMS or [byText]: the session opens, and the answer goes back the same way. */
    suspend fun receive(context: Context, address: String, handshake: Handshake, byText: Boolean) {
        val number = keyOf(context, address) ?: return
        val received = runCatching { SecaLink(context).receive(number, handshake, byText) }.getOrNull()
        if (received is SecaLink.Received.Connected) {
            received.reply?.let { send(context, address, it, alsoText = byText) }
            if (received.keyChanged) MessageNotifications.notifyKeyChanged(context, address)
        }
    }

    /** Contacts whose session could not open when their handshake came are tried again, and answered once connected. */
    suspend fun connectWaiting(context: Context) {
        runCatching { SecaLink(context).connectWaiting() }.getOrDefault(emptyList()).forEach { (peer, reply) ->
            send(context, peer.number, reply, alsoText = peer.textHandshake)
        }
    }

    /** A conversation's latest message as the list shows it: a handshake text reads as a short notice. */
    fun snippetOf(body: String): String = if (Handshake.fromText(body) != null) NOTICE else body
}
