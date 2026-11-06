package com.seca.contacts

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.seca.core.contacts.croppedJpeg
import com.seca.core.contacts.forCropping
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Framing a picture before it becomes somebody's face.
 *
 * A photograph is rarely a portrait. Taking the middle of it, which is what happened before,
 * puts a chin and a shoulder in a circle the size of a thumbnail. Here the owner drags and
 * pinches until the face is in the round window, and what they see is exactly what is kept:
 * the preview is drawn from the same rectangle the crop is cut from, rather than from a
 * transform that then has to be reproduced somewhere else and kept in step.
 */
@Composable
internal fun PhotoCropDialog(uri: Uri, onCancel: () -> Unit, onCropped: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val picture by produceState<Bitmap?>(initialValue = null, uri) { value = forCropping(context, uri) }
    val frame = remember(picture) { picture?.let(::Frame) }
    var busy by remember(picture) { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.frame_photo),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.frame_photo_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.large),
                ) {
                    if (frame == null) CircularProgressIndicator() else Framing(frame)
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        enabled = frame != null && !busy,
                        onClick = {
                            val held = frame ?: return@TextButton
                            busy = true
                            scope.launch {
                                val jpeg = croppedJpeg(
                                    picture = held.picture,
                                    left = held.left.roundToInt(),
                                    top = held.top.roundToInt(),
                                    side = held.side.roundToInt(),
                                )
                                busy = false
                                if (jpeg != null) onCropped(jpeg)
                            }
                        },
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}

/** The picture, the square cut out of it, and the fingers that move that square. */
@Composable
private fun Framing(frame: Frame) {
    val colors = MaterialTheme.colorScheme
    val image: ImageBitmap = remember(frame) { frame.picture.asImageBitmap() }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(frame) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    frame.moved(window = size.width.toFloat(), centroid = centroid, pan = pan, zoom = zoom)
                }
            },
    ) {
        drawImage(
            image = image,
            srcOffset = IntOffset(frame.left.roundToInt(), frame.top.roundToInt()),
            srcSize = IntSize(frame.side.roundToInt().coerceAtLeast(1), frame.side.roundToInt().coerceAtLeast(1)),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
        dimOutsideTheCircle(colors.scrim)
    }
}

/**
 * Where the square is, in the picture's own pixels.
 *
 * Never in screen pixels: a rotation, a fold or a resized window changes those and would drag
 * the framing with them. Two numbers and a width, and the screen is only ever a view onto them.
 */
private class Frame(val picture: Bitmap) {

    private val widest = min(picture.width, picture.height)
    private val closest = max(MIN_SQUARE, widest / DEEPEST_ZOOM)

    var side by mutableFloatStateOf(widest.toFloat())
        private set
    var left by mutableFloatStateOf((picture.width - widest) / 2f)
        private set
    var top by mutableFloatStateOf((picture.height - widest) / 2f)
        private set

    /**
     * A pinch and a drag, in one move.
     *
     * The point between the fingers is found in the picture first, and put back under them
     * afterwards. Without that the picture snaps towards its own middle as it is zoomed, which
     * is exactly where nobody is trying to look.
     */
    fun moved(window: Float, centroid: Offset, pan: Offset, zoom: Float) {
        if (window <= 0f) return
        val holdingX = left + centroid.x / window * side
        val holdingY = top + centroid.y / window * side
        val wanted = (side / zoom).coerceIn(closest.toFloat(), widest.toFloat())
        side = wanted
        left = (holdingX - centroid.x / window * wanted - pan.x / window * wanted)
            .coerceIn(0f, picture.width - wanted)
        top = (holdingY - centroid.y / window * wanted - pan.y / window * wanted)
            .coerceIn(0f, picture.height - wanted)
    }
}

/**
 * Everything outside the round window is darkened.
 *
 * One path with a hole in it rather than a scrim and an erase: erasing needs a layer of its
 * own, and without one it takes the picture away with the scrim.
 */
private fun DrawScope.dimOutsideTheCircle(scrim: Color) {
    val radius = size.minDimension / 2f
    val middle = Offset(size.width / 2f, size.height / 2f)
    val outside = Path().apply {
        addRect(Rect(Offset.Zero, size))
        addOval(Rect(center = middle, radius = radius))
        fillType = PathFillType.EvenOdd
    }
    drawPath(outside, color = scrim.copy(alpha = OUTSIDE))
    drawCircle(color = Color.White.copy(alpha = EDGE), radius = radius, center = middle, style = Stroke(width = 2f))
}

/** The smallest square anyone needs to frame, in the picture's pixels. */
private const val MIN_SQUARE = 96

/** How far a pinch may go in: past this a face is pixels. */
private const val DEEPEST_ZOOM = 6

private const val OUTSIDE = 0.55f
private const val EDGE = 0.7f
