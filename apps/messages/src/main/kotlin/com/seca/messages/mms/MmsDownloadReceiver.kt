package com.seca.messages.mms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.seca.messages.sms.MessageNotifications
import java.io.File

/**
 * Hears back from the carrier's server, once a multimedia message has been fetched.
 *
 * Only this app can reach it — the broadcast is sent to this exact class — and the only thing it
 * is told is which file was written. Everything else comes out of that file.
 */
class MmsDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsDownloader.ACTION_DOWNLOADED) return
        val path = intent.getStringExtra(MmsDownloader.EXTRA_FILE) ?: return
        val file = File(path)
        if (resultCode != Activity.RESULT_OK) {
            // The message stays announced but unfetched: the notification row is still there, so
            // nothing is lost, and another app or a later try can still go and get it.
            Log.w(LOG_TAG, "carrier did not send the message (code $resultCode)")
            file.delete()
            return
        }
        away {
            val retrieved = MmsDownloader.finish(context, file) ?: return@away
            val from = retrieved.from ?: return@away
            MessageNotifications.notifyIncoming(
                context,
                from,
                MessageNotifications.previewOf(context, retrieved),
            )
        }
    }

    private companion object {
        const val LOG_TAG = "SecaMms"
    }
}
