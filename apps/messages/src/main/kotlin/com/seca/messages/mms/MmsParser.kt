package com.seca.messages.mms

/**
 * Turns the bytes of a multimedia message into something the app can use.
 *
 * Headers come first, each named by one byte, in no guaranteed order and with fields this does
 * not know mixed in among them — so unknown fields are skipped by reading their value rather
 * than by assuming a length. After the headers, a retrieved message carries its parts.
 *
 * Nothing here trusts what it reads. A message that ends early, names a length longer than
 * itself or uses a field in a way it should not, comes back as null: these bytes arrive from
 * outside the phone, and a parser is the first thing an attacker meets.
 */
internal object MmsParser {

    fun parse(bytes: ByteArray): MmsPdu? {
        if (bytes.isEmpty()) return null
        val reader = PduReader(bytes)
        val headers = readHeaders(reader) ?: return null
        return when (headers.messageType) {
            MmsField.TYPE_NOTIFICATION_IND -> MmsPdu.Notification(
                transactionId = headers.transactionId,
                from = headers.from,
                subject = headers.subject,
                contentLocation = headers.contentLocation,
                size = headers.size,
                expiry = headers.expiry,
            )
            MmsField.TYPE_RETRIEVE_CONF -> MmsPdu.Retrieved(
                messageId = headers.messageId,
                transactionId = headers.transactionId,
                from = headers.from,
                to = headers.to,
                subject = headers.subject,
                sentAt = headers.date,
                parts = readParts(reader).orEmpty(),
            )
            else -> null
        }
    }

    private class Headers {
        var messageType: Int = -1
        var transactionId: String? = null
        var messageId: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        var subject: String? = null
        var contentLocation: String? = null
        var size: Long = 0
        var expiry: Long = 0
        var date: Long = 0
    }

    /**
     * Reads headers until the content type, which is always last and whose value introduces the
     * body. A header this does not know is skipped by reading past its value, so one unfamiliar
     * field does not cost the rest of the message.
     */
    private fun readHeaders(reader: PduReader): Headers? {
        val headers = Headers()
        while (!reader.exhausted) {
            val field = reader.byte() ?: break
            if (field and 0x80 == 0) return null
            when (field) {
                MmsField.MESSAGE_TYPE -> headers.messageType = reader.byte() ?: return null
                MmsField.TRANSACTION_ID -> headers.transactionId = reader.textString()
                MmsField.MESSAGE_ID -> headers.messageId = reader.textString()
                MmsField.CONTENT_LOCATION -> headers.contentLocation = reader.textString()
                MmsField.FROM -> headers.from = reader.address()
                MmsField.TO, MmsField.CC, MmsField.BCC -> reader.encodedString()
                    ?.substringBefore('/')
                    ?.takeIf { it.isNotBlank() }
                    ?.let(headers.to::add)
                MmsField.SUBJECT -> headers.subject = reader.encodedString()
                MmsField.MESSAGE_SIZE -> headers.size = reader.integer() ?: 0
                MmsField.DATE -> headers.date = (reader.integer() ?: 0) * MILLIS_PER_SECOND
                MmsField.EXPIRY -> headers.expiry = relativeOrAbsolute(reader)
                MmsField.CONTENT_TYPE -> {
                    readContentType(reader)
                    return headers.takeIf { it.messageType != -1 }
                }
                // Single-byte values: the message class, the version, the yes-or-no fields.
                MmsField.MESSAGE_CLASS, MmsField.MMS_VERSION, MmsField.PRIORITY,
                MmsField.DELIVERY_REPORT, MmsField.READ_REPLY, MmsField.REPORT_ALLOWED,
                MmsField.SENDER_VISIBILITY, MmsField.STATUS, MmsField.RESPONSE_STATUS,
                -> reader.byte()
                MmsField.RESPONSE_TEXT -> reader.encodedString()
                MmsField.DELIVERY_TIME -> relativeOrAbsolute(reader)
                else -> if (skipUnknown(reader) == null) return null
            }
        }
        return headers.takeIf { it.messageType != -1 }
    }

    /** A time written either as a moment or as a delay, each behind a token saying which. */
    private fun relativeOrAbsolute(reader: PduReader): Long {
        val length = reader.valueLength()?.toInt() ?: return 0
        val end = reader.position + length
        reader.byte()
        val value = reader.integer() ?: 0
        reader.skip(end - reader.position)
        return value
    }

    /**
     * A value whose field is not known here. A short integer is one byte and a text string ends
     * at its zero; anything else says its own length first.
     */
    private fun skipUnknown(reader: PduReader): Unit? {
        val first = reader.peek() ?: return null
        return when {
            first >= 0x80 -> reader.byte()?.let { }
            first <= PduReader.SHORT_LENGTH_MAX || first == PduReader.LENGTH_QUOTE -> {
                val length = reader.valueLength()?.toInt() ?: return null
                reader.skip(length)
            }
            else -> reader.textString()?.let { }
        }
    }

    /**
     * The body's content type, and the start parameter naming which part opens a slideshow.
     * Its value is read only to be stepped over: what matters is where the parts begin.
     */
    private fun readContentType(reader: PduReader) {
        val first = reader.peek() ?: return
        if (first >= 0x80) {
            reader.byte()
            return
        }
        val length = reader.valueLength()?.toInt() ?: return
        reader.skip(length)
    }

    /**
     * The parts of a retrieved message: how many, then each one's headers and its content, both
     * written with their lengths in front. A part whose lengths do not fit in what is left ends
     * the reading rather than being guessed at.
     */
    private fun readParts(reader: PduReader): List<MmsPdu.Part>? {
        val count = reader.uintvar()?.toInt() ?: return null
        if (count <= 0 || count > MAX_PARTS) return emptyList()
        val parts = mutableListOf<MmsPdu.Part>()
        repeat(count) {
            val headersLength = reader.uintvar()?.toInt() ?: return parts
            val dataLength = reader.uintvar()?.toInt() ?: return parts
            if (headersLength < 0 || dataLength < 0) return parts
            val headersEnd = reader.position + headersLength
            val header = readPartHeader(reader, headersEnd) ?: return parts
            reader.skip(headersEnd - reader.position)
            val data = reader.bytes(reader.position + dataLength)
            parts += MmsPdu.Part(
                contentType = header.contentType,
                name = header.name,
                contentId = header.contentId,
                data = data,
            )
        }
        return parts
    }

    private class PartHeader(val contentType: String, val name: String?, val contentId: String?)

    private fun readPartHeader(reader: PduReader, end: Int): PartHeader? {
        val first = reader.peek() ?: return null
        var contentType = "application/octet-stream"
        var typeEnd = end
        if (first >= 0x80) {
            reader.byte()
            contentType = wellKnownType(first and 0x7F)
        } else {
            val length = reader.valueLength()?.toInt() ?: return null
            typeEnd = (reader.position + length).coerceAtMost(end)
            val type = reader.peek() ?: return null
            contentType = if (type >= 0x80) {
                reader.byte()
                wellKnownType(type and 0x7F)
            } else {
                reader.textString() ?: contentType
            }
        }
        // Parameters follow the type, inside its length: the file name and the part's own id.
        var name: String? = null
        var contentId: String? = null
        while (reader.position < typeEnd) {
            val parameter = reader.byte() ?: break
            when (parameter) {
                PARAM_NAME, PARAM_FILENAME -> name = reader.textString()
                PARAM_CHARSET -> reader.integer()
                else -> if (parameter >= 0x80) reader.integer() else reader.textString()
            }
        }
        reader.skip(typeEnd - reader.position)
        // After the type come the part's own headers, where the content id usually lives.
        while (reader.position < end) {
            val field = reader.byte() ?: break
            when (field) {
                HEADER_CONTENT_ID, HEADER_CONTENT_LOCATION -> {
                    val value = reader.textString()
                    if (contentId == null) contentId = value?.trim('<', '>')
                }
                else -> if (skipUnknown(reader) == null) break
            }
        }
        return PartHeader(contentType, name, contentId)
    }

    /** The content types written as one byte rather than spelled out. */
    private fun wellKnownType(code: Int): String = when (code) {
        0x03 -> "text/plain"
        0x1E -> "text/x-vCard"
        0x1F -> "text/x-vCalendar"
        0x20 -> "image/gif"
        0x21 -> "image/jpeg"
        0x22 -> "image/tiff"
        0x23 -> "image/png"
        0x24 -> "image/vnd.wap.wbmp"
        0x25 -> "application/vnd.wap.multipart.*"
        0x26 -> "application/vnd.wap.multipart.mixed"
        0x27 -> "application/vnd.wap.multipart.form-data"
        0x28 -> "application/vnd.wap.multipart.byteranges"
        0x29 -> "application/vnd.wap.multipart.related"
        0x33 -> "audio/*"
        0x35 -> "video/*"
        0x3E -> "application/smil"
        else -> "application/octet-stream"
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val MAX_PARTS = 64
    private const val PARAM_CHARSET = 0x81
    private const val PARAM_NAME = 0x85
    private const val PARAM_FILENAME = 0x86
    private const val HEADER_CONTENT_ID = 0xC0
    private const val HEADER_CONTENT_LOCATION = 0x8E
}
