package com.seca.phone.call

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The call screen. It shows over the lock screen and turns the screen on, so
 * an incoming call is answered without unlocking, and it holds the proximity
 * sensor while talking, so a cheek never presses a button.
 */
class InCallActivity : ComponentActivity() {

    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        wakeWhileRinging()
        enableEdgeToEdge()
        handle(intent)
        setContent {
            InCallRoot(onScreenOffNearEar = ::holdProximity, onDone = { finishAndRemoveTask() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /**
     * While this screen is in front, the ringing notification stops floating over it: it would
     * land on the very answer and decline buttons it duplicates.
     */
    override fun onResume() {
        super.onResume()
        CallSession.screenVisible(true)
    }

    override fun onPause() {
        CallSession.screenVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        holdProximity(false)
        CallSession.screenVisible(false)
        super.onDestroy()
    }

    /** "Answer" pressed in the notification opens this screen, which answers as it appears. */
    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallSession.ringing()?.let(CallSession::answer)
        }
    }

    /**
     * A window that turns the screen on and the proximity sensor that darkens it pull against
     * each other: the window wakes the display the sensor has just let go dark, and the screen
     * stays lit against the ear for the whole call (#67). The screen only ever needs waking
     * while a call is ringing, so that is exactly how long the flag lives.
     */
    private fun wakeWhileRinging() {
        setTurnScreenOn(CallSession.ringing() != null)
        lifecycleScope.launch {
            CallSession.calls
                .map { calls -> calls.any { it.state == Call.STATE_RINGING || it.state == Call.STATE_SIMULATED_RINGING } }
                .distinctUntilChanged()
                .collect(::setTurnScreenOn)
        }
    }

    private fun holdProximity(hold: Boolean) {
        // A case or a screen protector can keep the sensor covered, which would leave the screen
        // dark for the whole call. The owner can turn this off in settings.
        if (hold && !InCallSettings(this).proximity) return
        val power = getSystemService(PowerManager::class.java) ?: return
        if (!power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
        if (hold) {
            val lock = proximity ?: power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "seca:call").also { proximity = it }
            if (!lock.isHeld) lock.acquire(PROXIMITY_TIMEOUT)
        } else {
            proximity?.takeIf { it.isHeld }?.release()
        }
    }

    companion object {
        const val EXTRA_ANSWER = "answer"

        /** A call outlasting this has long left the ear; the sensor lets go by itself. */
        private const val PROXIMITY_TIMEOUT = 4L * 60 * 60 * 1000
    }
}
