package com.seca.messages.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * "Refuser et répondre" from a call screen: the default messaging app sends
 * the text without showing anything. Only the system can start this service.
 */
class RespondViaMessageService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // "smsto:0612345678", possibly several numbers separated by commas or semicolons.
        val numbers = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            val sender = SmsSender(this)
            numbers.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { sender.send(it, text) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
