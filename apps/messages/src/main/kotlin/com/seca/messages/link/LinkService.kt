package com.seca.messages.link

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.edit
import com.seca.core.link.SecaLink
import com.seca.core.link.message.Envelope
import com.seca.core.link.message.LinkPayload
import com.seca.core.link.nostr.NostrEvent
import com.seca.messages.sms.MessageNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * Keeps Seca Link listening while it is on: one connection to each relay of
 * this phone, which first hands over what arrived while the phone was away,
 * then each envelope as it comes.
 *
 * It runs in the foreground with a quiet notification. Without one Android
 * would cut the connection, and a phone without Google has no push service to
 * wake the app instead.
 */
class LinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var following: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val link = SecaLink(this)
        if (!link.settings.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (following?.isActive != true) following = scope.launch { follow(link) }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = MessageNotifications.linkServiceNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** One loop per relay, which connects again after a drop, waiting a little longer each time. */
    private suspend fun follow(link: SecaLink) = coroutineScope {
        link.settings.relays().forEach { url ->
            launch {
                var wait = RETRY_MIN_MILLIS
                while (isActive) {
                    if (link.network.available()) {
                        link.inbox(url, since()).catch { }.collect { event ->
                            wait = RETRY_MIN_MILLIS
                            handle(link, event)
                        }
                    }
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(RETRY_MAX_MILLIS)
                }
            }
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
        when (val payload = incoming.payload) {
            is LinkPayload.Text -> {
                val message = LinkMessage(payload.id, number, payload.body, System.currentTimeMillis(), LinkStatus.Received, read = false)
                if (!messages.add(message)) return
                LinkTyping.stopped(number)
                MessageNotifications.notifyLink(this, number, payload.body)
                link.send(number, LinkPayload.Delivered(listOf(payload.id)))
            }
            is LinkPayload.Delivered -> messages.setStatus(payload.ids, LinkStatus.Delivered)
            is LinkPayload.Read -> messages.setStatus(payload.ids, LinkStatus.Read)
            LinkPayload.Typing -> LinkTyping.typing(number)
        }
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
        private const val PREFS = "seca_link_service"
        private const val KEY_LAST_SEEN = "last_seen"
        private const val MILLIS_PER_SECOND = 1000
        private const val LOOKBACK_SECONDS = 2L * 60 * 60
        private const val FIRST_LOOKBACK_SECONDS = 24L * 60 * 60
        private const val RETRY_MIN_MILLIS = 5_000L
        private const val RETRY_MAX_MILLIS = 60_000L

        /** Starts listening when Seca Link is on; Android refuses it from the background, which is harmless. */
        fun start(context: Context) {
            if (!SecaLink(context).settings.enabled) return
            runCatching { context.startForegroundService(Intent(context, LinkService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinkService::class.java))
        }
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
