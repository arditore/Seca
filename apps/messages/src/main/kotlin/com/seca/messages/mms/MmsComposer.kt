package com.seca.messages.mms

/** What is being sent: the people, what is written, and what is attached. */
internal class Outgoing(
    val to: List<String>,
    val from: String?,
    val text: String,
    val attachments: List<MmsPdu.Part>,
)

/**
 * Builds the binary a multimedia message is sent as.
 *
 * Two layers, and the second is the one people get wrong. The headers say who it is for and what
 * follows; then the body is itself an encoded structure — a count of parts, and for each one the
 * length of its header, the length of its data, its content type and its bytes. The carrier's
 * server reads both, and rejects the whole message if either is a byte out.
 *
 * A message is built to be read by a telephone nobody here chose. That rules out two shortcuts
 * that look harmless from this side: leaving out the presentation part, and leaving the words
 * without a character set. Both are below.
 */
internal object MmsComposer {

    /** The whole message, ready to hand to the telephony service. */
    fun compose(outgoing: Outgoing, transactionId: String): ByteArray = PduWriter().apply {
        header(MmsField.MESSAGE_TYPE) { byte(TYPE_SEND_REQ) }
        header(MmsField.TRANSACTION_ID) { textString(transactionId) }
        header(MmsField.MMS_VERSION) { shortInteger(MMS_VERSION_1_2) }
        // The sender's own number, when the SIM knows it. Carriers fill it in regardless, and
        // several refuse a message that tries to name someone else, so an absent one says so.
        outgoing.from?.takeIf { it.isNotBlank() }?.let { from ->
            header(MmsField.FROM) {
                val body = PduWriter().apply {
                    byte(FROM_ADDRESS_PRESENT)
                    address(from)
                }.bytes()
                valueLength(body.size)
                raw(body)
            }
        } ?: header(MmsField.FROM) {
            valueLength(1)
            byte(FROM_INSERT_ADDRESS)
        }
        outgoing.to.forEach { recipient -> header(MmsField.TO) { address(recipient) } }
        header(MmsField.DATE) { longInteger(System.currentTimeMillis() / MILLIS_PER_SECOND) }
        header(MmsField.MESSAGE_CLASS) { byte(CLASS_PERSONAL) }
        header(MmsField.PRIORITY) { byte(PRIORITY_NORMAL) }
        header(MmsField.DELIVERY_REPORT) { byte(NO) }
        header(MmsField.READ_REPLY) { byte(NO) }
        val carried = parts(outgoing)
        // The content type has to come last: everything after it is the body.
        header(MmsField.CONTENT_TYPE) { relatedTo(carried.first()) }
        raw(body(carried))
    }.bytes()

    /**
     * The presentation first, then the words, then whatever is attached.
     *
     * The presentation part is a small SMIL document naming the others and saying where they go.
     * It is tempting to leave out — every part is in the message without it, and an app that
     * lays its own messages out does not need it. But a multipart/related message names a root
     * part, the standard says that root is the presentation, and the phones that follow that to
     * the letter are the ones nobody here can test against: an iPhone, a car, a ten-year-old
     * handset. Those show the root and nothing else, so a message without one arrives as a
     * message with nothing in it.
     */
    private fun parts(outgoing: Outgoing): List<MmsPdu.Part> {
        val carried = buildList {
            outgoing.text.takeIf { it.isNotBlank() }?.let { words ->
                add(
                    MmsPdu.Part(
                        contentType = "text/plain",
                        name = "text.txt",
                        contentId = "<text>",
                        data = words.toByteArray(Charsets.UTF_8),
                    ),
                )
            }
            addAll(outgoing.attachments)
        }
        return listOf(presentation(carried)) + carried
    }

    /** The SMIL document: one slide, the picture above and the words below. */
    private fun presentation(carried: List<MmsPdu.Part>): MmsPdu.Part {
        val shown = buildString {
            carried.forEach { part ->
                val src = part.name ?: return@forEach
                val tag = tagFor(part.contentType)
                val region = when (tag) {
                    TAG_IMAGE, TAG_VIDEO -> """ region="Image""""
                    TAG_TEXT -> """ region="Text""""
                    else -> ""
                }
                append("""<$tag src="${escaped(src)}"$region/>""")
            }
        }
        val smil = """<smil><head><layout>""" +
            """<root-layout width="320px" height="480px"/>""" +
            """<region id="Image" top="0" left="0" width="100%" height="80%" fit="meet"/>""" +
            """<region id="Text" top="80%" left="0" width="100%" height="20%"/>""" +
            """</layout></head><body><par dur="$SLIDE_MILLIS">$shown</par></body></smil>"""
        return MmsPdu.Part(SMIL, "smil.xml", SMIL_ID, smil.toByteArray(Charsets.UTF_8))
    }

    /** Which SMIL element stands for a part. Anything with no element of its own is a reference. */
    private fun tagFor(contentType: String): String = when {
        contentType.startsWith("image/") -> TAG_IMAGE
        contentType.startsWith("video/") -> TAG_VIDEO
        contentType.startsWith("audio/") -> TAG_AUDIO
        contentType == "text/plain" -> TAG_TEXT
        else -> TAG_REFERENCE
    }

    /** A file name may hold an ampersand or a quotation mark, and the document has to survive it. */
    private fun escaped(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** The multipart type, naming which part is the presentation and what kind of thing it is. */
    private fun PduWriter.relatedTo(root: MmsPdu.Part) {
        val body = PduWriter().apply {
            byte(MULTIPART_RELATED)
            byte(PARAMETER_TYPE)
            textString(root.contentType)
            byte(PARAMETER_START)
            textString(root.contentId.orEmpty())
        }.bytes()
        valueLength(body.size)
        raw(body)
    }

    /** The body: how many parts, then each one's headers and bytes. */
    internal fun body(parts: List<MmsPdu.Part>): ByteArray = PduWriter().apply {
        uintvar(parts.size.toLong())
        parts.forEach { part ->
            val headers = PduWriter().apply {
                typeOf(part)
                part.contentId?.let {
                    byte(PART_CONTENT_ID)
                    textString(it)
                }
                part.name?.let {
                    byte(PART_CONTENT_LOCATION)
                    textString(it)
                }
            }.bytes()
            uintvar(headers.size.toLong())
            uintvar(part.data.size.toLong())
            raw(headers)
            raw(part.data)
        }
    }.bytes()

    /**
     * A part's type, and for words the set they are written in.
     *
     * Without the character set the receiving phone guesses, and what it guesses is rarely
     * UTF-8: every accent in the message arrives as a question mark or worse. This is invisible
     * from the sending side, where the words were always correct.
     */
    private fun PduWriter.typeOf(part: MmsPdu.Part) {
        if (!part.isText) {
            textString(part.contentType)
            return
        }
        val media = PduWriter().apply {
            textString(part.contentType)
            byte(PARAMETER_CHARSET)
            shortInteger(UTF_8)
        }.bytes()
        valueLength(media.size)
        raw(media)
    }

    /** The kind of message this is: one being sent, rather than one arriving. */
    const val TYPE_SEND_REQ = 0x80

    /** The presentation part's type and the name the content type points at it by. */
    const val SMIL = "application/smil"
    const val SMIL_ID = "<smil>"

    private const val MMS_VERSION_1_2 = 0x12
    private const val MILLIS_PER_SECOND = 1000L

    /** "Insert-address-token": let the carrier write the sender's own number in. */
    private const val FROM_INSERT_ADDRESS = 0x81
    private const val FROM_ADDRESS_PRESENT = 0x80

    private const val CLASS_PERSONAL = 0x80
    private const val PRIORITY_NORMAL = 0x81
    private const val NO = 0x81

    /** application/vnd.wap.multipart.related, as the one byte that stands for it. */
    private const val MULTIPART_RELATED = 0xB3

    private const val PARAMETER_START = 0x8A
    private const val PARAMETER_TYPE = 0x89
    private const val PARAMETER_CHARSET = 0x81
    private const val PART_CONTENT_ID = 0xC0
    private const val PART_CONTENT_LOCATION = 0x8E

    /** UTF-8, as the number the specification gives it. */
    private const val UTF_8 = 0x6A

    private const val SLIDE_MILLIS = "8000ms"
    private const val TAG_IMAGE = "img"
    private const val TAG_VIDEO = "video"
    private const val TAG_AUDIO = "audio"
    private const val TAG_TEXT = "text"
    private const val TAG_REFERENCE = "ref"
}
