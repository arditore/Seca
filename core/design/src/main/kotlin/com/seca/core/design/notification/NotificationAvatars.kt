package com.seca.core.design.notification

import android.content.ContentUris
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.provider.ContactsContract
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

/**
 * Avatars for notifications, drawn outside Compose as the apps draw them: the
 * contact's photo when there is one, else their initials on a colour of the
 * phone's own palette. Android crops them into a circle.
 */
object NotificationAvatars {

    private const val SIZE = 192
    private const val TWO_LETTERS = 0.36f
    private const val ONE_LETTER = 0.44f
    private const val TONES = 3

    /** The contact's photo, or the initials of [name] on the colour of [tone]. */
    fun iconFor(context: Context, name: String, contactId: Long? = null, tone: Int = 0): Icon =
        Icon.createWithBitmap(photoOf(context, contactId) ?: initialsOf(context, name, tone))

    /** The accent Android gives notifications of the Seca apps, taken from the wallpaper. */
    fun accentOf(context: Context): Int = context.getColor(android.R.color.system_accent1_600)

    /** The first letters of the first two words; "#" for a number or a name without letters. */
    fun initials(name: String): String {
        val letters = name.trim().split(Regex("\\s+")).mapNotNull { word -> word.firstOrNull { it.isLetter() } }.take(2)
        return if (letters.isEmpty()) "#" else letters.joinToString("").uppercase()
    }

    private fun photoOf(context: Context, contactId: Long?): Bitmap? {
        if (contactId == null) return null
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val photo = runCatching {
            ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, false)?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        val side = minOf(photo.width, photo.height)
        val square = Bitmap.createBitmap(photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side)
        return if (side == SIZE) square else square.scale(SIZE, SIZE)
    }

    private fun initialsOf(context: Context, name: String, tone: Int): Bitmap {
        val (background, foreground) = toneColors(context, tone)
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val text = initials(name)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            textAlign = Paint.Align.CENTER
            textSize = SIZE * if (text.length > 1) TWO_LETTERS else ONE_LETTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText(text, SIZE / 2f, SIZE / 2f - (paint.descent() + paint.ascent()) / 2, paint)
        return bitmap
    }

    /** The containers of the Material You palette, light or dark with the phone, as the apps use them. */
    private fun toneColors(context: Context, tone: Int): Pair<Int, Int> {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val (light, deep) = when (Math.floorMod(tone, TONES)) {
            0 -> android.R.color.system_accent1_100 to android.R.color.system_accent1_700
            1 -> android.R.color.system_accent3_100 to android.R.color.system_accent3_700
            else -> android.R.color.system_accent2_100 to android.R.color.system_accent2_700
        }
        val (text, textDark) = when (Math.floorMod(tone, TONES)) {
            0 -> android.R.color.system_accent1_900 to android.R.color.system_accent1_100
            1 -> android.R.color.system_accent3_900 to android.R.color.system_accent3_100
            else -> android.R.color.system_accent2_900 to android.R.color.system_accent2_100
        }
        return if (dark) context.getColor(deep) to context.getColor(textDark) else context.getColor(light) to context.getColor(text)
    }
}
