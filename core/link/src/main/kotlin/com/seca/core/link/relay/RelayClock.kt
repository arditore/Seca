package com.seca.core.link.relay

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * The time as the relays tell it. Relays turn away a typing notice a few
 * seconds old, and a phone whose clock lags behind would see every one
 * refused: the offset read from the relays' own answers puts it right.
 */
object RelayClock {

    /** Under this, the difference is network delay rather than a wrong clock. */
    private const val TRUSTED_DRIFT_SECONDS = 3L

    @Volatile
    private var offsetSeconds = 0L

    /** Now, in seconds, as the relays count it. */
    fun now(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND + offsetSeconds

    /** Reads the Date header of a relay's answer. */
    fun observe(dateHeader: String?) {
        val server = runCatching { ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }.getOrNull() ?: return
        val offset = server - System.currentTimeMillis() / MILLIS_PER_SECOND
        offsetSeconds = if (abs(offset) >= TRUSTED_DRIFT_SECONDS) offset else 0L
    }

    private const val MILLIS_PER_SECOND = 1000
}
