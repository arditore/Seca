package com.seca.messages.link

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.edit
import com.seca.core.link.SecaLink
import com.seca.core.link.message.Envelope
import com.seca.core.link.message.LinkPayload
import com.seca.core.link.nostr.NostrEvent
import com.seca.messages.ActiveConversation
import com.seca.messages.LinkConversations
import com.seca.messages.sms.LinkSms
import com.seca.messages.sms.MessageNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps Seca Link listening while it is on: one connection to each relay of
 * this phone, which first hands over what arrived while the phone was away,
 * then each envelope as it comes.
 *
 * Once the owner lets the app run in the background, it listens without any
 * notification. Until then it runs in the foreground with a quiet one: without
 * either, Android would cut the connection, and a phone without Google has no
 * push service to wake the app instead.
 */
class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var following: Job? = null
    private var sweeping: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started as a foreground service, it must show its notification at once, even to take it away just after.
        if (intent?.getBooleanExtra(EXTRA_FOREGROUND, false) == true && !startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val link = SecaLink(this)
        if (!link.settings.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (runsFreely(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else if (!startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Each start connects afresh, so a connection a relay dropped without a word is never waited on.
        following?.cancel()
        following = scope.launch { follow(link) }
        // Handshakes that came while the network was away get their session now, and their answer.
        scope.launch { LinkSms.connectWaiting(this@LinkService) }
        // Messages whose time is up leave this phone even while the app stays closed.
        if (sweeping?.isActive != true) sweeping = scope.launch { sweepExpired() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** False when Android refuses a foreground service at this moment, as it does from the background. */
    private fun startInForeground(): Boolean = runCatching {
        val notification = MessageNotifications.linkServiceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }.isSuccess

    /** One loop per relay, which connects again after a drop, waiting a little longer each time. */
    private suspend fun follow(link: SecaLink) = coroutineScope {
        val relays = link.settings.relays()
        LinkListening.keep(relays)
        relays.forEach { url ->
            launch {
                var wait = RETRY_MIN_MILLIS
                try {
                    while (isActive) {
                        if (link.network.available()) {
                            LinkListening.connecting(url)
                            link.inbox(url, since(), onOpen = { LinkListening.connected(url) }).catch { }.collect { event ->
                                wait = RETRY_MIN_MILLIS
                                LinkListening.envelope(url)
                                // Opening an envelope moves the session on: it is kept whole, even when listening restarts.
                                // One envelope that cannot be handled must never stop the listening.
                                withContext(NonCancellable) { runCatching { handle(link, event) } }
                            }
                        }
                        LinkListening.disconnected(url)
                        delay(wait)
                        wait = (wait * 2).coerceAtMost(RETRY_MAX_MILLIS)
                    }
                } finally {
                    LinkListening.disconnected(url)
                }
            }
        }
    }

    private suspend fun sweepExpired() {
        val conversations = LinkConversations(this)
        while (true) {
            runCatching { conversations.sweepExpired() }
            delay(SWEEP_MILLIS)
        }
    }

    private suspend fun handle(link: SecaLink, event: NostrEvent) {
        val messages = LinkMessages(this)
        val kept = event.kind == Envelope.KIND
        if (kept && messages.isSeen(event.id)) return
        val incoming = link.open(event) ?: return
        if (kept) {
            messages.markSeen(event.id)
            rememberLastSeen()
        }
        val number = incoming.number
        val now = System.currentTimeMillis()
        when (val payload = incoming.payload) {
            is LinkPayload.Text -> received(
                link,
                messages,
                LinkMessage(
                    id = payload.id,
                    number = number,
                    body = payload.body,
                    date = now,
                    status = LinkStatus.Received,
                    read = false,
                    replyTo = payload.replyTo,
                    expiresAt = LinkTimers.expiryAt(payload.expiresInSeconds, now),
                ),
            )
            // A photo or a voice message arrives piece by piece, and becomes a message once the last piece is in.
            is LinkPayload.MediaPart -> if (LinkMedia(this).accept(number, payload)) {
                received(
                    link,
                    messages,
                    LinkMessage(
                        id = payload.id,
                        number = number,
                        body = "",
                        date = now,
                        status = LinkStatus.Received,
                        read = false,
                        media = payload.mime,
                        expiresAt = LinkTimers.expiryAt(payload.expiresInSeconds, now),
                    ),
                )
            }
            is LinkPayload.Delivered -> messages.setStatus(payload.ids, LinkStatus.Delivered)
            is LinkPayload.Read -> messages.setStatus(payload.ids, LinkStatus.Read)
            LinkPayload.Typing -> LinkTyping.typing(number)
            is LinkPayload.Reaction -> messages.setTheirReaction(payload.targetId, number, payload.emoji.ifEmpty { null })
            // Either side sets how long messages are kept; both phones then follow it.
            is LinkPayload.ExpiryTimer -> LinkTimers.of(this).set(number, payload.seconds)
        }
    }

    private fun received(link: SecaLink, messages: LinkMessages, message: LinkMessage) {
        if (!messages.add(message)) return
        LinkTyping.stopped(message.number)
        // The conversation already on screen shows it; a notification would only repeat it.
        if (!ActiveConversation.isShown(message.number)) {
            runCatching { MessageNotifications.notifyLink(this, message.number, LinkConversations.previewOf(message)) }
        }
        // Sent aside, so the notices that follow on this relay do not wait for every relay to answer.
        scope.launch { runCatching { link.send(message.number, LinkPayload.Delivered(listOf(message.id))) } }
    }

    /** From a while before the last envelope seen: envelopes carry a time a few minutes early. */
    private fun since(): Long {
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        val last = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_LAST_SEEN, 0L)
        return if (last == 0L) now - FIRST_LOOKBACK_SECONDS else last - LOOKBACK_SECONDS
    }

    private fun rememberLastSeen() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit { putLong(KEY_LAST_SEEN, System.currentTimeMillis() / MILLIS_PER_SECOND) }
    }

    companion object {
        private const val NOTIFICATION_ID = 91
        private const val EXTRA_FOREGROUND = "foreground"
        private const val PREFS = "seca_link_service"
        private const val KEY_LAST_SEEN = "last_seen"
        private const val MILLIS_PER_SECOND = 1000
        private const val LOOKBACK_SECONDS = 2L * 60 * 60
        private const val FIRST_LOOKBACK_SECONDS = 24L * 60 * 60
        private const val RETRY_MIN_MILLIS = 5_000L
        private const val RETRY_MAX_MILLIS = 60_000L
        private const val SWEEP_MILLIS = 30_000L

        /**
         * Starts listening when Seca Link is on: in the background when the owner allowed it, in the
         * foreground otherwise. Android refuses both at some moments, which is harmless: the next opening
         * of the app starts it again.
         */
        fun start(context: Context) {
            if (!SecaLink(context).settings.enabled) return
            val intent = Intent(context, LinkService::class.java)
            runCatching {
                if (runsFreely(context)) {
                    context.startService(intent)
                } else {
                    context.startForegroundService(intent.putExtra(EXTRA_FOREGROUND, true))
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkService::class.java))
        }

        /** Whether Android lets the app run in the background: the owner lifted its battery optimisation. */
        fun runsFreely(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
}

/** Who is typing, as their last notice said; forgotten a few seconds after it. */
object LinkTyping {

    private const val SHOWN_MILLIS = 6_000L

    private val state = MutableStateFlow<Map<String, Long>>(emptyMap())
    val typing: StateFlow<Map<String, Long>> = state.asStateFlow()

    fun typing(number: String) = state.update { it + (number to System.currentTimeMillis()) }

    fun stopped(number: String) = state.update { it - number }

    fun isTyping(number: String, now: Long = System.currentTimeMillis()): Boolean = (typing.value[number] ?: 0L) > now - SHOWN_MILLIS
}
