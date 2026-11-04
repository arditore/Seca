package com.seca.messages.mms

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading multimedia messages, written the way a carrier writes them.
 *
 * The messages here are built byte by byte to the encoding in WAP-209, rather than captured from
 * a phone, so each test says exactly which part of the format it is about — and so a failure
 * points at a rule rather than at a blob.
 */
class MmsParserTest {

    @Test
    fun `a notification says who it is from and where to fetch it`() {
        val pdu = pdu {
            field(MmsField.MESSAGE_TYPE, byteOf(MmsField.TYPE_NOTIFICATION_IND))
            field(MmsField.TRANSACTION_ID, text("NOK5CAFE0001"))
            field(MmsField.MMS_VERSION, byteOf(0x92))
            field(MmsField.FROM, address("+33612345678/TYPE=PLMN"))
            field(MmsField.SUBJECT, text("Les photos de dimanche"))
            field(MmsField.MESSAGE_SIZE, longInteger(284_512))
            field(MmsField.EXPIRY, expiry(86_400))
            field(MmsField.CONTENT_LOCATION, text("http://mms.example.net/fetch?id=42"))
        }

        val parsed = MmsParser.parse(pdu) as MmsPdu.Notification
        assertEquals("NOK5CAFE0001", parsed.transactionId)
        // The "/TYPE=PLMN" a carrier appends is not part of anybody's phone number.
        assertEquals("+33612345678", parsed.from)
        assertEquals("Les photos de dimanche", parsed.subject)
        assertEquals("http://mms.example.net/fetch?id=42", parsed.contentLocation)
        assertEquals(284_512L, parsed.size)
        assertEquals(86_400L, parsed.expiry)
    }

    @Test
    fun `a retrieved message carries its text and its picture`() {
        val photo = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03)
        val pdu = pdu {
            field(MmsField.MESSAGE_TYPE, byteOf(MmsField.TYPE_RETRIEVE_CONF))
            field(MmsField.MESSAGE_ID, text("42@mms.example.net"))
            field(MmsField.TRANSACTION_ID, text("NOK5CAFE0001"))
            field(MmsField.DATE, longInteger(1_762_000_000))
            field(MmsField.FROM, address("+33612345678/TYPE=PLMN"))
            field(MmsField.TO, text("+33698765432/TYPE=PLMN"))
            field(MmsField.SUBJECT, text("Dimanche"))
            field(MmsField.CONTENT_TYPE, contentTypeRelated())
            parts(
                part(type = TEXT_PLAIN, name = "text.txt", id = "<text>", data = "Regarde ça".toByteArray()),
                part(type = IMAGE_JPEG, name = "photo.jpg", id = "<photo>", data = photo),
            )
        }

        val parsed = MmsParser.parse(pdu) as MmsPdu.Retrieved
        assertEquals("42@mms.example.net", parsed.messageId)
        assertEquals("+33612345678", parsed.from)
        assertEquals(listOf("+33698765432"), parsed.to)
        assertEquals("Dimanche", parsed.subject)
        assertEquals(1_762_000_000_000L, parsed.sentAt)
        assertEquals(2, parsed.parts.size)
        assertEquals("Regarde ça", parsed.text)
        val picture = parsed.parts[1]
        assertEquals("image/jpeg", picture.contentType)
        assertEquals("photo.jpg", picture.name)
        assertTrue(picture.data.contentEquals(photo))
    }

    @Test
    fun `a field this does not know does not cost the rest of the message`() {
        val pdu = pdu {
            field(MmsField.MESSAGE_TYPE, byteOf(MmsField.TYPE_NOTIFICATION_IND))
            // Something from a later version of the specification, written as a text value.
            field(0xBF, text("whatever-this-is"))
            field(MmsField.TRANSACTION_ID, text("T-1"))
            field(0xBE, byteOf(0x81))
            field(MmsField.CONTENT_LOCATION, text("http://mms.example.net/x"))
        }

        val parsed = MmsParser.parse(pdu) as MmsPdu.Notification
        assertEquals("T-1", parsed.transactionId)
        assertEquals("http://mms.example.net/x", parsed.contentLocation)
    }

    @Test
    fun `a message that stops in the middle is refused rather than guessed at`() {
        val whole = pdu {
            field(MmsField.MESSAGE_TYPE, byteOf(MmsField.TYPE_NOTIFICATION_IND))
            field(MmsField.TRANSACTION_ID, text("T-1"))
            field(MmsField.CONTENT_LOCATION, text("http://mms.example.net/x"))
        }
        // Every truncation of it: none may throw, and none may invent a content location.
        for (length in 1 until whole.size) {
            val cut = whole.copyOfRange(0, length)
            val parsed = runCatching { MmsParser.parse(cut) }.getOrNull()
            val location = (parsed as? MmsPdu.Notification)?.contentLocation
            assertTrue("truncated to $length invented $location", location == null || whole.size - length < 2)
        }
    }

    @Test
    fun `nothing at all is nothing`() {
        assertNull(MmsParser.parse(ByteArray(0)))
        assertNull(MmsParser.parse(byteArrayOf(0x00)))
    }

    @Test
    fun `a subject written in another alphabet survives`() {
        val subject = "देखो यह"
        val pdu = pdu {
            field(MmsField.MESSAGE_TYPE, byteOf(MmsField.TYPE_NOTIFICATION_IND))
            field(MmsField.SUBJECT, encodedText(subject, charset = UTF_8_CHARSET))
            field(MmsField.CONTENT_LOCATION, text("http://mms.example.net/x"))
        }
        assertEquals(subject, (MmsParser.parse(pdu) as MmsPdu.Notification).subject)
    }

    // --- building messages the way a carrier would ---

    private inner class Pdu {
        val out = ByteArrayOutputStream()

        fun field(id: Int, value: ByteArray) {
            out.write(id)
            out.write(value)
        }

        fun parts(vararg parts: ByteArray) {
            out.write(uintvar(parts.size.toLong()))
            parts.forEach(out::write)
        }
    }

    private fun pdu(build: Pdu.() -> Unit): ByteArray = Pdu().apply(build).out.toByteArray()

    private fun byteOf(value: Int) = byteArrayOf(value.toByte())

    private fun text(value: String) = value.toByteArray() + 0

    private fun encodedText(value: String, charset: Int): ByteArray {
        val body = value.toByteArray(Charsets.UTF_8) + 0
        val charsetBytes = shortIntegerOrLong(charset)
        return byteArrayOf((charsetBytes.size + body.size).toByte()) + charsetBytes + body
    }

    private fun shortIntegerOrLong(value: Int): ByteArray =
        if (value < 0x80) byteArrayOf((value or 0x80).toByte()) else longInteger(value.toLong())

    private fun longInteger(value: Long): ByteArray {
        var remaining = value
        val digits = mutableListOf<Byte>()
        while (remaining > 0) {
            digits.add(0, (remaining and 0xFF).toByte())
            remaining = remaining shr 8
        }
        if (digits.isEmpty()) digits.add(0)
        return byteArrayOf(digits.size.toByte()) + digits.toByteArray()
    }

    private fun address(value: String): ByteArray {
        val body = byteArrayOf(PduReader.ADDRESS_PRESENT.toByte()) + text(value)
        return byteArrayOf(body.size.toByte()) + body
    }

    private fun expiry(seconds: Long): ByteArray {
        // A relative expiry: the token saying so, then the number of seconds.
        val body = byteArrayOf(0x81.toByte()) + longInteger(seconds)
        return byteArrayOf(body.size.toByte()) + body
    }

    private fun contentTypeRelated(): ByteArray {
        val body = byteArrayOf(MULTIPART_RELATED.toByte())
        return byteArrayOf(body.size.toByte()) + body
    }

    private fun part(type: Int, name: String, id: String, data: ByteArray): ByteArray {
        val typeValue = byteArrayOf(type.toByte()) + byteArrayOf(PARAM_NAME.toByte()) + text(name)
        val header = byteArrayOf(typeValue.size.toByte()) + typeValue +
            byteArrayOf(HEADER_CONTENT_ID.toByte()) + text(id)
        return uintvar(header.size.toLong()) + uintvar(data.size.toLong()) + header + data
    }

    private fun uintvar(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val groups = mutableListOf<Byte>()
        var remaining = value
        while (remaining > 0) {
            groups.add(0, (remaining and 0x7F).toByte())
            remaining = remaining shr 7
        }
        for (i in 0 until groups.size - 1) groups[i] = (groups[i].toInt() or 0x80).toByte()
        return groups.toByteArray()
    }

    private companion object {
        const val TEXT_PLAIN = 0x83
        const val IMAGE_JPEG = 0xA1
        const val MULTIPART_RELATED = 0xA9
        const val PARAM_NAME = 0x85
        const val HEADER_CONTENT_ID = 0xC0
        const val UTF_8_CHARSET = 0x6A
    }
}
