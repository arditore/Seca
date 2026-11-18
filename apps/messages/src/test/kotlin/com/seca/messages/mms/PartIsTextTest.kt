package com.seca.messages.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A part is words only if it says so and its bytes agree.
 *
 * Two users reported a photo or a video arriving as a screen of replacement characters with a
 * few readable letters in it — which is exactly what a video looks like decoded as UTF-8 — and
 * one of them sent the screenshot that proved it (#72, #55). A part had been taken at its word,
 * so bytes that were never text went into the conversation as a sentence.
 *
 * Whatever the underlying mistake is, and it has not been found yet, this is the backstop: a
 * message cannot be made to show binary as a sentence by claiming a type.
 */
class PartIsTextTest {

    @Test
    fun `words are words`() {
        val part = MmsPdu.Part("text/plain", "text.txt", "<text>", "Bonjour, à tout à l'heure".toByteArray())
        assertTrue(part.isText)
        assertEquals("Bonjour, à tout à l'heure", part.text)
    }

    @Test
    fun `an empty text part is still text`() {
        assertTrue(MmsPdu.Part("text/plain", null, null, ByteArray(0)).isText)
    }

    @Test
    fun `a video claiming to be text is not text`() {
        // The head of a 3GP file: a length, "ftyp", a brand. Not valid UTF-8 and full of NUL.
        val video = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte()) +
            byteArrayOf(0x33, 0x67, 0x70, 0x34, 0x00, 0x00, 0x03, 0x00.toByte())
        val part = MmsPdu.Part("text/plain", "clip.3gp", "<clip>", video)
        assertFalse("bytes with NUL in them are not a sentence", part.isText)
        assertNull(part.text)
    }

    @Test
    fun `bytes that are not valid UTF-8 are not text`() {
        // A lone continuation byte and a truncated sequence: what a JPEG looks like here.
        val broken = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x41, 0xC3.toByte())
        assertFalse(MmsPdu.Part("text/plain", "photo.jpg", null, broken).isText)
    }

    @Test
    fun `a binary part keeps its own type and is never text`() {
        assertFalse(MmsPdu.Part("image/jpeg", "photo.jpg", null, byteArrayOf(1, 2, 3)).isText)
        assertFalse(MmsPdu.Part("video/3gpp", "clip.3gp", null, byteArrayOf(1, 2, 3)).isText)
        assertFalse(MmsPdu.Part("application/smil", null, null, "<smil/>".toByteArray()).isText)
    }

    /** Carriers do not agree on capitals, and the checks used to. */
    @Test
    fun `the type is read whatever its capitals`() {
        assertTrue(MmsPdu.Part("TEXT/PLAIN", null, null, "hello".toByteArray()).isText)
        assertTrue(MmsPdu.Part("Text/Plain", null, null, "hello".toByteArray()).isText)
    }
}
