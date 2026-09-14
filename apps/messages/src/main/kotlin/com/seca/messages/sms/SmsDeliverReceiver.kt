package com.seca.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.seca.messages.ConversationPrefs
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
                val stored = MessagesRepository(context).storeIncoming(address, body, sentAt, subscription)
                val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull()
                val prefs = ConversationPrefs(context)
                val code = OneTimeCode.find(body)

                if (threadId != null && isAdvertising(context, prefs, threadId, address, body, code)) {
                    // Filed away quietly: no notification, no invitation to Seca Link.
                    prefs.setSpam(threadId, spam = true)
                    return@launch
                }
                // A new message brings an archived conversation back into the list.
                threadId?.let { prefs.setArchived(it, archived = false) }
                if (code != null && stored != null && threadId != null) CodeCleanup.schedule(context, stored, threadId)
                MessageNotifications.notifyIncoming(context, address, body)
                // A conversation with someone new also offers them Seca Link, discreetly, when it is on.
                LinkSms.inviteIfDue(context, address)
            } finally {
                pending.finish()
            }
        }
    }

    private fun isAdvertising(
        context: Context,
        prefs: ConversationPrefs,
        threadId: Long,
        address: String,
        body: String,
        code: String?,
    ): Boolean {
        if (threadId in prefs.spam()) return true
        if (code != null || prefs.isTrusted(threadId) || !SpamFilter.enabled(context)) return false
        return SpamFilter.isCommercial(address, body) && !SpamFilter.isContact(context, address)
    }
}
