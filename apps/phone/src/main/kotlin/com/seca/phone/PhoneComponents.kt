package com.seca.phone

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.design.SecaIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.annotation.StringRes
import com.seca.core.model.uiLocale
import androidx.compose.ui.res.stringResource

/** The arrow or symbol telling how a call went, missed calls in the error colour. */
@Composable
internal fun CallTypeIcon(type: CallType, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val icon = when (type) {
        CallType.Incoming -> SecaIcons.CallReceived
        CallType.Outgoing -> SecaIcons.CallMade
        CallType.Missed, CallType.Rejected -> SecaIcons.CallMissed
        CallType.Blocked -> SecaIcons.Block
        CallType.Voicemail -> SecaIcons.Voicemail
        CallType.Other -> SecaIcons.Phone
    }
    val tint = when (type) {
        CallType.Missed -> colors.error
        CallType.Rejected, CallType.Blocked, CallType.Other -> colors.onSurfaceVariant
        else -> colors.primary
    }
    Icon(icon, contentDescription = stringResource(type.label), tint = tint, modifier = modifier)
}

/** How a call went, in words. */
@get:StringRes
internal val CallType.label: Int
    get() = when (this) {
        CallType.Incoming -> R.string.call_incoming
        CallType.Outgoing -> R.string.call_outgoing
        CallType.Missed -> R.string.call_missed
        CallType.Rejected -> R.string.call_rejected
        CallType.Blocked -> R.string.call_blocked
        CallType.Voicemail -> R.string.voicemail
        CallType.Other -> R.string.call_other
    }

/** A caller who is not in the contacts: a neutral disc, so it never borrows a profile's colour. */
@Composable
internal fun UnknownAvatar(modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Icon(
            SecaIcons.Contacts,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Whether the call can be returned: a hidden or unknown caller cannot. */
internal val CallRecord.callable: Boolean
    get() = presentation == Presentation.Allowed && number.isNotBlank()

/** How to name a caller who is not a known contact. */
internal fun callerLabel(context: Context, call: CallRecord, numbers: PhoneNumbers): String = when {
    call.presentation == Presentation.Restricted -> context.getString(R.string.hidden_number)
    call.presentation == Presentation.Payphone -> context.getString(R.string.payphone)
    !call.callable -> context.getString(R.string.unknown_number)
    else -> call.cachedName ?: numbers.display(call.number)
}

/** Kept between rows, and rebuilt when the language or the 12/24-hour setting changes. */
private var timeFormat: Triple<Locale, Boolean, DateTimeFormatter>? = null

/** The time of day, in the phone's 12- or 24-hour setting. */
internal fun timeOf(context: Context, millis: Long): String {
    val locale = uiLocale()
    val hours24 = DateFormat.is24HourFormat(context)
    val kept = timeFormat
    val formatter = if (kept != null && kept.first == locale && kept.second == hours24) {
        kept.third
    } else {
        DateTimeFormatter.ofPattern(if (hours24) "HH:mm" else "h:mm a", locale)
            .also { timeFormat = Triple(locale, hours24, it) }
    }
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(formatter)
}

/** "3 min 12 s", "45 s", or nothing for a call never picked up. */
internal fun durationOf(seconds: Long): String? = when {
    seconds <= 0 -> null
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
}
