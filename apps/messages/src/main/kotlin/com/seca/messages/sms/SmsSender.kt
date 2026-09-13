package com.seca.messages.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager

/**
 * Sends a text message through the SIM. A long text is split into the parts
 * the network carries and joined back on the other phone.
 *
 * The message is stored first, so the conversation shows it at once, and is
 * marked sent or failed when the network answers.
 */
class SmsSender(private val context: Context) {

    private val repository = MessagesRepository(context)

    /** False when this phone cannot send: no permission, or no SIM able to. */
    fun send(address: String, body: String): Boolean {
        val text = body.trim()
        if (address.isBlank() || text.isEmpty()) return false
        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(SmsManager::class.java) ?: return false
        val stored = repository.storeOutgoing(address, text)
        val parts = manager.divideMessage(text)
        // The stored message travels with the result, so the receiver knows which one to mark.
        val onSent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, MessageActionReceiver::class.java)
                .setAction(MessageActionReceiver.SENT)
                .setData(stored),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return runCatching {
            manager.sendMultipartTextMessage(address, null, parts, ArrayList(parts.map { onSent }), null)
        }.onFailure {
            stored?.let { repository.setSent(it, sent = false) }
        }.isSuccess
    }
}
