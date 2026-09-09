package com.seca.core.model

/** A phone number in whatever shape the user or the system provided it. */
@JvmInline
value class PhoneNumber(val raw: String) {

    /** The number stripped of formatting, keeping a leading `+` if present. */
    val digits: String
        get() = buildString {
            raw.forEachIndexed { index, c ->
                when {
                    c.isDigit() -> append(c)
                    c == '+' && index == 0 -> append(c)
                }
            }
        }

    /**
     * Whether two numbers plausibly designate the same line.
     *
     * Compares the last [SIGNIFICANT_DIGITS] digits so that an international
     * form matches its national form. Numbers shorter than that are only
     * considered equal when identical, to avoid matching short codes.
     */
    fun matches(other: PhoneNumber): Boolean {
        val a = digits.filter { it.isDigit() }
        val b = other.digits.filter { it.isDigit() }
        if (a.isEmpty() || b.isEmpty()) return false
        if (a.length < SIGNIFICANT_DIGITS || b.length < SIGNIFICANT_DIGITS) return a == b
        return a.takeLast(SIGNIFICANT_DIGITS) == b.takeLast(SIGNIFICANT_DIGITS)
    }

    private companion object {
        const val SIGNIFICANT_DIGITS = 9
    }
}
