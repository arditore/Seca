package com.seca.phone.call

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Whether the call screen's big buttons answer a swipe instead of a tap (#79). */
internal val LocalSwipeToAct = staticCompositionLocalOf { false }

/**
 * Makes a control act on an upward swipe rather than on a tap.
 *
 * Asked for twice, in an issue and by e-mail, by people who had answered and ended calls they
 * never meant to: a phone against a cheek or in a pocket presses whatever is under it, and the
 * three buttons that matter most are the ones that are hardest to undo.
 *
 * The tap is replaced rather than kept alongside, which is the point. A control that still
 * answers a stray touch protects nobody, so with this on there is nothing here a tap can do.
 *
 * A screen reader is the exception, and deliberately: dragging is not a gesture TalkBack
 * performs for its user, so the semantics action stays, and activating the button there does
 * what it always did. Somebody using a screen reader is not the person pocket-dialling.
 */
@Composable
internal fun Modifier.actOnSwipeUp(label: String, bySwipe: Boolean, onAct: () -> Unit): Modifier {
    if (!bySwipe) return clickable(onClickLabel = label, role = Role.Button, onClick = onAct)

    var lifted by remember { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(lifted, label = "lift")
    val reach = with(LocalDensity.current) { REACH.toPx() }
    return offset { IntOffset(0, shown.roundToInt()) }
        .semantics {
            this.role = Role.Button
            onClick(label) {
                onAct()
                true
            }
        }
        .pointerInput(onAct, reach) {
            detectVerticalDragGestures(
                onDragEnd = {
                    val far = -lifted >= reach
                    lifted = 0f
                    if (far) onAct()
                },
                onDragCancel = { lifted = 0f },
            ) { _, travelled ->
                // Upwards only, and it stops a little past the point where it acts: the button
                // following the finger is what says the gesture has been understood, and a
                // button that kept climbing would leave the screen.
                lifted = (lifted + travelled).coerceIn(-reach * OVERRUN, 0f)
            }
        }
}

/** How far up the button has to go. Short enough for one thumb, long enough not to happen by itself. */
private val REACH = 56.dp

/** How much further it will follow the finger after that, so the gesture does not feel stuck. */
private const val OVERRUN = 1.4f
