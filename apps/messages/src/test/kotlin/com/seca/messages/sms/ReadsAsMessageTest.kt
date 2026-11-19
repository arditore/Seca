package com.seca.messages.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling a message somebody wrote from bytes that were never words (#72).
 *
 * The rule has to be timid in one direction and firm in the other: refusing a real message is
 * losing it, while letting binary through is the screenful of hieroglyphs two users have now
 * reported. So the cases that matter most here are the ones in scripts and alphabets that look
 * unusual to an English eye and are perfectly ordinary writing.
 */
class ReadsAsMessageTest {

    @Test
    fun `ordinary writing is a message, in any script`() {
        listOf(
            "Hello, are you coming tonight?",
            "Da haben wir den Salat. Bis später!",
            "Zażółć gęślą jaźń",
            "Merhaba, yarın görüşürüz",
            "¿Nos vemos a las 20:30?",
            "Γεια σου, τι κάνεις;",
            "Привет, как дела?",
            "こんにちは、元気ですか",
            "مرحبا كيف حالك",
            "On y va 🎉🎉🎉",
            "Line one\nLine two\tand a tab\r\n",
        ).forEach { assertTrue("refused a real message: $it", readsAsMessage(it)) }
    }

    @Test
    fun `an empty message is left alone`() {
        assertTrue(readsAsMessage(""))
    }

    @Test
    fun `one NUL is enough to know it was never typed`() {
        assertFalse(readsAsMessage("a perfectly normal looking line\u0000with a NUL in it"))
    }

    @Test
    fun `a binary push is not a message`() {
        // What the announcement of a picture message looks like once something has decoded it
        // as though it were words: control characters, replacement characters, and a legible
        // fragment or two of the headers.
        val push = "\u0001\u0006\u0003¯\u0084\u008C\u0082\u0098�\u0001\u008D\u0092" +
            "\u0089\u0018\u0080�+33612345678/TYPE=PLMN\u0000\u008A\u0080\u008E\u0002" +
            "\u0019�\u0088\u0005\u0081\u0003\u0002£\u0000\u0083http://mms"
        assertFalse(readsAsMessage(push))
    }

    @Test
    fun `a message with one stray character still arrives`() {
        val mostlyWriting = "The train is late again, I will be there about half past " +
            "eight � see you then"
        assertTrue(readsAsMessage(mostlyWriting))
    }
}
