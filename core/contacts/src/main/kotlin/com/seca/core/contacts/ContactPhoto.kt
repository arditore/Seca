package com.seca.core.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
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

/** The side of a contact's picture, in pixels: enough for the large avatar, small enough to store. */
const val PHOTO_SIDE: Int = 512

private const val JPEG_QUALITY = 90
