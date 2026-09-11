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
import java.util.Date

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
    Icon(icon, contentDescription = type.label, tint = tint, modifier = modifier)
}

/** How a call went, in words. */
internal val CallType.label: String
    get() = when (this) {
        CallType.Incoming -> "Appel reçu"
        CallType.Outgoing -> "Appel émis"
        CallType.Missed -> "Appel manqué"
        CallType.Rejected -> "Appel refusé"
        CallType.Blocked -> "Appel bloqué"
        CallType.Voicemail -> "Messagerie vocale"
        CallType.Other -> "Appel"
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
internal fun callerLabel(call: CallRecord, numbers: PhoneNumbers): String = when {
    call.presentation == Presentation.Restricted -> "Numéro masqué"
    call.presentation == Presentation.Payphone -> "Cabine téléphonique"
    !call.callable -> "Numéro inconnu"
    else -> call.cachedName ?: numbers.display(call.number)
}

/** The time of day, in the phone's 12- or 24-hour setting. */
internal fun timeOf(context: Context, millis: Long): String = DateFormat.getTimeFormat(context).format(Date(millis))

/** "3 min 12 s", "45 s", or nothing for a call never picked up. */
internal fun durationOf(seconds: Long): String? = when {
    seconds <= 0 -> null
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
    else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
}
