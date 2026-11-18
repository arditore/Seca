package com.seca.phone.screening

/**
 * French numbers reserved for telemarketing. Since 2023 the ARCEP requires
 * automated and unsolicited sales calls to come from these ranges, so a call
 * from one of them is an advertisement by definition.
 */
internal object Telemarketing {

    private val FrenchRanges = listOf("162", "163", "270", "271", "377", "378", "424", "425", "568", "569", "948", "949")

    /** [e164] as "+33162…"; any other country is left alone. */
    fun isTelemarketing(e164: String): Boolean =
        e164.startsWith("+33") && FrenchRanges.any { e164.startsWith("+33$it") }

    /**
     * Whether the filter is worth offering on a phone in [region] (#77).
     *
     * These ranges are France's and match nothing anywhere else, so the switch elsewhere is a
     * promise the app cannot keep: it is turned on, nothing is ever filtered, and nothing says
     * why. The region is the SIM's country, not the phone's language — a French speaker in
     * Belgium has no use for this and an English speaker in France does, which is the opposite
     * of what the language would say.
     *
     * If another country publishes reserved ranges the way ARCEP does, this is the one place
     * that has to learn about it.
     */
    fun offeredIn(region: String): Boolean = region.equals(FRANCE, ignoreCase = true)

    /** The only country whose telemarketing ranges are known here. */
    private const val FRANCE = "FR"
}
