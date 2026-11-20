package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** One of the two things a swipe can do to a row. */
class SwipeAction(
    val onAct: () -> Unit,
    val icon: ImageVector,
    val label: String,
    /** Drawn in the error colours, for something that cannot simply be swiped back. */
    val destructive: Boolean = false,
)

/**
 * A row acted on by swiping it aside (#51, #85).
 *
 * Swiping towards the start archives, and that direction keeps that meaning. It was the only
 * one at first, deliberately, because a swipe is the gesture people make by accident: a
 * conversation archived is a tap away in the archive, while one deleted is gone from Android's
 * shared store with nothing to put back.
 *
 * A user who had tried it asked for the second direction anyway, saying one way round is
 * unintuitive and that two is what messaging apps do. Fair, so there are two. The assignment is
 * the other way round from the one he suggested, and that is the one deliberate part: the
 * gesture people have already learned keeps doing the reversible thing, and the direction
 * nobody has made yet is the one that deletes. Rewiring a learned gesture into a destructive
 * act is how somebody loses a conversation on the first day.
 *
 * The destructive direction also asks. It is the caller that asks, because what to say depends
 * on what is being deleted, so this only reports the swipe.
 */
@Composable
fun SecaSwipeAway(
    toStart: SwipeAction,
    modifier: Modifier = Modifier,
    /** The other way, for lists that offer something there. */
    toEnd: SwipeAction? = null,
    /** False while the row is doing something else with the finger, such as being chosen. */
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val start = rememberUpdatedState(toStart)
    val end = rememberUpdatedState(toEnd)
    // The row's own width, so how far the finger went can be weighed against it.
    var width by remember { mutableFloatStateOf(0f) }
    // The state asks its own question below, and to answer it the question needs the state.
    // It is only ever asked while somebody is dragging, long after this composition has handed
    // the state over, so a box filled in just below is enough.
    val live = remember { arrayOfNulls<SwipeToDismissBoxState>(1) }
    val state = rememberSwipeToDismissBoxState(
        // Distance is the whole of it, and this is the half of that the gesture does not decide
        // on its own: a swipe that is thrown rather than carried also dismisses, however short
        // it was, and a thumb scrolling a list ends its arc in exactly such a flick (#92).
        confirmValueChange = { going ->
            going == SwipeToDismissBoxValue.Settled || carried(live[0], width)
        },
        // Most of the way across, not a third of it: a list that is being scrolled with a
        // thumb catches a short sideways movement often enough to matter.
        positionalThreshold = { distance -> distance * THRESHOLD },
    )
    live[0] = state
    LaunchedEffect(state.currentValue) {
        val action = when (state.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> start.value
            SwipeToDismissBoxValue.StartToEnd -> end.value
            SwipeToDismissBoxValue.Settled -> null
        } ?: return@LaunchedEffect
        action.onAct()
        // Snapped back rather than animated. Animating moves currentValue part way, which is
        // the key this effect is keyed on, so the effect driving the animation cancels itself
        // and leaves the row stranded wide open on its own background. Invisible while the
        // only direction archived, because the row left the list before anyone could see it;
        // plain to see on the direction that only asks a question and keeps the row.
        state.snapTo(SwipeToDismissBoxValue.Settled)
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { width = it.width.toFloat() },
        enableDismissFromStartToEnd = toEnd != null,
        backgroundContent = {
            // Nothing drawn until the finger has actually moved, so a row at rest is a row.
            val going = state.targetValue
            val action = when (going) {
                SwipeToDismissBoxValue.EndToStart -> toStart
                SwipeToDismissBoxValue.StartToEnd -> toEnd
                SwipeToDismissBoxValue.Settled -> null
            }
            if (action != null) {
                val container = if (action.destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
                val tint = if (action.destructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                val side = if (going == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
                val gap = if (going == SwipeToDismissBoxValue.EndToStart) {
                    Modifier.padding(end = ICON_GAP)
                } else {
                    Modifier.padding(start = ICON_GAP)
                }
                Box(
                    contentAlignment = side,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(container),
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = tint,
                        modifier = gap,
                    )
                }
            }
        },
        content = { content() },
    )
}

/**
 * Whether the row was really carried, rather than brushed quickly.
 *
 * Nothing measured yet, or an offset the gesture has not set, means getting out of the way: a
 * guard against an accident must never be what stops a deliberate swipe from working.
 */
private fun carried(state: SwipeToDismissBoxState?, width: Float): Boolean {
    if (state == null || width <= 0f) return true
    val distance = runCatching { abs(state.requireOffset()) }.getOrNull() ?: return true
    return distance >= width * THRESHOLD
}

/** How far across before letting go acts on the row. */
private const val THRESHOLD = 0.6f

/** The icon sits in from the edge the finger came from, rather than against it. */
private val ICON_GAP = 28.dp
