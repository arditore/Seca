package com.seca.phone.call

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

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
        setTurnScreenOn(true)
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

    override fun onDestroy() {
        holdProximity(false)
        super.onDestroy()
    }

    /** "Answer" pressed in the notification opens this screen, which answers as it appears. */
    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallSession.ringing()?.let(CallSession::answer)
        }
    }

    private fun holdProximity(hold: Boolean) {
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
