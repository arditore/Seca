package com.seca.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seca.core.suite.SecaBlocking
import com.seca.phone.blockNumbers
import com.seca.phone.unblockNumbers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Seca Contacts asking for somebody to be blocked (#50).
 *
 * Android's blocked-number list may only be written by the default phone app, which is this
 * one, so the request arrives here and the writing happens here. Guarded in the manifest by
 * the Seca signing key: no other app can ask.
 */
class BlockRequestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SecaBlocking.ACTION) return
        val numbers = intent.getStringArrayExtra(SecaBlocking.EXTRA_NUMBERS)?.toList().orEmpty()
        if (numbers.isEmpty()) return
        val block = intent.getBooleanExtra(SecaBlocking.EXTRA_BLOCK, true)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (block) blockNumbers(context, numbers) else unblockNumbers(context, numbers)
            } finally {
                pending.finish()
            }
        }
    }
}
