package com.seca.phone.call

import android.app.NotificationManager
import android.content.Intent
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.InCallService

/**
 * Android's telephony hands every call here once Seca Phone is the default
 * phone app. Telecom still does the ringing, the vibration and the audio;
 * this shows who is calling and forwards the buttons.
 */
class SecaInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallSession.attach(this)
    }

    override fun onDestroy() {
        CallSession.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallSession.add(call)
        val manager = getSystemService(NotificationManager::class.java)
        val ringing = call.details.state == Call.STATE_RINGING
        // A ringing call is announced by its notification, which fills the screen when the
        // phone is locked. Without notifications or without that full screen, the call screen
        // opens right away: a call must never go unseen. Outgoing calls always open it.
        val announced = ringing && manager?.areNotificationsEnabled() == true && manager.canUseFullScreenIntent()
        if (!announced) openScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallSession.remove(call)
    }

    /** Android asks for the call screen, for instance from the call chip in the status bar. */
    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        openScreen()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        super.onMuteStateChanged(isMuted)
        CallSession.onMuted(isMuted)
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        CallSession.onEndpoint(callEndpoint)
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        CallSession.onEndpoints(availableEndpoints)
    }

    private fun openScreen() {
        startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
