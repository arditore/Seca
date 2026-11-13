package com.seca.phone

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The captions under the buttons on the call screen have a ceiling, and nothing used to enforce
 * it.
 *
 * They sit in a grid three across, which on the narrowest phone the apps support leaves a little
 * under a third of the width for each. "Add call" fits there; "Ajouter un appel" was drawn as
 * "Ajouter un ", cut mid-word with no ellipsis to even hint at it, and it shipped that way in
 * French, Polish and German because the screen had only ever been looked at in English.
 *
 * Counting characters is a rough stand-in for measuring text, and a deliberate one: it is wrong
 * about a word of thin letters and right about the thing that actually goes wrong, which is
 * somebody translating a one-word caption into a phrase. The screen draws two lines and an
 * ellipsis now, so passing this is not the only thing keeping the words whole - it is what makes
 * the next long translation argue for itself instead of arriving unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CallControlLabelsTest {

    @Test
    fun captionsFitUnderTheirButtons() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val tooLong = mutableListOf<String>()
        for (language in LANGUAGES) {
            RuntimeEnvironment.setQualifiers("+$language")
            for (caption in CAPTIONS) {
                val text = context.getString(caption)
                if (text.length > CEILING) tooLong += "$language: \"$text\" (${text.length})"
            }
        }
        assertTrue(
            "A caption on the call screen is longer than $CEILING characters and will be cut or " +
                "wrapped under its button:\n" + tooLong.joinToString("\n"),
            tooLong.isEmpty(),
        )
    }

    private companion object {
        /** Every language the apps ship, which is every language these words are read in. */
        val LANGUAGES = listOf("en-rUS", "fr-rFR", "es-rES", "pl-rPL", "de-rDE")

        /** The words drawn under the round buttons while a call is in progress. */
        val CAPTIONS = listOf(
            R.string.mute,
            R.string.keypad,
            R.string.hold,
            R.string.add,
            R.string.swap,
            R.string.merge,
            R.string.audio_speaker,
            R.string.audio_output,
        )

        /**
         * Room for the longest word already here, "Zusammenführen" at fourteen, and not much
         * more. A caption that needs more than this wants to be a shorter word, not a wider
         * button: the button is as wide as a third of a phone.
         */
        const val CEILING = 15
    }
}
