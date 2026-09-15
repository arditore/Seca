package com.seca.phone

import android.content.Context
import android.content.Intent
import android.telecom.Call
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaIcons
import com.seca.phone.call.CallSession
import com.seca.phone.call.InCallActivity
import com.seca.phone.call.elapsed
import com.seca.phone.call.primary
import com.seca.phone.call.status
import com.seca.phone.call.title
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

private const val TICK_MILLIS = 1000L

/** Opens the call screen, from anywhere in the app. */
internal fun openCallScreen(context: Context) {
    context.startActivity(Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Whether a call is in progress, ringing or on hold. */
internal fun callInProgress(): Boolean = CallSession.calls.value.any { it.state != Call.STATE_DISCONNECTED }

/**
 * A pill at the top of the app while a call goes on: who, for how long, a tap
 * to go back to the call, and hanging up right there.
 */
@Composable
internal fun OngoingCallBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val calls by CallSession.calls.collectAsState()
    val call = calls.primary()?.takeIf { it.state != Call.STATE_DISCONNECTED } ?: return
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(TICK_MILLIS)
            value = System.currentTimeMillis()
        }
    }
    val answered = call.connectTime > 0 && call.state == Call.STATE_ACTIVE
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { openCallScreen(context) },
        shape = CircleShape,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(SecaIcons.Phone, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = call.title(context, CallSession.numbers),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.tap_to_return,
                        if (answered) elapsed((now - call.connectTime).coerceAtLeast(0) / TICK_MILLIS) else call.status(context),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledIconButton(
                onClick = { CallSession.hangUp(call.call) },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.error, contentColor = colors.onError),
            ) {
                Icon(SecaIcons.CallEnd, contentDescription = stringResource(R.string.hang_up))
            }
        }
    }
}
