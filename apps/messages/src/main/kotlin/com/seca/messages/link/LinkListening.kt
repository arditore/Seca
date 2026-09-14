package com.seca.messages.link

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How listening to each relay stands right now, as the Seca Link status screen shows it. */
object LinkListening {

    enum class State { Connecting, Connected, Disconnected }

    /** [since] is when the state last changed; [lastEnvelopeAt], when an envelope last came, 0 when none has. */
    data class Relay(val state: State, val since: Long, val lastEnvelopeAt: Long = 0L)

    private val relays = MutableStateFlow<Map<String, Relay>>(emptyMap())
    val states: StateFlow<Map<String, Relay>> = relays.asStateFlow()

    /** Forgets relays the owner removed. */
    fun keep(urls: List<String>) = relays.update { current -> current.filterKeys { it in urls } }

    fun connecting(url: String) = set(url, State.Connecting)

    fun connected(url: String) = set(url, State.Connected)

    fun disconnected(url: String) = set(url, State.Disconnected)

    fun envelope(url: String) {
        val now = System.currentTimeMillis()
        relays.update { current ->
            val before = current[url]
            current + (url to Relay(State.Connected, if (before?.state == State.Connected) before.since else now, now))
        }
    }

    private fun set(url: String, state: State) {
        relays.update { current ->
            val before = current[url]
            if (before?.state == state) current else current + (url to Relay(state, System.currentTimeMillis(), before?.lastEnvelopeAt ?: 0L))
        }
    }
}
