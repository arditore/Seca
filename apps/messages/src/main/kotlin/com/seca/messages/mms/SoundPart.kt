package com.seca.messages.mms

/**
 * Whether a part of a multimedia message is something to listen to.
 *
 * Not simply "does its type begin with audio". A recording made by another messaging app arrives
 * named by whatever that app felt like saying, and whatever the carrier's server rewrote on the
 * way: an Ogg announced as application/ogg, an m4a announced as nothing at all, an AMR with its
 * type lost entirely and only the file name left to go on.
 *
 * Getting this wrong is not cosmetic. A voice message that does not read as one is drawn as a
 * file with a paperclip and a "touch to open", which is a recording nobody listens to.
 */
internal object SoundPart {

    fun matches(contentType: String?, name: String?): Boolean {
        val type = contentType?.trim()?.substringBefore(';')?.lowercase().orEmpty()
        return when {
            type.startsWith("audio/") -> true
            type in TYPES -> true
            // Nothing useful was said about it, so the only thing left is what it is called.
            type.isEmpty() || type in VAGUE -> suffixOf(name) in SUFFIXES
            else -> false
        }
    }

    private fun suffixOf(name: String?): String =
        name?.trim()?.substringAfterLast('.', "")?.lowercase().orEmpty()

    /** Sounds that arrive announced as something other than a sound. */
    private val TYPES = setOf("application/ogg", "application/x-ogg", "application/x-m4a")

    /** Types that say nothing at all, where the file name is the only thing to go on. */
    private val VAGUE = setOf("application/octet-stream", "*/*", "application/unknown")

    /**
     * File endings that are a sound and nothing else.
     *
     * `.3gp` and `.mp4` are deliberately absent: they carry video as often as sound, and a film
     * drawn as a play button with no picture is a worse mistake than a recording drawn as a file.
     */
    private val SUFFIXES = setOf(
        "amr", "awb", "3ga", "aac", "m4a", "mp3", "ogg", "oga", "opus", "wav", "flac", "mka", "mid", "imy",
    )
}
