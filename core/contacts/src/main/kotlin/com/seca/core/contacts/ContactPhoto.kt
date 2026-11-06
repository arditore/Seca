package com.seca.core.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a picked image into the square JPEG a contact's picture is kept as.
 *
 * Shrunk and cropped here rather than handed over whole: the picture is stored in the phone's
 * contacts database, which every app on the phone reads, and a ten-megapixel photograph has no
 * business being in there. The original stays in the gallery untouched — the photo picker hands
 * over this one image and nothing else of it.
 */
suspend fun squareJpeg(context: Context, uri: Uri, side: Int = PHOTO_SIDE): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = side.toFloat() / min(info.size.width, info.size.height)
            if (scale < 1f) {
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).toInt()),
                    max(1, (info.size.height * scale).toInt()),
                )
            }
            // Decoded into ordinary memory, since it is about to be cropped and compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val edge = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - edge) / 2,
            (decoded.height - edge) / 2,
            edge,
            edge,
        )
        ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }.getOrNull()
}

/**
 * A picked image, small enough to turn around under a finger.
 *
 * Loaded once and cropped from afterwards, so dragging and pinching never touch the disk. The
 * longest edge is held down to [CROP_EDGE]: far more than the square that comes out of it, so
 * a close crop is still sharp, and far less than what a camera writes.
 */
suspend fun forCropping(context: Context, uri: Uri, longest: Int = CROP_EDGE): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = longest.toFloat() / max(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).toInt()),
                        max(1, (info.size.height * scale).toInt()),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

/**
 * The square the owner framed, as the JPEG a contact's picture is kept as.
 *
 * The rectangle is in the picture's own pixels and is pushed back inside it rather than
 * refused: a pinch that ran a few pixels past the edge is an accident of a finger, not a
 * reason to hand back nothing.
 */
suspend fun croppedJpeg(picture: Bitmap, left: Int, top: Int, side: Int, out: Int = PHOTO_SIDE): ByteArray? =
    withContext(Dispatchers.IO) {
        runCatching {
            val edge = side.coerceIn(1, min(picture.width, picture.height))
            val x = left.coerceIn(0, picture.width - edge)
            val y = top.coerceIn(0, picture.height - edge)
            val square = Bitmap.createBitmap(picture, x, y, edge, edge)
            val sized = if (edge == out) square else square.scale(out, out)
            ByteArrayOutputStream().use { stream ->
                sized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                stream.toByteArray()
            }
        }.getOrNull()
    }

/** The side of a contact's picture, in pixels: enough for the large avatar, small enough to store. */
const val PHOTO_SIDE: Int = 512

/** The longest edge an image is held to while it is being framed. */
const val CROP_EDGE: Int = 1600

private const val JPEG_QUALITY = 90
