package com.seca.phone.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog.Calls
import androidx.core.content.edit

/**
 * Which missed calls the owner has already been told about.
 *
 * Android leaves missed calls to the default phone app, and asks for them again every time the
 * phone starts up — with Telecom's own count, which counts every call still flagged new in the
 * log. Clearing that flag needs permission to write the call log, which this app only ever
 * asks for when somebody deletes a call, so for most people it was never granted and every
 * restart announced the same missed call again. One user had it happen every morning.
 *
 * So this app keeps its own note instead, which needs nobody's permission: the moment the
 * history was last looked at. Anything older than that has been seen. The log is still marked
 * read as well where that is allowed, because other apps count it too.
 */
internal object MissedCalls {

    /** The history has been looked at, or the notification put away: everything so far is seen. */
    fun markSeen(context: Context, at: Long = System.currentTimeMillis()) {
        notes(context).edit { putLong(KEY_SEEN, at) }
    }

    /**
     * How many missed calls have arrived since the last look, or null when the log cannot be
     * read and there is nothing to go on but what Telecom said.
     */
    fun unseen(context: Context): Int? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val seenUpTo = notes(context).getLong(KEY_SEEN, 0L)
        return runCatching {
            context.contentResolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls._ID),
                "${Calls.TYPE} = ? AND ${Calls.DATE} > ?",
                arrayOf(Calls.MISSED_TYPE.toString(), seenUpTo.toString()),
                null,
            )?.use { it.count }
        }.getOrNull()
    }

    private fun notes(context: Context) =
        context.getSharedPreferences("missed-calls", Context.MODE_PRIVATE)

    private const val KEY_SEEN = "seen-up-to"
}
