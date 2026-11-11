package com.seca.messages

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A picture from a message: a still one, or one that moves.
 *
 * Both know their own size, because a bubble sizes itself from the proportions of what is in it.
 */
internal sealed class MessagePicture(val width: Int, val height: Int) {
    class Still(val bitmap: ImageBitmap) : MessagePicture(bitmap.width, bitmap.height)
    class Moving(val drawable: AnimatedImageDrawable) :
        MessagePicture(drawable.intrinsicWidth, drawable.intrinsicHeight)
}

/**
 * Reads the picture at [path], no larger than [maxEdge] pixels; null until it is ready.
 *
 * A GIF decoded into a bitmap is its first frame and nothing more, which is how every GIF in
 * every conversation arrived: a still picture of the beginning of a joke. Asked for a drawable
 * instead, the decoder hands back one that runs itself.
 */
@Composable
internal fun rememberMessagePicture(path: String, maxEdge: Int): MessagePicture? {
    val resolver = LocalContext.current.contentResolver
    val picture by produceState<MessagePicture?>(initialValue = null, path, maxEdge) {
        value = withContext(Dispatchers.IO) { decode(resolver, path, maxEdge) }
    }
    return picture
}

private fun decode(resolver: ContentResolver, path: String, maxEdge: Int): MessagePicture? = runCatching {
    val shrinkToFit = ImageDecoder.OnHeaderDecodedListener { decoder, info, _ ->
        val scale = min(1f, maxEdge.toFloat() / max(info.size.width, info.size.height))
        if (scale < 1f) {
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt(),
                (info.size.height * scale).roundToInt(),
            )
        }
    }
    when (val drawn = ImageDecoder.decodeDrawable(sourceOf(resolver, path), shrinkToFit)) {
        is AnimatedImageDrawable -> MessagePicture.Moving(
            drawn.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE },
        )
        // Decoded as a drawable and then read as a bitmap, so one decode covers both: asking
        // the decoder for each in turn reads the whole file twice.
        else -> MessagePicture.Still(drawn.toBitmap().asImageBitmap())
    }
}.getOrNull()

/**
 * Where a picture is to be read from.
 *
 * A Seca Link picture is a file this app wrote itself; a picture message's photo stays in the
 * phone's own store, where every messaging app can see it, and is named by a content address.
 * Both are shown the same way, so this is the one place that tells them apart.
 */
private fun sourceOf(resolver: ContentResolver, path: String): ImageDecoder.Source =
    if (path.startsWith("content://")) {
        ImageDecoder.createSource(resolver, path.toUri())
    } else {
        ImageDecoder.createSource(File(path))
    }

/**
 * Draws a picture.
 *
 * Compose draws bitmaps, so a moving picture is handed to the view the platform already knows
 * how to run one in rather than reimplemented here.
 */
@Composable
internal fun MessagePictureImage(
    picture: MessagePicture,
    description: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    when (picture) {
        is MessagePicture.Still -> Image(
            bitmap = picture.bitmap,
            contentDescription = description,
            contentScale = contentScale,
            modifier = modifier,
        )
        is MessagePicture.Moving -> AndroidView(
            factory = { context -> ImageView(context) },
            update = { view ->
                view.scaleType = if (contentScale == ContentScale.Crop) {
                    ImageView.ScaleType.CENTER_CROP
                } else {
                    ImageView.ScaleType.FIT_CENTER
                }
                view.contentDescription = description
                view.setImageDrawable(picture.drawable)
                // Starting it is the UI thread's to do, and this is the UI thread.
                if (!picture.drawable.isRunning) picture.drawable.start()
            },
            modifier = modifier,
        )
    }
}
