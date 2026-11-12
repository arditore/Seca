package com.seca.messages.mms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Sends a multimedia message.
 *
 * The phone does not post it for us: the message is built here, written into a file of this
 * app's own, and the telephony service is handed that one file to carry to the carrier's
 * server. It answers later, to [MmsSendReceiver].
 *
 * The message is stored before it leaves, in the outbox, so the conversation shows it at once
 * and so a send that never comes back leaves something behind rather than nothing.
 */
internal object MmsSender {

    const val ACTION_SENT = "com.seca.messages.MMS_SENT"
    const val EXTRA_FILE = "file"
    const val EXTRA_MESSAGE = "message"

    /** The stored message, or null when it could not be built or written. */
    suspend fun send(
        context: Context,
        to: List<String>,
        text: String,
        attachments: List<MmsPdu.Part>,
        subscriptionId: Int = NO_SIM_CHOSEN,
    ): Uri? = withContext(Dispatchers.IO) {
        if (to.isEmpty() || (text.isBlank() && attachments.isEmpty())) return@withContext null
        val transactionId = "T${UUID.randomUUID().toString().replace("-", "").take(TRANSACTION_LENGTH)}"
        // The sender is left for the carrier to write in. They do it regardless, they are the
        // only ones who know it for certain, and a message naming the wrong sender is refused
        // outright by several networks — so there is nothing to gain by guessing.
        val outgoing = Outgoing(to = to, from = null, text = text, attachments = attachments)
        val pdu = runCatching { MmsComposer.compose(outgoing, transactionId) }.getOrNull()
            ?: return@withContext null

        val stored = MmsStore.saveOutgoing(context, outgoing, transactionId) ?: return@withContext null
        val file = runCatching {
            File(context.cacheDir, "mms").apply { mkdirs() }
                .resolve("send-$transactionId.pdu")
                .apply { writeBytes(pdu) }
        }.getOrNull() ?: run {
            MmsStore.markSent(context, stored, sent = false)
            return@withContext stored
        }

        val shared = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.mms", file)
        }.getOrNull() ?: run {
            MmsStore.markSent(context, stored, sent = false)
            return@withContext stored
        }
        // The telephony service reads our file, and only this one, for as long as it takes.
        context.grantUriPermission(TELEPHONY, shared, Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val done = PendingIntent.getBroadcast(
            context,
            transactionId.hashCode(),
            Intent(context, MmsSendReceiver::class.java)
                .setAction(ACTION_SENT)
                .putExtra(EXTRA_FILE, file.absolutePath)
                .putExtra(EXTRA_MESSAGE, stored.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val handed = runCatching {
            manager(context, subscriptionId).sendMultimediaMessage(context, shared, null, null, done)
            true
        }.onFailure { Log.w(LOG_TAG, "the phone would not take the message", it) }.getOrDefault(false)
        if (!handed) {
            file.delete()
            MmsStore.markSent(context, stored, sent = false)
        }
        stored
    }

    /** Called back once the carrier has answered; the file is ours to clean up either way. */
    fun finish(context: Context, message: Uri, file: File?, sent: Boolean, reason: Int = 0) {
        file?.delete()
        MmsStore.markSent(context, message, sent, reason)
    }

    private fun manager(context: Context, subscriptionId: Int): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subscriptionId == NO_SIM_CHOSEN) manager else manager.createForSubscriptionId(subscriptionId)
    }

    const val NO_SIM_CHOSEN: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private const val TELEPHONY = "com.android.phone"
    private const val TRANSACTION_LENGTH = 16
    private const val LOG_TAG = "SecaMms"
}
