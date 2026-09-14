package com.seca.phone.screening

import android.content.Context
import androidx.core.content.edit
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** What happens to a call from a contact in a blocked profile. */
enum class BlockMode {
    /** Refused before it rings; it stays in the history among the declined calls. */
    Decline,

    /** Shown without ringing nor vibrating, for the owner to take or leave. */
    Silence,
}

/** How long blocked profiles stay blocked. */
enum class BlockFor {
    /** Until the owner lets them call again. */
    UntilLifted,
    OneHour,

    /** Until eight in the morning. */
    UntilMorning,

    /** Through the weekend, until Monday at eight. */
    UntilMonday,
    ;

    /** When a block started at [now] ends, in milliseconds; 0 when only the owner ends it. */
    fun endFrom(now: ZonedDateTime): Long = when (this) {
        UntilLifted -> 0L
        OneHour -> now.plusHours(1).toInstant().toEpochMilli()
        UntilMorning -> {
            val morning = now.toLocalDate().atTime(MORNING_HOUR, 0).atZone(now.zone)
            (if (now.isBefore(morning)) morning else morning.plusDays(1)).toInstant().toEpochMilli()
        }
        UntilMonday -> now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            .atTime(MORNING_HOUR, 0).atZone(now.zone).toInstant().toEpochMilli()
    }

    private companion object {
        const val MORNING_HOUR = 8
    }
}

/** How incoming calls are filtered. Telemarketing is blocked by default; unknown callers still ring. */
class ScreeningSettings(context: Context) {

    private val prefs = context.getSharedPreferences("seca_call_screening", Context.MODE_PRIVATE)

    var blockTelemarketing: Boolean
        get() = prefs.getBoolean(KEY_TELEMARKETING, true)
        set(value) = prefs.edit { putBoolean(KEY_TELEMARKETING, value) }

    var silenceUnknown: Boolean
        get() = prefs.getBoolean(KEY_UNKNOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_UNKNOWN, value) }

    /** The Seca Contacts profiles whose contacts cannot call right now, by id. A block with an end lifts itself. */
    var blockedProfiles: Set<String>
        get() = if (expired()) emptySet() else prefs.getStringSet(KEY_BLOCKED_PROFILES, null)?.toSet().orEmpty()
        set(value) {
            val restart = value.isEmpty() || expired()
            prefs.edit {
                putStringSet(KEY_BLOCKED_PROFILES, value)
                // Nothing left blocked, or an end already past: the next block starts with no end.
                if (restart) {
                    remove(KEY_BLOCKED_UNTIL)
                    remove(KEY_BLOCK_FOR)
                }
            }
        }

    var blockMode: BlockMode
        get() = BlockMode.entries.firstOrNull { it.name == prefs.getString(KEY_BLOCK_MODE, null) } ?: BlockMode.Decline
        set(value) = prefs.edit { putString(KEY_BLOCK_MODE, value.name) }

    /** How long the block lasts, as the owner picked it. */
    val blockFor: BlockFor
        get() = if (expired()) BlockFor.UntilLifted else BlockFor.entries.firstOrNull { it.name == prefs.getString(KEY_BLOCK_FOR, null) } ?: BlockFor.UntilLifted

    /** When the block ends, in milliseconds; 0 when it lasts until the owner lifts it. */
    val blockedUntil: Long
        get() = if (expired()) 0L else prefs.getLong(KEY_BLOCKED_UNTIL, 0L)

    /** Sets how long the block lasts, counted from [now]. */
    fun setBlockFor(choice: BlockFor, now: ZonedDateTime = ZonedDateTime.now()) = prefs.edit {
        putString(KEY_BLOCK_FOR, choice.name)
        putLong(KEY_BLOCKED_UNTIL, choice.endFrom(now))
    }

    private fun expired(): Boolean = prefs.getLong(KEY_BLOCKED_UNTIL, 0L).let { it > 0L && it <= System.currentTimeMillis() }

    private companion object {
        const val KEY_TELEMARKETING = "block_telemarketing"
        const val KEY_UNKNOWN = "silence_unknown"
        const val KEY_BLOCKED_PROFILES = "blocked_profiles"
        const val KEY_BLOCK_MODE = "block_mode"
        const val KEY_BLOCK_FOR = "block_for"
        const val KEY_BLOCKED_UNTIL = "blocked_until"
    }
}
