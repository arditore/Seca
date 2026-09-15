package com.seca.messages.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seca.core.link.SecaLink
import com.seca.messages.LinkConversations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The text Seca Phone sends when the owner refuses a call with a message:
 * sent at once, through Seca Link when the caller has it, else by SMS. Only
 * apps signed with the Seca key may ask.
 */
class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val number = intent.getStringExtra(EXTRA_NUMBER)?.takeIf { it.isNotBlank() } ?: return
        val text = intent.getStringExtra(EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val key = LinkSms.keyOf(context, number)
                val linked = key != null && SecaLink(context).canSend(key)
                if (!linked || !LinkConversations(context).sendText(number, text)) SmsSender(context).send(number, text)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val ACTION = "com.seca.messages.action.QUICK_REPLY"
        const val EXTRA_NUMBER = "number"
        const val EXTRA_TEXT = "text"
    }
}
