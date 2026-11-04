package com.seca.messages.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Fetches a multimedia message from the carrier, once the push has said where it is.
 *
 * The fetch itself is Android's: [SmsManager.downloadMultimediaMessage] knows the carrier's
 * settings, opens the mobile connection the message has to come through — never Wi-Fi, the
 * server is inside the carrier's own network — and goes through whatever proxy the APN names.
 * Doing that by hand is where most third-party messaging apps spend their time, and get it
 * wrong on one carrier in ten.
 *
 * What this adds is a place to put it: the system writes the message into a file this app owns
 * and hands back through a content URI, which is then read, parsed and stored.
 */
internal object MmsDownloader {

    private const val LOG_TAG = "SecaMms"
    const val ACTION_DOWNLOADED = "com.seca.messages.MMS_DOWNLOADED"
    const val EXTRA_FILE = "file"

    /** Starts the fetch. The answer comes back as a broadcast to [MmsDownloadReceiver]. */
    fun download(context: Context, notification: MmsPdu.Notification, subscriptionId: Int) {
        val location = notification.contentLocation?.takeIf { it.isNotBlank() } ?: return
        val file = fileFor(context, notification) ?: return
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
        }.getOrElse {
            Log.w(LOG_TAG, "no uri for the download", it)
            return
        }
        // The telephony service writes into the file, so it needs to be let in for this one URI.
        context.grantUriPermission(TELEPHONY_PACKAGE, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val done = PendingIntent.getBroadcast(
            context,
            file.name.hashCode(),
            Intent(context, MmsDownloadReceiver::class.java)
                .setAction(ACTION_DOWNLOADED)
                .putExtra(EXTRA_FILE, file.absolutePath),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching {
            manager(context, subscriptionId).downloadMultimediaMessage(context, location, uri, null, done)
        }.onFailure { Log.w(LOG_TAG, "download refused", it) }
    }

    /** Reads what the carrier sent, stores it, and tidies the file away. */
    fun finish(context: Context, file: File): MmsPdu.Retrieved? {
        val bytes = runCatching { file.readBytes() }.getOrNull()
        file.delete()
        if (bytes == null || bytes.isEmpty()) return null
        val retrieved = MmsParser.parse(bytes) as? MmsPdu.Retrieved ?: return null
        MmsStore.saveRetrieved(context, retrieved)
        return retrieved
    }

    private fun fileFor(context: Context, notification: MmsPdu.Notification): File? {
        val folder = File(context.cacheDir, "mms").apply { mkdirs() }
        val name = notification.transactionId?.filter(Char::isLetterOrDigit)?.takeIf { it.isNotEmpty() }
            ?: System.currentTimeMillis().toString()
        return runCatching { File(folder, "$name.pdu").apply { if (exists()) delete() } }.getOrNull()
    }

    private fun manager(context: Context, subscriptionId: Int): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subscriptionId >= 0) manager.createForSubscriptionId(subscriptionId) else manager
    }

    private const val TELEPHONY_PACKAGE = "com.android.phone"
}
