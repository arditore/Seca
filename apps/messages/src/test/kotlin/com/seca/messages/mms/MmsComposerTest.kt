package com.seca.messages.mms

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A message we build has to be one we can read back.
 *
 * There is no way to try this against a carrier in a test, and a length written one byte short
 * comes back from the network as a number with no explanation. So the reader that was written
 * for arriving messages is turned on our own: whatever the writer puts in, the reader must get
 * out. The two were written from the same specification and not from each other, which is what
 * makes the round trip worth something.
 */
class MmsComposerTest {

    @Test
    fun `the body says how many parts it has, and each one comes back whole`() {
        val picture = ByteArray(300) { (it * 3).toByte() }
        val body = MmsComposer.body(
            listOf(
                MmsPdu.Part("text/plain", "text.txt", "<text>", "Coucou".toByteArray()),
                MmsPdu.Part("image/jpeg", "photo.jpg", "<photo>", picture),
            ),
        )

        val parts = readParts(body)

        assertEquals(2, parts.size)
        assertEquals("Coucou", String(parts[0]))
        assertArrayEquals(picture, parts[1])
    }

    @Test
    fun `a message carrying nothing but words still has one part`() {
        val body = MmsComposer.body(listOf(MmsPdu.Part("text/plain", null, null, "hi".toByteArray())))

        assertEquals(listOf("hi"), readParts(body).map { String(it) })
    }

    @Test
    fun `a part larger than a single length byte is still measured correctly`() {
        // Past 127 the lengths change shape, and a part of a few hundred kilobytes is the
        // ordinary case: a picture. Getting this wrong truncates every real message.
        val large = ByteArray(200_000) { (it % 251).toByte() }
        val parts = readParts(MmsComposer.body(listOf(MmsPdu.Part("image/jpeg", "p.jpg", "<p>", large))))

        assertArrayEquals(large, parts.single())
    }

    @Test
    fun `a length past a hundred and twenty-seven is written over several bytes`() {
        // The boundary every one of these encodings turns on: one byte below, two above.
        val writer = PduWriter()
        writer.uintvar(127)
        writer.uintvar(128)

        assertEquals(listOf(0x7F, 0x81, 0x00), writer.bytes().map { it.toInt() and 0xFF })
    }

    @Test
    fun `text beginning with a high byte is quoted, so it is not read as a number`() {
        val writer = PduWriter()
        writer.textString("é")

        // 0x7F first, or the reader takes the accent's first byte for a length.
        assertEquals(0x7F, writer.bytes().first().toInt() and 0xFF)
    }

    @Test
    fun `a short length is written plainly and a long one is announced`() {
        assertEquals(listOf(30), PduWriter().apply { valueLength(30) }.bytes().map { it.toInt() and 0xFF })
        assertEquals(listOf(31, 31), PduWriter().apply { valueLength(31) }.bytes().map { it.toInt() and 0xFF })
    }

    @Test
    fun `a number is written the way a carrier expects to read it`() {
        val written = String(PduWriter().apply { address("+33612345678") }.bytes(), Charsets.ISO_8859_1)

        assertTrue(written, "+33612345678/TYPE=PLMN" in written)
    }

    @Test
    fun `an e-mail address is left alone`() {
        val written = String(PduWriter().apply { address("someone@example.org") }.bytes(), Charsets.ISO_8859_1)

        assertTrue(written, "someone@example.org" in written)
        assertTrue(written, "TYPE=PLMN" !in written)
    }

    @Test
    fun `a message says it is one being sent`() {
        val pdu = MmsComposer.compose(
            Outgoing(listOf("+33612345678"), null, "hello", emptyList()),
            transactionId = "T1",
        )

        // The first two bytes are the message type header and the kind of message it is.
        assertEquals(MmsField.MESSAGE_TYPE, pdu[0].toInt() and 0xFF)
        assertEquals(MmsComposer.TYPE_SEND_REQ, pdu[1].toInt() and 0xFF)
    }
}

/**
 * Reads a composed body back, written here rather than reused from the app.
 *
 * The point of the test is that the writer agrees with the specification, and a test that used
 * the app's own reader would only prove the two agree with each other.
 */
private fun readParts(body: ByteArray): List<ByteArray> {
    var at = 0

    fun uintvar(): Int {
        var value = 0
        while (true) {
            val byte = body[at++].toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F)
            if (byte and 0x80 == 0) return value
        }
    }

    val count = uintvar()
    return (0 until count).map {
        val headers = uintvar()
        val data = uintvar()
        at += headers
        body.copyOfRange(at, at + data).also { at += data }
    }
}
