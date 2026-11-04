package com.seca.messages.mms

/**
 * The two kinds of multimedia message Seca has to read.
 *
 * A multimedia message arrives in two steps. First a notification, pushed over SMS, which says
 * who it is from, how big it is and where to fetch it. Then, once fetched from the carrier's
 * server, the message itself, which is a set of parts: the text, the pictures, sometimes a
 * slideshow nobody asked for.
 */
internal sealed interface MmsPdu {

    /** The push that says a message is waiting, and where. */
    data class Notification(
        val transactionId: String?,
        val from: String?,
        val subject: String?,
        val contentLocation: String?,
        val size: Long,
        val expiry: Long,
    ) : MmsPdu

    /** The message itself, once fetched. */
    data class Retrieved(
        val messageId: String?,
        val transactionId: String?,
        val from: String?,
        val to: List<String>,
        val subject: String?,
        val sentAt: Long,
        val parts: List<Part>,
    ) : MmsPdu {
        /** The readable text of the message, which is what a conversation shows. */
        val text: String get() = parts.mapNotNull(Part::text).filter { it.isNotBlank() }.joinToString("\n")
    }

    /** One piece of a message: a line of text, a photo, a sound. */
    data class Part(
        val contentType: String,
        val name: String?,
        val contentId: String?,
        val data: ByteArray,
    ) {
        val isText: Boolean get() = contentType.startsWith("text/")

        /** The part's words, when it has any. */
        val text: String? get() = if (isText) runCatching { data.toString(Charsets.UTF_8) }.getOrNull() else null

        // Data classes compare arrays by identity; parts are compared in tests, so this is spelled out.
        override fun equals(other: Any?): Boolean = this === other ||
            (
                other is Part && contentType == other.contentType && name == other.name &&
                    contentId == other.contentId && data.contentEquals(other.data)
                )

        override fun hashCode(): Int {
            var result = contentType.hashCode()
            result = 31 * result + (name?.hashCode() ?: 0)
            result = 31 * result + (contentId?.hashCode() ?: 0)
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}

/** The header fields and message types this reads, by the single byte each is written as. */
internal object MmsField {
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val DELIVERY_TIME = 0x87
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPLY = 0x90
    const val REPORT_ALLOWED = 0x91
    const val RESPONSE_STATUS = 0x92
    const val RESPONSE_TEXT = 0x93
    const val SENDER_VISIBILITY = 0x94
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98

    /** The push that announces a message. */
    const val TYPE_NOTIFICATION_IND = 0x82

    /** The message itself. */
    const val TYPE_RETRIEVE_CONF = 0x84
}
