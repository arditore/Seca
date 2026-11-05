package com.seca.core.suite

/**
 * The XML that SMS and call archives are exchanged in.
 *
 * There is no standard for either. What exists is the dialect SMS Backup & Restore writes, which
 * most other tools learned to read because it got there first — specified nowhere, understood
 * nearly everywhere. That makes it the most likely thing to still be readable by whatever anyone
 * reaches for in five years, which is the whole point of writing it at all.
 *
 * Only the fields that carry meaning are written. The readers in the wild treat what they do not
 * find as absent rather than as an error, and inventing values to look complete would be worse
 * than leaving them out.
 */
object PortableXml {

    /** Opens a document whose root is [root], with the number of rows in it. */
    fun open(root: String, count: Int): StringBuilder = StringBuilder()
        .append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
        .append("<$root count=\"$count\">\n")

    fun close(builder: StringBuilder, root: String): String = builder.append("</$root>\n").toString()

    /** One self-closing element, with the attributes that have something to say. */
    fun row(builder: StringBuilder, name: String, attributes: List<Pair<String, String?>>) {
        builder.append("  <").append(name)
        attributes.forEach { (key, value) ->
            if (value != null) builder.append(' ').append(key).append("=\"").append(escape(value)).append('"')
        }
        builder.append(" />\n")
    }

    /**
     * What XML allows inside an attribute.
     *
     * Control characters are dropped rather than escaped: XML 1.0 has no way to write most of
     * them, and a single one makes the whole file unreadable to a strict parser — which is
     * exactly the parser someone will be using years from now.
     */
    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when {
                character == '&' -> append("&amp;")
                character == '<' -> append("&lt;")
                character == '>' -> append("&gt;")
                character == '"' -> append("&quot;")
                character == '\'' -> append("&apos;")
                character == '\n' -> append("&#10;")
                character == '\r' -> append("&#13;")
                character == '\t' -> append("&#9;")
                character.code < FIRST_PRINTABLE -> Unit
                else -> append(character)
            }
        }
    }

    /** Below this, a character has no place in an XML 1.0 document. */
    private const val FIRST_PRINTABLE = 0x20
}
