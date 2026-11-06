package com.seca.messages.mms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A voice message from somebody else's app has to read as a voice message.
 *
 * These are the shapes recordings actually arrive in, which is not the shape the specification
 * suggests. The alternative to a test is sending oneself a recording from every messaging app
 * in the world, and there is no phone here running the ones that matter.
 */
class SoundPartTest {

    @Test
    fun `a recording that says what it is, in any of the ways apps say it`() {
        listOf(
            "audio/amr" to "voice.amr",
            "audio/3gpp" to "voice.3gp",
            "audio/mp4" to "AudioRecording.m4a",
            "audio/aac" to null,
            "audio/ogg" to "voice.ogg",
            "audio/mpeg" to "song.mp3",
            // The character set is part of the header and not part of the type.
            "audio/amr; charset=utf-8" to "voice.amr",
            "AUDIO/AMR" to "voice.amr",
        ).forEach { (type, name) ->
            assertTrue("$type / $name", SoundPart.matches(type, name))
        }
    }

    @Test
    fun `an Ogg announced as an application is still a recording`() {
        assertTrue(SoundPart.matches("application/ogg", "voice.ogg"))
        assertTrue(SoundPart.matches("application/x-ogg", null))
    }

    @Test
    fun `a recording with nothing said about it is known by its name`() {
        // A carrier's server that rewrote the type, or an app that never set one.
        assertTrue(SoundPart.matches("application/octet-stream", "Voice 001.m4a"))
        assertTrue(SoundPart.matches(null, "message.amr"))
        assertTrue(SoundPart.matches("", "note.opus"))
    }

    @Test
    fun `a film is not a recording, whatever it is called`() {
        // The expensive mistake: a video drawn as a play button with no picture.
        assertFalse(SoundPart.matches("video/3gpp", "clip.3gp"))
        assertFalse(SoundPart.matches("application/octet-stream", "clip.3gp"))
        assertFalse(SoundPart.matches("application/octet-stream", "clip.mp4"))
        assertFalse(SoundPart.matches("video/mp4", null))
    }

    @Test
    fun `a picture, a card and a document are left alone`() {
        assertFalse(SoundPart.matches("image/jpeg", "photo.jpg"))
        assertFalse(SoundPart.matches("text/x-vcard", "Alix.vcf"))
        assertFalse(SoundPart.matches("application/pdf", "bill.pdf"))
        assertFalse(SoundPart.matches("application/smil", "smil.xml"))
        assertFalse(SoundPart.matches(null, null))
    }

    @Test
    fun `a name that merely mentions a sound is not one`() {
        // The suffix is what comes after the last dot, not anything in the middle.
        assertFalse(SoundPart.matches("application/octet-stream", "mp3 collection.zip"))
        assertFalse(SoundPart.matches("application/octet-stream", "notes.amr.txt"))
    }
}
