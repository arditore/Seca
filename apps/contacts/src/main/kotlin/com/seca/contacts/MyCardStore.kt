package com.seca.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** The phone owner's own card. */
data class MyCard(
    /** Empty until the owner names it; the card then reads "My card". */
    val name: String = "",
    /** Numbers the owner typed in, for lines whose SIM does not carry its number. */
    val numbers: List<String> = emptyList(),
    /** Changes whenever the photo does, so the screen reloads it. */
    val photoVersion: Long = 0,
)

/**
 * Keeps "My card" in this app's private storage, never in the shared
 * contacts: other apps cannot read it, and it is not synced anywhere.
 */
class MyCardStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("seca_my_card", Context.MODE_PRIVATE)
    private val photoFile: File get() = File(context.filesDir, "my_card_photo.jpg")

    fun load(): MyCard = MyCard(
        name = prefs.getString(KEY_NAME, null).orEmpty(),
        numbers = prefs.getString(KEY_NUMBERS, null)?.let { json ->
            JSONArray(json).let { array -> (0 until array.length()).map(array::getString) }
        }.orEmpty(),
        photoVersion = if (photoFile.exists()) photoFile.lastModified() else 0,
    )

    fun save(name: String, numbers: List<String>) {
        prefs.edit {
            putString(KEY_NAME, name.trim())
            putString(KEY_NUMBERS, JSONArray(numbers.map(String::trim).filter(String::isNotEmpty)).toString())
        }
    }

    suspend fun photo(): Bitmap? = withContext(Dispatchers.IO) {
        if (photoFile.exists()) BitmapFactory.decodeFile(photoFile.path) else null
    }

    /**
     * Keeps a square, shrunk copy of the picked image. The photo picker hands
     * over this one image only, and the original stays in the gallery.
     */
    suspend fun setPhoto(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = PHOTO_SIZE.toFloat() / min(info.size.width, info.size.height)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).toInt()),
                        max(1, (info.size.height * scale).toInt()),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val side = min(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
            photoFile.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        }.isSuccess
    }

    fun removePhoto() {
        photoFile.delete()
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_NUMBERS = "numbers"
        const val PHOTO_SIZE = 512
        const val JPEG_QUALITY = 90
    }
}
