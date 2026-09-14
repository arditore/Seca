package com.seca.phone.call

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.Call

/**
 * "Refuser et répondre" without the wait. Left to Android, the text only
 * leaves as the call is torn down on the network, which can take a while.
 * When Seca Messages is the messaging app, the call is refused at once and the
 * text handed to it: through Seca Link when the caller has it, else by SMS.
 */
internal object QuickReply {

    private const val SECA_MESSAGES = "com.seca.messages"
    private const val ACTION = "com.seca.messages.action.QUICK_REPLY"
    private const val PERMISSION = "com.seca.permission.SEND_MESSAGES"
    private const val EXTRA_NUMBER = "number"
    private const val EXTRA_TEXT = "text"

    fun decline(context: Context?, call: Call, text: String) {
        val number = call.details.handle?.schemeSpecificPart
        if (context != null && !number.isNullOrBlank() && handOver(context, number, text)) {
            call.reject(false, null)
        } else {
            call.reject(true, text)
        }
    }

    private fun handOver(context: Context, number: String, text: String): Boolean {
        // Only the messaging app may send a text, and only a Seca app signed with the same key may be asked to.
        if (Telephony.Sms.getDefaultSmsPackage(context) != SECA_MESSAGES) return false
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        val intent = Intent(ACTION).setPackage(SECA_MESSAGES).putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_TEXT, text)
        if (context.packageManager.queryBroadcastReceivers(intent, 0).isEmpty()) return false
        return runCatching { context.sendBroadcast(intent) }.isSuccess
    }
}
