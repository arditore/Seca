package com.seca.core.link.relay

import com.seca.core.link.nostr.NostrEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** What a relay made of one event. */
sealed interface PublishResult {
    data object Accepted : PublishResult

    /** The relay answered and declined: full, paying, or a rule of its own. */
    data class Refused(val reason: String) : PublishResult

    /** No answer: offline, blocked, or not a relay. */
    data class Unreachable(val reason: String) : PublishResult
}

/**
 * Talks to Nostr relays (NIP-01) over WebSocket. Publishing and fetching use a
 * short connection each; following an inbox keeps one open. Only what Seca Link
 * publishes goes out: public keys and encrypted envelopes.
 */
class RelayClient {

    /** Sends [event] to [url] and waits for the relay's verdict. */
    suspend fun publish(url: String, event: NostrEvent): PublishResult {
        val request = requestFor(url) ?: return PublishResult.Unreachable("Adresse invalide")
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("[\"EVENT\",${event.toJson()}]")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val message = runCatching { JSONArray(text) }.getOrNull() ?: return
                        // Other messages, a request to authenticate for one, are not an answer to this event.
                        if (message.optString(0) != "OK" || message.optString(1) != event.id) return
                        val result = if (message.optBoolean(2)) {
                            PublishResult.Accepted
                        } else {
                            PublishResult.Refused(message.optString(3))
                        }
                        if (continuation.isActive) continuation.resume(result)
                        webSocket.close(NORMAL_CLOSURE, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (continuation.isActive) continuation.resume(PublishResult.Unreachable("Connexion fermée"))
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (continuation.isActive) {
                            continuation.resume(PublishResult.Unreachable(t.message ?: t.javaClass.simpleName))
                        }
                    }
                }
                val socket = http.newWebSocket(request, listener)
                continuation.invokeOnCancellation { socket.cancel() }
            }
        } ?: PublishResult.Unreachable("Pas de réponse")
    }

    /**
     * The events stored on [url] that match [filter], a NIP-01 filter written
     * as JSON, up to the relay's end of stored events. Null when the relay
     * could not be reached. Signatures are for the caller to check.
     */
    suspend fun fetch(url: String, filter: String): List<NostrEvent>? {
        val request = requestFor(url) ?: return null
        val subscription = "seca-${subscriptions.incrementAndGet()}"
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<List<NostrEvent>?> { continuation ->
                val events = mutableListOf<NostrEvent>()
                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("[\"REQ\",\"$subscription\",$filter]")
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val message = runCatching { JSONArray(text) }.getOrNull() ?: return
                        if (message.optString(1) != subscription) return
                        when (message.optString(0)) {
                            "EVENT" -> message.optJSONObject(2)?.let(NostrEvent::fromJson)?.let(events::add)
                            "EOSE", "CLOSED" -> {
                                if (continuation.isActive) continuation.resume(events.toList())
                                webSocket.send("[\"CLOSE\",\"$subscription\"]")
                                webSocket.close(NORMAL_CLOSURE, null)
                            }
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (continuation.isActive) continuation.resume(events.toList().ifEmpty { null })
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
                val socket = http.newWebSocket(request, listener)
                continuation.invokeOnCancellation { socket.cancel() }
            }
        }
    }

    /**
     * Follows [url] for the events matching [filter]: those kept first, then
     * new ones as they arrive. The flow ends when the connection drops; the
     * caller connects again. A relay that asks for authentication (NIP-42), as
     * some do before handing out encrypted envelopes, gets [authenticate]'s
     * answer, and the request is made again.
     */
    fun subscribe(url: String, filter: String, authenticate: suspend (relay: String, challenge: String) -> NostrEvent): Flow<NostrEvent> =
        callbackFlow {
            val request = requestFor(url)
            if (request == null) {
                close()
                return@callbackFlow
            }
            val subscription = "seca-${subscriptions.incrementAndGet()}"
            val ask = "[\"REQ\",\"$subscription\",$filter]"
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(ask)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val message = runCatching { JSONArray(text) }.getOrNull() ?: return
                    when (message.optString(0)) {
                        "EVENT" -> if (message.optString(1) == subscription) {
                            message.optJSONObject(2)?.let(NostrEvent::fromJson)?.let { trySend(it) }
                        }
                        "AUTH" -> {
                            val challenge = message.optString(1)
                            launch {
                                webSocket.send("[\"AUTH\",${authenticate(url, challenge).toJson()}]")
                                webSocket.send(ask)
                            }
                        }
                        // Closed for want of authentication: asked again once the AUTH answer is sent.
                        "CLOSED" -> if (message.optString(1) == subscription && !message.optString(2).startsWith("auth-required")) {
                            close()
                        }
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    close()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    close()
                }
            }
            val socket = http.newWebSocket(request, listener)
            awaitClose { socket.close(NORMAL_CLOSURE, null) }
        }

    private fun requestFor(url: String): Request? = runCatching { Request.Builder().url(url).build() }.getOrNull()

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val PING_SECONDS = 45L
        const val NORMAL_CLOSURE = 1000

        val subscriptions = AtomicInteger()

        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // Keeps a followed inbox alive through routers that drop quiet connections.
                .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }
}
