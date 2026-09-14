package com.seca.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.seca.core.link.handshake.Handshake
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A data SMS on Seca Link's port: another Seca phone's invitation, or its
 * answer. The session opens here, and the answer leaves the same way.
 */
class LinkHandshakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only the system may send this action; anything else is ignored.
        if (intent.action != Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val address = parts.firstOrNull()?.originatingAddress ?: return
        val payload = parts.fold(ByteArray(0)) { all, part -> all + (part.userData ?: ByteArray(0)) }
        val handshake = Handshake.decode(payload) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LinkSms.receive(context, address, handshake, byText = false)
            } finally {
                pending.finish()
            }
        }
    }
}
