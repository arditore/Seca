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
    /** The parts of a body on their own, for the tests: the composer writes one, this reads it. */
    internal fun partsOf(body: ByteArray): List<MmsPdu.Part> = readParts(PduReader(body)).orEmpty()

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
        when {
            // One byte standing for a type everybody knows, with nothing after it.
            first >= 0x80 -> {
                reader.byte()
                contentType = wellKnownType(first and 0x7F)
            }
            // A length, then the type and whatever parameters belong to it.
            first <= PduReader.SHORT_LENGTH_MAX || first == PduReader.LENGTH_QUOTE -> {
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
            // The type spelled out on its own, with no length in front and no parameters
            // after it. This is what the standard calls the constrained form, it is what Seca
            // itself writes, and the parser used to give up on it: the length it looked for
            // was the letter "a" of "application", so every part was dropped and the message
            // arrived with nothing in it. A picture sent by Seca could not be read by Seca.
            else -> {
                contentType = reader.textString() ?: return null
                typeEnd = reader.position
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

    /**
     * The content types written as one byte rather than spelled out.
     *
     * The numbers are not ours to choose: they are the WSP assigned numbers, the same list
     * Android's own `WspTypeDecoder` carries, and a table that is off by even one labels every
     * picture as something else. This one was, by three, so a JPEG arriving as 0x1E was read as
     * a contact card and its bytes went into the conversation as words. That is the screenful
     * of hieroglyphs two people reported, from different networks and different handsets, and
     * why a card arrived calling itself a photograph (#72, #55).
     *
     * Kept whole rather than trimmed to the types that seemed to matter, because which ones
     * matter is decided by carriers, not here.
     */
    private fun wellKnownType(code: Int): String = WELL_KNOWN_TYPES.getOrNull(code) ?: "application/octet-stream"

    private val WELL_KNOWN_TYPES = listOf(
        "*/*",
        "text/*",
        "text/html",
        "text/plain",
        "text/x-hdml",
        "text/x-ttml",
        "text/x-vCalendar",
        "text/x-vCard",
        "text/vnd.wap.wml",
        "text/vnd.wap.wmlscript",
        "text/vnd.wap.wta-event",
        "multipart/*",
        "multipart/mixed",
        "multipart/form-data",
        "multipart/byterantes",
        "multipart/alternative",
        "application/*",
        "application/java-vm",
        "application/x-www-form-urlencoded",
        "application/x-hdmlc",
        "application/vnd.wap.wmlc",
        "application/vnd.wap.wmlscriptc",
        "application/vnd.wap.wta-eventc",
        "application/vnd.wap.uaprof",
        "application/vnd.wap.wtls-ca-certificate",
        "application/vnd.wap.wtls-user-certificate",
        "application/x-x509-ca-cert",
        "application/x-x509-user-cert",
        "image/*",
        "image/gif",
        "image/jpeg",
        "image/tiff",
        "image/png",
        "image/vnd.wap.wbmp",
        "application/vnd.wap.multipart.*",
        "application/vnd.wap.multipart.mixed",
        "application/vnd.wap.multipart.form-data",
        "application/vnd.wap.multipart.byteranges",
        "application/vnd.wap.multipart.alternative",
        "application/xml",
        "text/xml",
        "application/vnd.wap.wbxml",
        "application/x-x968-cross-cert",
        "application/x-x968-ca-cert",
        "application/x-x968-user-cert",
        "text/vnd.wap.si",
        "application/vnd.wap.sic",
        "text/vnd.wap.sl",
        "application/vnd.wap.slc",
        "text/vnd.wap.co",
        "application/vnd.wap.coc",
        "application/vnd.wap.multipart.related",
        "application/vnd.wap.sia",
        "text/vnd.wap.connectivity-xml",
        "application/vnd.wap.connectivity-wbxml",
        "application/pkcs7-mime",
        "application/vnd.wap.hashed-certificate",
        "application/vnd.wap.signed-certificate",
        "application/vnd.wap.cert-response",
        "application/xhtml+xml",
        "application/wml+xml",
        "text/css",
        "application/vnd.wap.mms-message",
        "application/vnd.wap.rollover-certificate",
        "application/vnd.wap.locc+wbxml",
        "application/vnd.wap.loc+xml",
        "application/vnd.syncml.dm+wbxml",
        "application/vnd.syncml.dm+xml",
        "application/vnd.syncml.notification",
        "application/vnd.wap.xhtml+xml",
        "application/vnd.wv.csp.cir",
        "application/vnd.oma.dd+xml",
        "application/vnd.oma.drm.message",
        "application/vnd.oma.drm.content",
        "application/vnd.oma.drm.rights+xml",
        "application/vnd.oma.drm.rights+wbxml",
        "application/vnd.wv.csp+xml",
        "application/vnd.wv.csp+wbxml",
        "application/vnd.syncml.ds.notification",
        "audio/*",
        "video/*",
        "application/vnd.oma.dd2+xml",
        "application/mikey",
        "application/vnd.oma.dcd",
        "application/vnd.oma.dcdc",
        "application/vnd.uplanet.cacheop-wbxml",
        "application/vnd.uplanet.signal",
        "application/vnd.uplanet.alert-wbxml",
        "application/vnd.uplanet.list-wbxml",
        "application/vnd.uplanet.listcmd-wbxml",
        "application/vnd.uplanet.channel-wbxml",
        "application/vnd.uplanet.provisioning-status-uri",
        "x-wap.multipart/vnd.uplanet.header-set",
        "application/vnd.uplanet.bearer-choice-wbxml",
        "application/vnd.phonecom.mmc-wbxml",
        "application/vnd.nokia.syncset+wbxml",
        "image/x-up-wpng",
        "application/iota.mmc-wbxml",
        "application/iota.mmc-xml",
        "application/vnd.syncml+xml",
        "application/vnd.syncml+wbxml",
        "text/vnd.wap.emn+xml",
        "text/calendar",
        "application/vnd.omads-email+xml",
        "application/vnd.omads-file+xml",
        "application/vnd.omads-folder+xml",
        "text/directory;profile=vCard",
        "application/vnd.wap.emn+wbxml",
        "application/vnd.nokia.ipdc-purchase-response",
        "application/vnd.motorola.screen3+xml",
        "application/vnd.motorola.screen3+gzip",
        "application/vnd.cmcc.setting+wbxml",
        "application/vnd.cmcc.bombing+wbxml",
        "application/vnd.docomo.pf",
        "application/vnd.docomo.ub",
        "application/vnd.omaloc-supl-init",
        "application/vnd.oma.group-usage-list+xml",
        "application/oma-directory+xml",
        "application/vnd.docomo.pf2",
        "application/vnd.oma.drm.roap-trigger+wbxml",
        "application/vnd.sbm.mid2",
        "application/vnd.wmf.bootstrap",
        "application/vnc.cmcc.dcd+xml",
        "application/vnd.sbm.cid",
        "application/vnd.oma.bcast.provisioningtrigger",
    )

    private const val MILLIS_PER_SECOND = 1000L
    private const val MAX_PARTS = 64
    private const val PARAM_CHARSET = 0x81
    private const val PARAM_NAME = 0x85
    private const val PARAM_FILENAME = 0x86
    private const val HEADER_CONTENT_ID = 0xC0
    private const val HEADER_CONTENT_LOCATION = 0x8E
}
