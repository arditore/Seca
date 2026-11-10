package com.seca.core.suite

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Says out loud when one Seca app hands its data to another.
 *
 * The three apps are kept apart on purpose — Seca Contacts and Seca Phone cannot reach the
 * network at all — and then `com.seca.permission.BACKUP` opens a door between them that hands
 * over everything an app holds. That door is how one backup covers three apps and it cannot
 * simply be closed. It can stop being silent.
 *
 * During a backup the owner sees this once, right after pressing the button, and thinks
 * nothing of it. Outside one they see a Seca app reading their address book with no backup in
 * sight, which is the thing the current design gives no way to notice.
 *
 * It is posted by the app **being read**, never by the one asking, so a dishonest sibling
 * cannot suppress it. It does not stop such a sibling; it makes a quiet pull impossible, which
 * is the part that can be fixed. Issue #27.
 */
internal object BackupDoorNotice {

    /**
     * Posted after a part has been served.
     *
     * [caller] is read from the calling package by the provider rather than taken from an
     * extra: an app asking for the address book is not the right authority on who it is.
     */
    fun served(context: Context, caller: String, portable: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.door_channel),
                // A record rather than an alert: it happens every night a backup is written.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val said = context.getString(
            if (portable) R.string.door_handed_portable else R.string.door_handed,
            appName(context, caller),
            appName(context, context.packageName),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(context.getString(R.string.door_title))
            .setContentText(said)
            .setStyle(NotificationCompat.BigTextStyle().bigText(said))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            // One per part rather than one a day: a backup of three apps shows three, which is
            // an honest count of what was handed over.
            .build()
        runCatching { manager.notify(CHANNEL + caller, NOTIFICATION, notification) }
    }

    /** What the owner calls the app, not what the system calls its package. */
    private fun appName(context: Context, packageName: String): String = runCatching {
        val packages = context.packageManager
        packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private const val CHANNEL = "seca-backup-door"
    private const val NOTIFICATION = 7302
}
