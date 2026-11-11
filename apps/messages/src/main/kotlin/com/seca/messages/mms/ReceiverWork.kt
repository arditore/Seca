package com.seca.messages.mms

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Does a receiver's work away from the thread that draws, and lets it fail without taking the
 * app down with it.
 *
 * Everything a multimedia receiver touches is bytes a stranger sent to this phone, parsed and
 * then written to the message store. On the main thread that is an "application not
 * responding" waiting for a large enough photo; unguarded it is a crash waiting for a
 * malformed one. The parser is written to return null rather than throw, and this is what
 * stands behind it being wrong about that.
 */
internal fun BroadcastReceiver.away(work: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            work()
        } catch (error: Exception) {
            Log.w("SecaMms", "a message arrived that could not be dealt with", error)
        } finally {
            pending.finish()
        }
    }
}
