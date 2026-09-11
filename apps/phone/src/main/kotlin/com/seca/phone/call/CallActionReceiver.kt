package com.seca.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Refuser" and "Raccrocher" pressed in a call notification. Not exported: only this app's notifications reach it. */
class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallSession.ringing()?.let { CallSession.decline(it) }
            HANG_UP -> CallSession.calls.value.primary()?.let { CallSession.hangUp(it.call) }
        }
    }

    companion object {
        const val DECLINE = "com.seca.phone.call.DECLINE"
        const val HANG_UP = "com.seca.phone.call.HANG_UP"
    }
}
