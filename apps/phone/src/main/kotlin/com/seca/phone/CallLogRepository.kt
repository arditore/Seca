package com.seca.phone

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog.Calls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

enum class CallType { Incoming, Outgoing, Missed, Rejected, Blocked, Voicemail, Other }

/** Whether the caller shared their number: a hidden or unknown one cannot be called back. */
enum class Presentation { Allowed, Restricted, Unknown, Payphone }

/** One row of the phone's call history. */
data class CallRecord(
    val id: Long,
    val number: String,
    val type: CallType,
    val date: Long,
    val durationSeconds: Long,
    val presentation: Presentation,
    /** The name the system dialer stored at the time, used when the contact is no longer visible. */
    val cachedName: String?,
)

/**
 * Reads the system call history, the one every dialer on the phone shares.
 * Nothing is copied: the history stays where Android keeps it.
 */
class CallLogRepository(private val resolver: ContentResolver) {

    suspend fun calls(limit: Int = 1000): List<CallRecord> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Calls._ID,
            Calls.NUMBER,
            Calls.TYPE,
            Calls.DATE,
            Calls.DURATION,
            Calls.NUMBER_PRESENTATION,
            Calls.CACHED_NAME,
        )
        val uri = Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(Calls.LIMIT_PARAM_KEY, limit.toString())
            .build()
        resolver.query(uri, projection, null, null, "${Calls.DATE} DESC")?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        CallRecord(
                            id = c.getLong(0),
                            number = c.getString(1).orEmpty(),
                            type = typeOf(c.getInt(2)),
                            date = c.getLong(3),
                            durationSeconds = c.getLong(4),
                            presentation = presentationOf(c.getInt(5)),
                            cachedName = c.getString(6)?.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** Emits whenever a call is added to or removed from the history. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Calls.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun typeOf(type: Int): CallType = when (type) {
        Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> CallType.Incoming
        Calls.OUTGOING_TYPE -> CallType.Outgoing
        Calls.MISSED_TYPE -> CallType.Missed
        Calls.REJECTED_TYPE -> CallType.Rejected
        Calls.BLOCKED_TYPE -> CallType.Blocked
        Calls.VOICEMAIL_TYPE -> CallType.Voicemail
        else -> CallType.Other
    }

    private fun presentationOf(presentation: Int): Presentation = when (presentation) {
        Calls.PRESENTATION_RESTRICTED -> Presentation.Restricted
        Calls.PRESENTATION_PAYPHONE -> Presentation.Payphone
        Calls.PRESENTATION_ALLOWED -> Presentation.Allowed
        else -> Presentation.Unknown
    }
}
