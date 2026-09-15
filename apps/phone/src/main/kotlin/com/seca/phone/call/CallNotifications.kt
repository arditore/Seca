package com.seca.phone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.design.notification.NotificationAvatars
import com.seca.phone.MainActivity
import com.seca.phone.R

/**
 * The notifications of calls, drawn as Android draws a phone's own:
 * - an incoming call rings with the caller's photo and the answer and decline
 *   buttons, filling the screen when the phone is locked;
 * - the call in progress stays as a call notification, which puts Android's
 *   call chip in the status bar: one tap from anywhere brings the call back;
 * - missed calls, with "Call back" and "Message".
 *
 * None of them makes a sound: Android keeps ringing and vibrating as usual.
 */
internal object CallNotifications {

    private const val INCOMING = "incoming_calls"
    private const val ONGOING = "ongoing_calls"
    private const val MISSED = "missed_calls"
    const val NOTIFICATION_ID = 7
    private const val MISSED_ID = 8

    /** Decoding a photo takes a moment; each caller's is drawn once per call. */
    private val icons = HashMap<String, Icon>()

    fun update(context: Context, calls: List<CallView>, service: Service?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(context, manager)
        val primary = calls.primary()
        when {
            primary == null || primary.state == Call.STATE_DISCONNECTED -> {
                service?.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                manager.cancel(NOTIFICATION_ID)
                icons.clear()
            }
            primary.state == Call.STATE_RINGING || primary.state == Call.STATE_SIMULATED_RINGING ->
                manager.notify(NOTIFICATION_ID, incoming(context, manager, primary))
            else -> showOngoing(context, manager, service, primary)
        }
    }

    private fun incoming(context: Context, manager: NotificationManager, view: CallView): Notification {
        val title = view.title(context, CallSession.numbers)
        val screen = screenIntent(context, answer = false)
        val builder = base(context, INCOMING)
            .setContentTitle(title)
            .setContentText(view.subtitle(CallSession.numbers) ?: context.getString(R.string.call_status_incoming))
            .setContentIntent(screen)
        if (manager.fullScreenAllowed()) {
            builder
                .setFullScreenIntent(screen, true)
                .setStyle(
                    Notification.CallStyle.forIncomingCall(
                        personOf(context, view, title),
                        declineIntent(context),
                        screenIntent(context, answer = true),
                    ),
                )
        } else {
            // Android refuses a call-style notification without a full-screen intent: plain buttons then.
            builder
                .setLargeIcon(iconOf(context, view, title))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.decline), declineIntent(context)).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.answer), screenIntent(context, answer = true)).build())
        }
        return builder.build()
    }

    /**
     * As the in-call service's own notification, the call gets the chip in the status bar and
     * a coloured call notification. Should Android refuse, a plain one still leads back to the call.
     */
    private fun showOngoing(context: Context, manager: NotificationManager, service: Service?, view: CallView) {
        val shown = service != null && runCatching {
            service.startForeground(NOTIFICATION_ID, ongoing(context, view, callStyle = true), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        }.isSuccess
        if (!shown) manager.notify(NOTIFICATION_ID, ongoing(context, view, callStyle = false))
    }

    private fun ongoing(context: Context, view: CallView, callStyle: Boolean): Notification {
        val title = view.title(context, CallSession.numbers)
        val answered = view.connectTime > 0 && view.state == Call.STATE_ACTIVE
        val hangUp = broadcast(context, CallActionReceiver.HANG_UP, 3)
        val builder = base(context, ONGOING)
            .setContentTitle(title)
            .setContentText(view.status(context))
            .setUsesChronometer(answered)
            .setShowWhen(answered)
            .setWhen(if (answered) view.connectTime else System.currentTimeMillis())
            .setContentIntent(screenIntent(context, answer = false))
        if (callStyle) {
            builder
                .setStyle(Notification.CallStyle.forOngoingCall(personOf(context, view, title), hangUp))
                .setColorized(true)
        } else {
            builder
                .setLargeIcon(iconOf(context, view, title))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.hang_up), hangUp).build())
        }
        return builder.build()
    }

    /** Missed calls, as Android asks the phone app to show them; "Call back" and "Message" when one number called. */
    fun showMissed(context: Context, count: Int, number: String?, callBack: PendingIntent?, clear: PendingIntent?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(context, manager)
        val known = number?.takeIf { it.isNotBlank() }
        val contact = known?.let { lookUp(context, it) }
        val who = contact?.name ?: known?.let { PhoneNumbers(PhoneNumbers.detectRegion(context)).display(it) }
        val builder = Notification.Builder(context, MISSED)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setColor(NotificationAvatars.accentOf(context))
            .setContentTitle(context.resources.getQuantityString(R.plurals.missed_calls, count, count))
            .setContentText(who ?: context.getString(R.string.missed_open_history))
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(historyIntent(context))
            .setAutoCancel(true)
            .setShowWhen(true)
        clear?.let(builder::setDeleteIntent)
        if (who != null) builder.setLargeIcon(NotificationAvatars.iconFor(context, who, contact?.id))
        if (known != null) {
            builder
                .addAction(Notification.Action.Builder(null, context.getString(R.string.call_back), callBack ?: dialIntent(context, known)).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.message), messageIntent(context, known)).build())
        }
        manager.notify(MISSED_ID, builder.build())
    }

    fun cancelMissed(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(MISSED_ID)
    }

    private fun base(context: Context, channel: String) = Notification.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_call)
        .setColor(NotificationAvatars.accentOf(context))
        .setCategory(Notification.CATEGORY_CALL)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setOnlyAlertOnce(true)

    /** The caller as Android shows them, with their photo; a contact is tied to its card, so Do Not Disturb lets favourites through. */
    private fun personOf(context: Context, view: CallView, title: String): Person {
        val caller = view.caller
        return Person.Builder()
            .setName(title)
            .setIcon(iconOf(context, view, title))
            .setImportant(caller != null)
            .apply { caller?.let { setUri(ContactsContract.Contacts.getLookupUri(it.contactId, it.lookupKey).toString()) } }
            .build()
    }

    private fun iconOf(context: Context, view: CallView, title: String): Icon =
        icons.getOrPut("${view.number}|${view.caller?.contactId}") {
            NotificationAvatars.iconFor(context, title, view.caller?.contactId, view.caller?.tone ?: 0)
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

    private fun historyIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        4,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun dialIntent(context: Context, number: String): PendingIntent = PendingIntent.getActivity(
        context,
        5,
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun messageIntent(context: Context, number: String): PendingIntent = PendingIntent.getActivity(
        context,
        6,
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun declineIntent(context: Context) = broadcast(context, CallActionReceiver.DECLINE, 2)

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        code,
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private class Contact(val id: Long, val name: String)

    private fun lookUp(context: Context, number: String): Contact? = runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(PhoneLookup._ID, PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) Contact(it.getLong(0), it.getString(1).orEmpty()) else null }
    }.getOrNull()?.takeIf { it.name.isNotBlank() }

    /**
     * Creates the channels, or renames them: for a channel that already exists Android keeps the
     * owner's choices and only takes the new name and description, so they follow the phone's language.
     */
    private fun ensureChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(INCOMING, context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_incoming_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ONGOING, context.getString(R.string.ongoing_call), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_ongoing_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_missed_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }
}
