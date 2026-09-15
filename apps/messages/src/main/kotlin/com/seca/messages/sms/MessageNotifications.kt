package com.seca.messages.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.LocusId
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.util.Log
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.design.notification.NotificationAvatars
import com.seca.messages.LinkConversations
import com.seca.messages.MainActivity
import com.seca.messages.R
import com.seca.messages.link.LinkMessages

/**
 * The notifications of Seca Messages, drawn as Android draws conversations:
 * - one per conversation, with the sender's photo or initials, the unread
 *   messages, a reply field, "Mark as read" and, for a verification code,
 *   "Copy code"; a Seca Link message joins the same notification;
 * - each tied to a conversation shortcut, so Android files it under
 *   "Conversations" and the owner can make it a priority one;
 * - alerts apart: a scheduled message that could not leave, a changed
 *   Seca Link key;
 * - the quiet one Seca Link needs to keep listening.
 *
 * Android hides the text on the lock screen when the owner asked it to.
 */
internal object MessageNotifications {

    const val KEY_REPLY = "reply"
    const val EXTRA_THREAD = "thread_id"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_CODE = "code"
    private const val CHANNEL = "messages"
    private const val ALERTS = "message_alerts"
    private const val LINK_SERVICE = "link_service"
    private const val MAX_LINES = 8
    private const val TAG_SCHEDULED = "scheduled"
    private const val TAG_KEY_CHANGED = "link-key"
    private const val TAG_LINK_STOPPED = "link-stopped"
    private const val LOG_TAG = "SecaNotifications"

    fun notifyIncoming(context: Context, address: String, body: String) {
        val threadId = threadOf(context, address)
        val unread = unreadOf(context, threadId).ifEmpty { listOf(body to System.currentTimeMillis()) }
        showConversation(context, address, threadId, unread, code = OneTimeCode.find(body), encrypted = false)
    }

    /** A Seca Link message arrived, shown in the conversation's notification with its lock. */
    fun notifyLink(context: Context, number: String, body: String) {
        val threadId = threadOf(context, number)
        val unread = LinkMessages(context).unread(number).takeLast(MAX_LINES).map { LinkConversations.previewOf(context, it) to it.date }
            .ifEmpty { listOf(body to System.currentTimeMillis()) }
        showConversation(context, number, threadId, unread, code = OneTimeCode.find(body), encrypted = true)
    }

    private fun showConversation(
        context: Context,
        address: String,
        threadId: Long,
        messages: List<Pair<String, Long>>,
        code: String?,
        encrypted: Boolean,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(context, manager)
        if (!manager.areNotificationsEnabled()) return

        val contact = contactOf(context, address)
        val name = contact?.name ?: PhoneNumbers(PhoneNumbers.detectRegion(context)).display(address)
        val avatar = NotificationAvatars.bitmapFor(context, name, contact?.id)
        // Android takes no bitmap in the long-lived shortcut a conversation notification needs, and
        // without that shortcut it draws the app's icon in place of the contact: the avatar goes by
        // content URI instead, and the bitmap only serves if the file cannot be written.
        val icon = AvatarFiles.uriOf(context, address, avatar)?.let(Icon::createWithContentUri) ?: Icon.createWithBitmap(avatar)
        val sender = Person.Builder()
            .setName(name)
            .setKey(address)
            .setIcon(icon)
            .setImportant(contact != null)
            .apply { contact?.let { setUri(ContactsContract.Contacts.getLookupUri(it.id, it.lookupKey).toString()) } }
            .build()
        val style = Notification.MessagingStyle(Person.Builder().setName(context.getString(R.string.you)).build())
        messages.forEach { (text, date) -> style.addMessage(text, date, sender) }

        val notificationCode = codeOf(threadId, address)
        val shortcut = publishShortcut(context, threadId, address, name, sender, icon)
        // A reply field needs a mutable intent to carry the typed text; it only ever reaches this app.
        val reply = PendingIntent.getBroadcast(
            context,
            notificationCode,
            actionIntent(context, MessageActionReceiver.REPLY, threadId, address),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val markRead = PendingIntent.getBroadcast(
            context,
            notificationCode,
            actionIntent(context, MessageActionReceiver.MARK_READ, threadId, address),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(NotificationAvatars.accentOf(context))
            .setStyle(style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openIntent(context, threadId, address, notificationCode))
            .setAutoCancel(true)
            .setShortcutId(shortcut)
            .setLocusId(LocusId(shortcut))
            .apply {
                if (encrypted) setSubText(context.getString(R.string.encrypted_seca_link))
                // A verification code gets its own button, so it can be pasted without opening anything.
                code?.let { oneTimeCode ->
                    val copy = PendingIntent.getBroadcast(
                        context,
                        oneTimeCode.hashCode(),
                        actionIntent(context, MessageActionReceiver.COPY_CODE, threadId, address).putExtra(EXTRA_CODE, oneTimeCode),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    addAction(Notification.Action.Builder(null, context.getString(R.string.copy_code_named, oneTimeCode), copy).build())
                }
            }
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_message), context.getString(R.string.reply), reply)
                    .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.your_reply)).build())
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                    .build(),
            )
            .addAction(
                Notification.Action.Builder(null, context.getString(R.string.mark_read), markRead)
                    .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .build(),
            )
            .build()
        manager.notify(notificationCode, notification)
    }

    /** Clears a conversation's notification, once it has been read or answered. */
    fun cancel(context: Context, threadId: Long) {
        if (threadId < 0) return
        context.getSystemService(NotificationManager::class.java)?.cancel(codeOf(threadId, ""))
    }

    /** A scheduled message could not leave, most likely because Seca is no longer the SMS app. */
    fun notifyScheduledNotSent(context: Context, address: String) = alert(
        context = context,
        address = address,
        tag = TAG_SCHEDULED,
        title = context.getString(R.string.scheduled_not_sent),
        text = { name -> context.getString(R.string.scheduled_not_sent_text, name) },
    )

    /** A contact stopped having Seca Link: their keys are no longer published, so messages go back to SMS. */
    fun notifyLinkStopped(context: Context, address: String) = alert(
        context = context,
        address = address,
        tag = TAG_LINK_STOPPED,
        title = context.getString(R.string.link_stopped),
        text = { name -> context.getString(R.string.link_stopped_text, name) },
    )

    /** A contact's Seca Link key changed: a new phone or a reinstall, or someone trying to sit in between. */
    fun notifyKeyChanged(context: Context, address: String) = alert(
        context = context,
        address = address,
        tag = TAG_KEY_CHANGED,
        title = context.getString(R.string.key_changed),
        text = { name -> context.getString(R.string.key_changed_text, name) },
    )

    /** The notification Seca Link keeps while it listens: silent, at the bottom of the shade, and hideable. */
    fun linkServiceNotification(context: Context): Notification {
        context.getSystemService(NotificationManager::class.java)?.let { ensureChannels(context, it) }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, LINK_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(NotificationAvatars.accentOf(context))
            .setContentTitle("Seca Link")
            .setContentText(context.getString(R.string.link_service_text))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun alert(context: Context, address: String, tag: String, title: String, text: (String) -> String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(context, manager)
        if (!manager.areNotificationsEnabled()) return
        val threadId = threadOf(context, address)
        val contact = contactOf(context, address)
        val name = contact?.name ?: PhoneNumbers(PhoneNumbers.detectRegion(context)).display(address)
        val code = codeOf(threadId, address)
        val notification = Notification.Builder(context, ALERTS)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(NotificationAvatars.accentOf(context))
            .setLargeIcon(NotificationAvatars.iconFor(context, name, contact?.id))
            .setContentTitle(title)
            .setContentText(text(name))
            .setStyle(Notification.BigTextStyle().bigText(text(name)))
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(openIntent(context, threadId, address, code))
            .setAutoCancel(true)
            .build()
        // Its own tag, so it never replaces the conversation's messages.
        manager.notify(tag, code, notification)
    }

    private fun shortcutIdOf(threadId: Long, address: String): String =
        if (threadId >= 0) "conversation-$threadId" else "conversation-${address.hashCode()}"

    /**
     * The conversation as a long-lived shortcut, which Android needs to draw the
     * notification as a conversation: the contact's name and photo in place of
     * the app's own, and its place at the top of the shade.
     *
     * It cannot be kept off the launcher. A shortcut excluded from that surface
     * is dropped without a word — measured on the device: pushing one leaves no
     * shortcut at all, and Android then logs "added an invalid shortcut" and
     * falls back to a plain notification. So long-pressing the app's icon lists
     * recent conversations, as it does for every messaging app, and a
     * conversation deleted here takes its shortcut away with it.
     */
    private fun publishShortcut(context: Context, threadId: Long, address: String, name: String, person: Person, icon: Icon): String {
        val id = shortcutIdOf(threadId, address)
        val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return id
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CONVERSATION)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)
        runCatching {
            shortcuts.pushDynamicShortcut(
                ShortcutInfo.Builder(context, id)
                    .setShortLabel(name)
                    .setLongLived(true)
                    .setPerson(person)
                    .setIcon(icon)
                    .setIntent(intent)
                    .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
                    .build(),
            )
            // Android draws a notification as a conversation only while its shortcut exists.
        }.onFailure { refused -> Log.w(LOG_TAG, "conversation shortcut refused", refused) }
        return id
    }

    /** Lets go of a conversation's shortcut and notification, once the conversation itself is gone. */
    fun forget(context: Context, threadId: Long, address: String) {
        cancel(context, threadId)
        val shortcuts = context.getSystemService(ShortcutManager::class.java) ?: return
        runCatching { shortcuts.removeLongLivedShortcuts(listOf(shortcutIdOf(threadId, address))) }
    }

    private fun openIntent(context: Context, threadId: Long, address: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context,
        code,
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CONVERSATION)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(context: Context, action: String, threadId: Long, address: String) =
        Intent(context, MessageActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)

    private fun threadOf(context: Context, address: String): Long =
        runCatching { Telephony.Threads.getOrCreateThreadId(context, address) }.getOrNull() ?: -1L

    /** One number per conversation, so a new message replaces that conversation's notification. */
    private fun codeOf(threadId: Long, address: String): Int =
        if (threadId >= 0) (threadId % Int.MAX_VALUE).toInt() else address.hashCode()

    private class Contact(val id: Long, val lookupKey: String, val name: String)

    private fun contactOf(context: Context, address: String): Contact? = runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
            arrayOf(PhoneLookup._ID, PhoneLookup.LOOKUP_KEY, PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) Contact(it.getLong(0), it.getString(1).orEmpty(), it.getString(2).orEmpty()) else null }
    }.getOrNull()?.takeIf { it.name.isNotBlank() }

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

    /**
     * Creates the channels, or renames them: for a channel that already exists Android keeps the
     * owner's choices and only takes the new name and description, so they follow the phone's language.
     */
    private fun ensureChannels(context: Context, manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_messages_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_alerts_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(LINK_SERVICE, context.getString(R.string.channel_link), NotificationManager.IMPORTANCE_MIN).apply {
                description = context.getString(R.string.channel_link_desc)
                setShowBadge(false)
            },
        )
    }
}
