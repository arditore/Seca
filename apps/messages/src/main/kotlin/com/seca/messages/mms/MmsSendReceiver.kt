package com.seca.messages.mms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import java.io.File

/**
 * Hears whether the carrier took a multimedia message.
 *
 * Only this app can reach it — the broadcast names this exact class — and all it is told is
 * which message and which file. A message the carrier refused goes to failed rather than
 * disappearing: an outgoing message that silently stops existing is the one failure people
 * never forgive, and the conversation offers it again from there.
 */
class MmsSendReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsSender.ACTION_SENT) return
        val message = intent.getStringExtra(MmsSender.EXTRA_MESSAGE)?.toUri() ?: return
        val file = intent.getStringExtra(MmsSender.EXTRA_FILE)?.let(::File)
        val sent = resultCode == Activity.RESULT_OK
        if (!sent) Log.w(LOG_TAG, "the carrier refused the message (code $resultCode)")
        MmsSender.finish(context, message, file, sent)
    }

    private companion object {
        const val LOG_TAG = "SecaMms"
    }
}
