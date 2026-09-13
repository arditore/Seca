package com.seca.messages.sms

import android.app.Activity
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seca.messages.copySensitive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * What happens to a message after the screen: the network confirms a send,
 * or the owner replies or marks a conversation read from its notification.
 * Not exported: only this app reaches it.
 */
class MessageActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == COPY_CODE) {
            intent.getStringExtra(MessageNotifications.EXTRA_CODE)?.let { copySensitive(context, it) }
            return
        }
        val sent = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent, sent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent, sent: Boolean) {
        val repository = MessagesRepository(context)
        val threadId = intent.getLongExtra(MessageNotifications.EXTRA_THREAD, -1L)
        when (intent.action) {
            SENT -> intent.data?.let { repository.setSent(it, sent) }
            REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(MessageNotifications.KEY_REPLY)
                    ?.toString()
                val address = intent.getStringExtra(MessageNotifications.EXTRA_ADDRESS)
                if (text.isNullOrBlank() || address.isNullOrBlank()) return
                SmsSender(context).send(address, text)
                // Replying means the conversation was read.
                if (threadId >= 0) repository.markRead(threadId)
                MessageNotifications.cancel(context, threadId)
            }
            MARK_READ -> {
                if (threadId >= 0) repository.markRead(threadId)
                MessageNotifications.cancel(context, threadId)
            }
            SEND_SCHEDULED -> {
                val scheduled = ScheduledMessages(context)
                val message = scheduled.find(intent.getLongExtra(ScheduledMessages.EXTRA_ID, -1L)) ?: return
                if (SmsSender(context).send(message.address, message.body)) {
                    scheduled.remove(message.id)
                } else {
                    // Kept, so the conversation still offers to send it; the owner learns it did not leave.
                    MessageNotifications.notifyScheduledNotSent(context, message.address)
                }
            }
        }
    }

    companion object {
        const val SENT = "com.seca.messages.SENT"
        const val REPLY = "com.seca.messages.REPLY"
        const val MARK_READ = "com.seca.messages.MARK_READ"
        const val COPY_CODE = "com.seca.messages.COPY_CODE"
        const val SEND_SCHEDULED = "com.seca.messages.SEND_SCHEDULED"
    }
}
