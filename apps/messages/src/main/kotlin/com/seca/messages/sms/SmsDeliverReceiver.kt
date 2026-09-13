package com.seca.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A text message arrives. Android hands it only to the default messaging
 * app, which must store it itself: nothing else will.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only the system may send this action; anything else is ignored rather than stored as a message.
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return
        val address = parts.first().displayOriginatingAddress ?: return
        // A long text arrives in several parts, in order.
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val sentAt = parts.first().timestampMillis
        val subscription = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MessagesRepository(context).storeIncoming(address, body, sentAt, subscription)
                MessageNotifications.notifyIncoming(context, address, body)
            } finally {
                pending.finish()
            }
        }
    }
}
