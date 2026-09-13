package com.seca.core.link.relay

import com.seca.core.link.nostr.NostrEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import java.util.concurrent.TimeUnit
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
 * Talks to Nostr relays (NIP-01) over WebSocket, one short connection per
 * request. Only what Seca Link publishes goes out: public keys, and later
 * encrypted envelopes.
 */
class RelayClient {

    /** Sends [event] to [url] and waits for the relay's verdict. */
    suspend fun publish(url: String, event: NostrEvent): PublishResult {
        val request = runCatching { Request.Builder().url(url).build() }.getOrNull()
            ?: return PublishResult.Unreachable("Adresse invalide")
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

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val CONNECT_TIMEOUT_SECONDS = 10L
        const val NORMAL_CLOSURE = 1000

        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }
}
