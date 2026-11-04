package com.seca.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.seca.messages.mms.MmsDownloader
import com.seca.messages.mms.MmsParser
import com.seca.messages.mms.MmsPdu
import com.seca.messages.mms.MmsStore

/**
 * A multimedia message has been announced.
 *
 * This is the one that matters most in the whole app: as the phone's messaging app, Seca is the
 * only app Android hands this to. Ignoring it, as earlier versions did, meant a picture message
 * simply never arrived — not shown late, not shown badly: gone, with nobody told.
 *
 * What arrives here is only the announcement. It says who wrote, how big the message is and
 * where the carrier is keeping it; the message itself is fetched afterwards, over the mobile
 * connection, and that answer comes back to [com.seca.messages.mms.MmsDownloadReceiver].
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only the system may send this action; anything else is ignored.
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val bytes = intent.getByteArrayExtra(EXTRA_DATA) ?: return
        val notification = MmsParser.parse(bytes) as? MmsPdu.Notification ?: run {
            Log.w(LOG_TAG, "a push arrived that is not a message anyone can read")
            return
        }
        // Stored first, fetched second: a fetch that fails then leaves something behind that
        // remembers a message is owed, rather than nothing at all.
        MmsStore.saveNotification(context, notification)
        MmsDownloader.download(context, notification, subscriptionOf(intent))
    }

    private fun subscriptionOf(intent: Intent): Int =
        intent.getIntExtra(EXTRA_SUBSCRIPTION, SubscriptionManager.INVALID_SUBSCRIPTION_ID)

    private companion object {
        const val EXTRA_DATA = "data"
        const val EXTRA_SUBSCRIPTION = "subscription"
        const val LOG_TAG = "SecaMms"
    }
}
