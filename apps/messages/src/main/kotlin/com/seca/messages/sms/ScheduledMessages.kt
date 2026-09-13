package com.seca.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** A message written now, which the phone sends at [at]. */
data class ScheduledMessage(val id: Long, val address: String, val body: String, val at: Long)

/**
 * Messages waiting for their time. They stay in this app's private storage,
 * and an alarm wakes the app to send each one. Alarms do not survive a
 * restart, so [rearmAll] sets them again.
 */
class ScheduledMessages(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Soonest first. */
    fun all(): List<ScheduledMessage> = synchronized(lock) { read() }.sortedBy { it.at }

    fun find(id: Long): ScheduledMessage? = all().firstOrNull { it.id == id }

    fun add(address: String, body: String, at: Long): ScheduledMessage {
        val message = synchronized(lock) {
            val id = prefs.getLong(KEY_NEXT_ID, 1L)
            ScheduledMessage(id, address, body, at).also { write(read() + it, nextId = id + 1) }
        }
        arm(message)
        return message
    }

    /** Takes a message out of the queue, and its alarm with it. */
    fun remove(id: Long) {
        synchronized(lock) { write(read().filterNot { it.id == id }) }
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntentOf(id))
    }

    /** Sets every alarm again; a message whose time has passed leaves at once. */
    fun rearmAll() = all().forEach(::arm)

    private fun arm(message: ScheduledMessage) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntentOf(message.id)
        // With the owner's permission the message leaves on the minute; without it, Android may delay it a little.
        if (alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.at, pending)
                return
            } catch (revoked: SecurityException) {
                // The permission was taken back between the check and the call.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, message.at, pending)
    }

    private fun pendingIntentOf(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        (id % Int.MAX_VALUE).toInt(),
        Intent(context, MessageActionReceiver::class.java)
            .setAction(MessageActionReceiver.SEND_SCHEDULED)
            // The id in the data keeps each message's alarm apart from the others.
            .setData(Uri.fromParts(URI_SCHEME, id.toString(), null))
            .putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun read(): List<ScheduledMessage> = runCatching {
        val array = JSONArray(prefs.getString(KEY_MESSAGES, null) ?: "[]")
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            ScheduledMessage(item.getLong("id"), item.getString("address"), item.getString("body"), item.getLong("at"))
        }
    }.getOrDefault(emptyList())

    private fun write(messages: List<ScheduledMessage>, nextId: Long? = null) {
        val array = JSONArray()
        messages.forEach {
            array.put(JSONObject().put("id", it.id).put("address", it.address).put("body", it.body).put("at", it.at))
        }
        prefs.edit {
            putString(KEY_MESSAGES, array.toString())
            nextId?.let { putLong(KEY_NEXT_ID, it) }
        }
    }

    companion object {
        const val EXTRA_ID = "scheduled_id"
        private const val PREFS = "seca_scheduled"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_NEXT_ID = "next_id"
        private const val URI_SCHEME = "seca-scheduled"

        // The screen and the alarm may both change the queue at the same moment.
        private val lock = Any()
    }
}
