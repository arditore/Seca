package com.seca.messages.sms

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.content.edit
import kotlinx.coroutines.delay

/**
 * A short sound when a message goes, for people who want to hear that it did.
 *
 * Asked for by a user who missed it from the app they came from. **Off by default**: a phone
 * that makes a noise nobody asked for in a meeting is the one thing nobody forgives, and the
 * message appearing in the conversation already says it went.
 *
 * It is a tone the phone makes rather than a sound file, so there is nothing to ship, nothing
 * to license and nothing to go out of fashion. It plays on the notification stream, so the
 * volume the owner set for notifications is the volume it plays at — and it is not played at
 * all when the phone is set to silent, where the stream alone would not be enough on every
 * version of Android.
 *
 * The sound means the message was handed over, which is all the app ever knows at that moment.
 */
object SentSound {

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, on) }
    }

    /** Plays it, if it is wanted and the phone is not being kept quiet. */
    suspend fun play(context: Context) {
        if (!enabled(context)) return
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, LENGTH_MS)
            // Released after it has finished: let go of it any sooner and it is cut off.
            delay(LENGTH_MS + SETTLE_MS)
            tone.release()
        }
    }

    private const val PREFS = "seca_sent_sound"
    private const val KEY_ENABLED = "enabled"

    /** Out of a hundred. Quiet enough to sit under a conversation rather than announce itself. */
    private const val VOLUME = 60

    private const val LENGTH_MS = 120
    private const val SETTLE_MS = 60L
}
