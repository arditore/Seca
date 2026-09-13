package com.seca.messages.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony
import android.provider.Telephony.Sms
import com.seca.core.contacts.PhoneNumbers
import com.seca.messages.MainActivity
import com.seca.messages.R

/**
 * One notification per conversation, showing who wrote and what: the unread
 * messages of the conversation, with a reply field and "Marquer comme lu".
 * Android hides the text on the lock screen when the owner asked it to.
 */
internal object MessageNotifications {

    const val KEY_REPLY = "reply"
    const val EXTRA_THREAD = "thread_id"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_CODE = "code"
    private const val CHANNEL = "messages"
    private const val MAX_LINES = 8

    fun notifyIncoming(context: Context, address: String, body: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        if (!manager.areNotificationsEnabled()) return

        val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull() ?: -1L
        val name = nameOf(context, address) ?: PhoneNumbers(PhoneNumbers.detectRegion(context)).display(address)
        val sender = Person.Builder().setName(name).setKey(address).build()
        val style = Notification.MessagingStyle(Person.Builder().setName("Moi").build())
        val unread = unreadOf(context, threadId)
        if (unread.isEmpty()) {
            style.addMessage(body, System.currentTimeMillis(), sender)
        } else {
            unread.forEach { (text, date) -> style.addMessage(text, date, sender) }
        }

        val code = codeOf(threadId, address)
        val open = PendingIntent.getActivity(
            context,
            code,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_CONVERSATION)
                .putExtra(EXTRA_THREAD, threadId)
                .putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // A reply field needs a mutable intent to carry the typed text; it only ever reaches this app.
        val reply = PendingIntent.getBroadcast(
            context,
            code,
            actionIntent(context, MessageActionReceiver.REPLY, threadId, address),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markRead = PendingIntent.getBroadcast(
            context,
            code,
            actionIntent(context, MessageActionReceiver.MARK_READ, threadId, address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // A verification code gets its own button, so it can be pasted without opening anything.
        val oneTimeCode = OneTimeCode.find(body)
        val copyCode = oneTimeCode?.let {
            PendingIntent.getBroadcast(
                context,
                it.hashCode(),
                actionIntent(context, MessageActionReceiver.COPY_CODE, threadId, address).putExtra(EXTRA_CODE, it),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setShortcutId(null)
            .apply {
                if (oneTimeCode != null && copyCode != null) {
                    addAction(Notification.Action.Builder(null, "Copier le code $oneTimeCode", copyCode).build())
                }
            }
            .addAction(
                Notification.Action.Builder(null, "Répondre", reply)
                    .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Répondre").build())
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                    .build(),
            )
            .addAction(
                Notification.Action.Builder(null, "Marquer comme lu", markRead)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .build(),
            )
            .build()
        manager.notify(code, notification)
    }

    /** Clears a conversation's notification, once it has been read or answered. */
    fun cancel(context: Context, threadId: Long) {
        if (threadId < 0) return
        context.getSystemService(NotificationManager::class.java)?.cancel(codeOf(threadId, ""))
    }

    private fun actionIntent(context: Context, action: String, threadId: Long, address: String) =
        Intent(context, MessageActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)

    /** One number per conversation, so a new message replaces that conversation's notification. */
    private fun codeOf(threadId: Long, address: String): Int =
        if (threadId >= 0) (threadId % Int.MAX_VALUE).toInt() else address.hashCode()

    private fun nameOf(context: Context, address: String): String? = runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
            arrayOf(PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The unread messages of the conversation, oldest first, the latest few only. */
    private fun unreadOf(context: Context, threadId: Long): List<Pair<String, Long>> {
        if (threadId < 0) return emptyList()
        return runCatching {
            context.contentResolver.query(
                Sms.CONTENT_URI,
                arrayOf(Sms.BODY, Sms.DATE),
                "${Sms.THREAD_ID} = ? AND ${Sms.READ} = 0 AND ${Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Sms.MESSAGE_TYPE_INBOX.toString()),
                "${Sms.DATE} DESC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext() && size < MAX_LINES) add(c.getString(0).orEmpty() to c.getLong(1))
                }.reversed()
            }
        }.getOrNull().orEmpty()
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Les messages reçus, avec leur expéditeur et leur texte."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }
}
