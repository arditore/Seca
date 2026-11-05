package com.seca.core.suite

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the next backup is written, and what happens if it cannot be.
 *
 * The alarm is inexact on purpose. A backup has no minute it must happen on, and an exact alarm
 * is a thing to ask the owner's permission for; Android is free to fold this one in with whatever
 * else it was going to wake the phone for. It is set for the small hours, when a phone is
 * usually still and on a charger.
 */
object AutoBackupSchedule {

    /** Sets the next one, or cancels everything when no folder is chosen any more. */
    fun arm(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val settings = AutoBackupSettings(app)
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST,
            Intent(app, AutoBackupReceiver::class.java).setAction(ACTION_RUN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!settings.on) {
            alarms.cancel(pending)
            return
        }
        runCatching { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(settings), pending) }
    }

    /**
     * The next small hours that is a rhythm away from the last backup written.
     *
     * Counted from the last one rather than from now, which matters after the phone has been
     * off: an alarm does not survive a restart, and counting from the moment it is set again
     * would push a weekly backup another week out every time the phone was rebooted — quietly
     * turning "every week" into "whenever the phone last happened to be up at three".
     */
    private fun nextAt(settings: AutoBackupSettings): Long {
        val zone = ZoneId.systemDefault()
        val last = settings.lastRun
        val due = if (last == 0L) {
            LocalDate.now(zone)
        } else {
            Instant.ofEpochMilli(last).atZone(zone).toLocalDate().plusDays(settings.rhythm.days.toLong())
        }
        var at = due.atTime(HOUR).atZone(zone).toInstant().toEpochMilli()
        // Already owed, or past this morning's hour: the next one that has not happened yet.
        while (at <= System.currentTimeMillis()) at += DAY_MILLIS
        return at
    }

    /** Writes one, says so if it could not, and sets the next. */
    suspend fun runOnce(context: Context): AutoBackup.Result {
        val app = context.applicationContext
        val result = AutoBackup.run(app)
        when (result) {
            is AutoBackup.Result.Failed -> tell(app, result.reason)
            is AutoBackup.Result.Written -> Log.i(LOG_TAG, "wrote ${result.files.size} file(s)")
        }
        arm(app)
        return result
    }

    /**
     * Says that a backup did not happen.
     *
     * A backup that silently stops being written is the worst kind of backup, because it is
     * discovered on the day it was needed. Nothing is said when one succeeds: that is the
     * ordinary case and does not deserve a notification every morning.
     */
    private fun tell(context: Context, reason: String) {
        // Asked of the owner like any other notification, and refused as easily; a backup that
        // fails in silence is still written down, and the settings screen says so.
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.auto_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.auto_failed_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setCategory(Notification.CATEGORY_ERROR)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION, notification) }
    }

    const val ACTION_RUN = "com.seca.suite.AUTO_BACKUP"

    private const val CHANNEL = "seca-auto-backup"
    private const val NOTIFICATION = 7301
    private const val REQUEST = 7301
    private const val LOG_TAG = "SecaBackup"

    /** Three in the morning: a phone is usually still, and usually on a charger. */
    private val HOUR = LocalTime.of(3, 0)
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
}

/**
 * Hears the alarm, and sets it again after a restart or an update.
 *
 * An alarm does not survive either, so a backup that was set up months ago and never thought
 * about again would quietly stop at the next update — which is exactly when nobody is watching.
 */
class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AutoBackupSchedule.ACTION_RUN -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AutoBackupSchedule.runOnce(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> AutoBackupSchedule.arm(context)
        }
    }
}
