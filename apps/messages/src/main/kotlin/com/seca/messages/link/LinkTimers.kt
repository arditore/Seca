package com.seca.messages.link

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How long the new messages of each Seca Link conversation are kept, by
 * number. Either side may set it; the other phone follows, so both let the
 * same messages go.
 */
class LinkTimers private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("seca_link_timers", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val timers: StateFlow<Map<String, Int>> = state.asStateFlow()

    /** Seconds; 0 keeps the messages. */
    operator fun get(number: String): Int = state.value[number] ?: 0

    fun set(number: String, seconds: Int) {
        prefs.edit { if (seconds > 0) putInt(number, seconds) else remove(number) }
        state.value = read()
    }

    private fun read(): Map<String, Int> = prefs.all.mapNotNull { (number, seconds) -> (seconds as? Int)?.let { number to it } }.toMap()

    companion object {
        @Volatile
        private var instance: LinkTimers? = null

        fun of(context: Context): LinkTimers = instance ?: synchronized(this) {
            instance ?: LinkTimers(context.applicationContext).also { instance = it }
        }

        private const val MINUTE = 60
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR
        private const val WEEK = 7 * DAY

        /** What the owner may choose, in seconds. */
        val Choices = listOf(0, 5 * MINUTE, HOUR, DAY, WEEK)

        fun label(seconds: Int): String = when {
            seconds <= 0 -> "Désactivés"
            seconds < HOUR -> "${seconds / MINUTE} min"
            seconds < DAY -> "${seconds / HOUR} h"
            seconds < WEEK -> "${seconds / DAY} j"
            else -> "${seconds / WEEK} sem."
        }

        /** When a message kept [seconds] from [from] goes; 0 when it is kept. */
        fun expiryAt(seconds: Int, from: Long): Long = if (seconds > 0) from + seconds * 1000L else 0L
    }
}
