package com.seca.core.design.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val BubbleSize = 64.dp

/**
 * The letter strip along a long list: touch it or slide along it to jump to a
 * letter. While the finger is on it, the letter shows large in an M3
 * Expressive bubble beside the finger, and each new letter ticks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SecaFastScroller(letters: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    var active by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    val currentLetters by rememberUpdatedState(letters)
    val currentOnSelect by rememberUpdatedState(onSelect)

    fun select(y: Float) {
        val list = currentLetters
        if (list.isEmpty()) return
        fingerY = y.coerceIn(0f, height.toFloat())
        val letter = list[((fingerY / height) * list.size).toInt().coerceIn(0, list.lastIndex)]
        if (letter != active) {
            active = letter
            currentOnSelect(letter)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(modifier) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .onSizeChanged { height = it.height.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        select(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            select(change.position.y)
                        }
                        active = null
                    }
                },
        ) {
            letters.forEach { letter ->
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        active?.let { letter ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = -(BubbleSize + 8.dp).roundToPx(),
                            y = (fingerY - BubbleSize.toPx() / 2).roundToInt(),
                        )
                    }
                    .size(BubbleSize)
                    .clip(MaterialShapes.Cookie4Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(letter, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
