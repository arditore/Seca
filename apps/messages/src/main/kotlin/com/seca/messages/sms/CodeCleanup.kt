package com.seca.messages.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.edit

/** How long a text holding a verification code stays on the phone. */
enum class CodeLifetime(val label: String, val millis: Long?) {
    Always("Garder", null),
    QuarterHour("15 min", 15L * 60 * 1000),
    Hour("1 heure", 60L * 60 * 1000),
    Day("24 heures", 24L * 60 * 60 * 1000),
}

/**
 * Erases texts holding a verification code once they have served, when the
 * owner asks for it: a code left in the inbox is one more thing to read over a
 * shoulder, or to pull from a stolen phone.
 */
internal object CodeCleanup {

    private const val PREFS = "seca_codes"
    private const val KEY_LIFETIME = "lifetime"

    fun lifetime(context: Context): CodeLifetime {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIFETIME, null)
        return CodeLifetime.entries.firstOrNull { it.name == name } ?: CodeLifetime.Always
    }

    fun setLifetime(context: Context, lifetime: CodeLifetime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LIFETIME, lifetime.name) }
    }

    /** Plans the erasure of a text that just arrived with a code. A few minutes late is fine: no exact alarm. */
    fun schedule(context: Context, message: Uri, threadId: Long) {
        val millis = lifetime(context).millis ?: return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, MessageActionReceiver::class.java)
            .setAction(MessageActionReceiver.ERASE_CODE)
            .setData(message)
            .putExtra(MessageNotifications.EXTRA_THREAD, threadId)
        val pending = PendingIntent.getBroadcast(
            context,
            message.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + millis, pending)
    }

    /**
     * Erases the codes already past their time: those whose alarm a restart
     * dropped, and those from before the owner chose a delay. Returns how many.
     */
    suspend fun sweep(context: Context): Int {
        val millis = lifetime(context).millis ?: return 0
        val repository = MessagesRepository(context)
        val expired = repository.receivedBefore(System.currentTimeMillis() - millis).filter { OneTimeCode.find(it.body) != null }
        expired.forEach { repository.deleteMessage(it.id) }
        return expired.size
    }
}
