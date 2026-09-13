package com.seca.core.link.nostr

import org.json.JSONObject
import java.security.MessageDigest

/** A signed Nostr event, as NIP-01 defines it. */
data class NostrEvent(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String,
) {
    /** The event as a relay receives it. */
    fun toJson(): String = StringBuilder()
        .append("{\"id\":").appendJsonString(id, canonical = false)
        .append(",\"pubkey\":").appendJsonString(pubkey, canonical = false)
        .append(",\"created_at\":").append(createdAt)
        .append(",\"kind\":").append(kind)
        .append(",\"tags\":").appendTags(tags, canonical = false)
        .append(",\"content\":").appendJsonString(content, canonical = false)
        .append(",\"sig\":").appendJsonString(sig, canonical = false)
        .append('}')
        .toString()

    companion object {
        /** The SHA-256 of `[0,pubkey,created_at,kind,tags,content]`, which names and is signed for an event. */
        fun idOf(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): ByteArray =
            MessageDigest.getInstance("SHA-256")
                .digest(serializeForId(pubkey, createdAt, kind, tags, content).toByteArray(Charsets.UTF_8))

        /** Written exactly as NIP-01 prescribes, so every relay and client computes the same id. */
        internal fun serializeForId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String =
            StringBuilder()
                .append("[0,").appendJsonString(pubkey, canonical = true)
                .append(',').append(createdAt)
                .append(',').append(kind)
                .append(',').appendTags(tags, canonical = true)
                .append(',').appendJsonString(content, canonical = true)
                .append(']')
                .toString()

        /** Reads an event sent by a relay; null when it is malformed. The signature is checked apart. */
        fun fromJson(json: JSONObject): NostrEvent? = runCatching {
            val tags = json.getJSONArray("tags")
            NostrEvent(
                id = json.getString("id"),
                pubkey = json.getString("pubkey"),
                createdAt = json.getLong("created_at"),
                kind = json.getInt("kind"),
                tags = (0 until tags.length()).map { index ->
                    val tag = tags.getJSONArray(index)
                    (0 until tag.length()).map(tag::getString)
                },
                content = json.getString("content"),
                sig = json.getString("sig"),
            )
        }.getOrNull()
    }
}

private fun StringBuilder.appendTags(tags: List<List<String>>, canonical: Boolean): StringBuilder {
    append('[')
    tags.forEachIndexed { index, tag ->
        if (index > 0) append(',')
        append('[')
        tag.forEachIndexed { position, value ->
            if (position > 0) append(',')
            appendJsonString(value, canonical)
        }
        append(']')
    }
    return append(']')
}

/**
 * A JSON string. For an id, NIP-01 escapes these seven characters and nothing
 * else; on the wire the other control characters are escaped too, as JSON
 * requires, and read back to the same text.
 */
internal fun StringBuilder.appendJsonString(value: String, canonical: Boolean): StringBuilder {
    append('"')
    for (char in value) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            Char(FORM_FEED) -> append("\\f")
            else -> if (!canonical && char < ' ') {
                append("\\u").append(char.code.toString(HEX).padStart(UNICODE_ESCAPE_DIGITS, '0'))
            } else {
                append(char)
            }
        }
    }
    return append('"')
}

private const val HEX = 16
private const val FORM_FEED = 12
private const val UNICODE_ESCAPE_DIGITS = 4
