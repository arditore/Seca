package com.seca.phone.call

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Buttons pressed in this app's call notifications. Not exported: only they reach it. */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallSession.ringing()?.let { CallSession.decline(it) }
            HANG_UP -> CallSession.calls.value.primary()?.let { CallSession.hangUp(it.call) }
            MISSED_SEEN -> MissedCalls.markSeen(context)
        }
    }

    companion object {
        const val DECLINE = "com.seca.phone.call.DECLINE"
        const val HANG_UP = "com.seca.phone.call.HANG_UP"
        private const val MISSED_SEEN = "com.seca.phone.call.MISSED_SEEN"

        /**
         * Putting the missed-call notification away counts as having seen it.
         *
         * Without this, somebody who swipes the notification aside rather than opening the
         * history is told about the same call again the next time the phone starts.
         */
        fun missedSeen(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CallActionReceiver::class.java).setAction(MISSED_SEEN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
