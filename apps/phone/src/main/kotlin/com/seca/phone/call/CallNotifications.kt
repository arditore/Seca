package com.seca.phone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.telecom.Call
import com.seca.phone.R

/**
 * The notifications of a call. An incoming call gets a call-style
 * notification that turns into the full screen when the phone is locked;
 * the call in progress gets a quiet one with "Raccrocher".
 *
 * Neither makes a sound: Android keeps ringing and vibrating as usual.
 */
internal object CallNotifications {

    private const val INCOMING = "incoming_calls"
    private const val ONGOING = "ongoing_calls"
    private const val NOTIFICATION_ID = 7

    fun update(context: Context, calls: List<CallView>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(manager)
        val primary = calls.primary()
        when {
            primary == null || primary.state == Call.STATE_DISCONNECTED -> manager.cancel(NOTIFICATION_ID)
            primary.state == Call.STATE_RINGING || primary.state == Call.STATE_SIMULATED_RINGING ->
                manager.notify(NOTIFICATION_ID, incoming(context, manager, primary))
            else -> manager.notify(NOTIFICATION_ID, ongoing(context, primary))
        }
    }

    private fun incoming(context: Context, manager: NotificationManager, view: CallView): Notification {
        val title = view.title(CallSession.numbers)
        val screen = screenIntent(context, answer = false)
        val builder = Notification.Builder(context, INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(title)
            .setContentText(view.subtitle(CallSession.numbers) ?: "Appel entrant")
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(screen)
        if (manager.canUseFullScreenIntent()) {
            val person = Person.Builder().setName(title).setImportant(true).build()
            builder
                .setFullScreenIntent(screen, true)
                .setStyle(Notification.CallStyle.forIncomingCall(person, declineIntent(context), screenIntent(context, answer = true)))
        } else {
            // Android refuses a call-style notification without a full-screen intent: plain buttons then.
            builder
                .addAction(Notification.Action.Builder(null, "Refuser", declineIntent(context)).build())
                .addAction(Notification.Action.Builder(null, "Répondre", screenIntent(context, answer = true)).build())
        }
        return builder.build()
    }

    private fun ongoing(context: Context, view: CallView): Notification {
        val answered = view.connectTime > 0 && view.state == Call.STATE_ACTIVE
        return Notification.Builder(context, ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(view.title(CallSession.numbers))
            .setContentText(view.status())
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(answered)
            .setShowWhen(answered)
            .setWhen(if (answered) view.connectTime else System.currentTimeMillis())
            .setContentIntent(screenIntent(context, answer = false))
            .addAction(Notification.Action.Builder(null, "Raccrocher", broadcast(context, CallActionReceiver.HANG_UP, 3)).build())
            .build()
    }

    /**
     * Opens the in-call screen; with [answer], the screen answers as it opens.
     * Answering goes through the screen rather than a broadcast because Android
     * no longer lets a notification button open an activity through a receiver.
     */
    private fun screenIntent(context: Context, answer: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (answer) 1 else 0,
        Intent(context, InCallActivity::class.java)
            .putExtra(InCallActivity.EXTRA_ANSWER, answer)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun declineIntent(context: Context) = broadcast(context, CallActionReceiver.DECLINE, 2)

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels(manager: NotificationManager) {
        if (manager.getNotificationChannel(INCOMING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(INCOMING, "Appels entrants", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Pour répondre ou refuser. La sonnerie reste celle d'Android."
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
        if (manager.getNotificationChannel(ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ONGOING, "Appel en cours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Pendant un appel, pour revenir à l'écran d'appel ou raccrocher."
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }
    }
}
