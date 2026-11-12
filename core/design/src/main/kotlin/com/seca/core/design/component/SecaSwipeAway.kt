package com.seca.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A row that is put away by swiping it aside (#51).
 *
 * It archives rather than deletes, and that is the whole design. A swipe is the one gesture
 * people make by accident and cannot see coming; where it cannot be undone it is a trap. A
 * conversation deleted here is gone from Android's shared message store with nothing to put
 * back, while one archived is a tap away in the archive. So the swipe does the reversible
 * thing and deleting stays behind the choosing, where it has to be asked for.
 *
 * One direction only. Two meanings on one gesture, told apart by which way the finger went, is
 * a thing people get wrong in a hurry.
 */
@Composable
fun SecaSwipeAway(
    onAway: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    /** False while the row is doing something else with the finger, such as being chosen. */
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val away = rememberUpdatedState(onAway)
    val state = rememberSwipeToDismissBoxState(
        // Most of the way across, not a third of it: a list that is being scrolled with a
        // thumb catches a short sideways movement often enough to matter.
        positionalThreshold = { width -> width * THRESHOLD },
    )
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            away.value()
            // The row leaves the list on its own once it is archived. Put back by hand so that
            // a list which does not drop it is not left with a blank where the row was.
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Nothing drawn until the finger has actually moved, so a row at rest is a row.
            if (state.targetValue == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(end = 28.dp),
                    )
                }
            }
        },
        content = { content() },
    )
}

/** How far across before letting go puts the row away. */
private const val THRESHOLD = 0.6f
