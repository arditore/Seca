package com.seca.core.suite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file has to survive being read years from now by something strict.
 *
 * Which means two things, and they are the two things tested here: everything XML reserves must
 * be escaped, and the characters XML 1.0 cannot represent at all must be gone rather than written
 * raw. One stray control character makes the whole archive unreadable to a strict parser, and a
 * strict parser is exactly what someone will be holding when they need it.
 */
class PortableXmlTest {

    @Test
    fun `the characters XML reserves are escaped`() {
        val out = PortableXml.open("smses", 1)
        PortableXml.row(out, "sms", listOf("body" to """Ben & Jerry's <b>"best"</b>"""))
        val text = PortableXml.close(out, "smses")

        assertTrue(text, """body="Ben &amp; Jerry&apos;s &lt;b&gt;&quot;best&quot;&lt;/b&gt;"""" in text)
    }

    @Test
    fun `a line break survives, as the escape XML has for it`() {
        val out = PortableXml.open("smses", 1)
        PortableXml.row(out, "sms", listOf("body" to "first\nsecond"))

        assertTrue(PortableXml.close(out, "smses").contains("first&#10;second"))
    }

    @Test
    fun `a control character XML cannot write is dropped rather than written raw`() {
        val out = PortableXml.open("smses", 1)
        PortableXml.row(out, "sms", listOf("body" to "before\u0000\u0007after"))
        val text = PortableXml.close(out, "smses")

        assertTrue(text, "before after".replace(" ", "") in text.replace(" ", ""))
        assertFalse(text.contains('\u0000'))
        assertFalse(text.contains('\u0007'))
    }

    @Test
    fun `an attribute with nothing to say is left out entirely`() {
        val out = PortableXml.open("smses", 1)
        PortableXml.row(out, "sms", listOf("address" to "0612", "subject" to null))
        val text = PortableXml.close(out, "smses")

        assertTrue(text, "address=\"0612\"" in text)
        assertFalse(text, "subject" in text)
    }

    @Test
    fun `the document says how many rows it holds`() {
        val out = PortableXml.open("calls", 42)

        assertEquals(
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n<calls count=\"42\">\n</calls>\n",
            PortableXml.close(out, "calls"),
        )
    }
}
