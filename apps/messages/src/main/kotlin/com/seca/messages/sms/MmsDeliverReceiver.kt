package com.seca.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Multimedia messages are not handled yet. Android only lets an app become
 * the default messaging app when it declares this receiver, so it exists and
 * does nothing for now.
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only the system may send this action; anything else is ignored.
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        // The MMS itself is left to a later version.
    }
}
