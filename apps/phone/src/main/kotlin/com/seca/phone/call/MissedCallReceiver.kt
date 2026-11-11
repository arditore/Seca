package com.seca.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Android leaves missed calls for the default phone app to announce. A count of zero means
 * they have been seen: the notification goes away.
 *
 * The count Telecom sends is its own, and it counts every missed call still flagged new in the
 * log — which, on a phone where this app was never given leave to write that log, is every
 * missed call there has ever been. So the app counts them itself, against its own note of when
 * the history was last looked at, and only falls back to Telecom's number when it cannot read
 * the log at all.
 */
class MissedCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val told = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        if (told <= 0) {
            CallNotifications.cancelMissed(context)
            return
        }
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        // Counted and looked up off the main thread: the log is a query and the caller's name
        // and photo are two more.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = MissedCalls.unseen(context) ?: told
                if (count <= 0) {
                    CallNotifications.cancelMissed(context)
                    return@launch
                }
                CallNotifications.showMissed(
                    context,
                    count,
                    number,
                    callBack = null,
                    clear = CallActionReceiver.missedSeen(context),
                )
            } finally {
                pending.finish()
            }
        }
    }
}
