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
        header(MmsField.MESSAGE_CLASS) { textString(CLASS_PERSONAL) }
        header(MmsField.PRIORITY) { byte(PRIORITY_NORMAL) }
        header(MmsField.DELIVERY_REPORT) { byte(NO) }
        header(MmsField.READ_REPLY) { byte(NO) }
        // The content type has to come last: everything after it is the body.
        header(MmsField.CONTENT_TYPE) { contentTypeOfBody() }
        raw(body(parts(outgoing)))
    }.bytes()

    /**
     * The parts, with the words first.
     *
     * No presentation part is written. A slideshow description tells the other phone where to
     * put things on a screen we know nothing about; every messaging app worth the name lays the
     * parts out itself, and an absent one is read as "show them in order".
     */
    private fun parts(outgoing: Outgoing): List<MmsPdu.Part> = buildList {
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

    /** The multipart type, with the character set its part headers are written in. */
    private fun PduWriter.contentTypeOfBody() {
        val body = PduWriter().apply {
            byte(MULTIPART_RELATED)
            // Which part is the one to show first, and the set the names are written in.
            byte(PARAMETER_START or 0)
            textString("<text>")
            byte(PARAMETER_TYPE)
            textString("text/plain")
        }.bytes()
        valueLength(body.size)
        raw(body)
    }

    /** The body: how many parts, then each one's headers and bytes. */
    internal fun body(parts: List<MmsPdu.Part>): ByteArray = PduWriter().apply {
        uintvar(parts.size.toLong())
        parts.forEach { part ->
            val headers = PduWriter().apply {
                textString(part.contentType)
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

    /** The kind of message this is: one being sent, rather than one arriving. */
    const val TYPE_SEND_REQ = 0x80

    private const val MMS_VERSION_1_2 = 0x12
    private const val MILLIS_PER_SECOND = 1000L

    /** "Insert-address-token": let the carrier write the sender's own number in. */
    private const val FROM_INSERT_ADDRESS = 0x81
    private const val FROM_ADDRESS_PRESENT = 0x80

    private const val CLASS_PERSONAL = "personal"
    private const val PRIORITY_NORMAL = 0x81
    private const val NO = 0x81

    /** application/vnd.wap.multipart.related, as the one byte that stands for it. */
    private const val MULTIPART_RELATED = 0xB3

    private const val PARAMETER_START = 0x8A
    private const val PARAMETER_TYPE = 0x89
    private const val PART_CONTENT_ID = 0xC0
    private const val PART_CONTENT_LOCATION = 0x8E
}
