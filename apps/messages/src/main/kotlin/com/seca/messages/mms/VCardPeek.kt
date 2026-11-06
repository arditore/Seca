package com.seca.messages.mms

/** The little a conversation needs to show of a contact card: who it is, and one way to reach them. */
internal class CardFace(val name: String, val detail: String)

/**
 * Reads just enough of a vCard to put a name on a bubble.
 *
 * Not a parser. A card may carry a photograph, half a dozen addresses and fields invented by
 * whatever wrote it, and none of that belongs in a line of a conversation — the whole card goes
 * to the contacts app when it is opened, which knows how to read all of it. What is wanted here
 * is a name and one line under it.
 *
 * Folded lines are joined back together first: the format wraps anything long onto a following
 * line that begins with a space, so a name read line by line can arrive cut in half.
 */
internal object VCardPeek {

    fun read(card: String): CardFace? {
        val lines = unfold(card)
        val name = lines.firstValue("FN")
            ?: lines.firstValue("N")?.split(';')?.filter { it.isNotBlank() }?.asReversed()?.joinToString(" ")
        val detail = lines.firstValue("TEL") ?: lines.firstValue("EMAIL") ?: lines.firstValue("ORG")
        if (name.isNullOrBlank() && detail.isNullOrBlank()) return null
        return CardFace(name?.trim().orEmpty(), detail?.trim().orEmpty())
    }

    /** The first value of a property, whatever parameters were written after its name. */
    private fun List<String>.firstValue(property: String): String? = firstNotNullOfOrNull { line ->
        val colon = line.indexOf(':')
        if (colon <= 0) return@firstNotNullOfOrNull null
        val head = line.take(colon).substringAfter('.').uppercase()
        val named = head == property || head.startsWith("$property;")
        if (named) line.substring(colon + 1).takeIf { it.isNotBlank() } else null
    }

    private fun unfold(card: String): List<String> {
        val lines = mutableListOf<String>()
        card.lineSequence().forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + line.substring(1)
            } else {
                lines += line.trimEnd('\r')
            }
        }
        return lines
    }
}
