package com.seca.messages

/**
 * Whether the owner is looking at Seca Messages: while the app is in front,
 * on any of its screens, what arrives shows up there and needs no
 * notification. The conversation on screen is known by its number in
 * international format.
 */
internal object ActiveConversation {

    @Volatile
    private var shown: String? = null

    @Volatile
    private var inFront = false

    /** From the activity: true while it is resumed. */
    fun appInFront(resumed: Boolean) {
        inFront = resumed
    }

    fun show(number: String) {
        shown = number
    }

    fun hide(number: String) {
        if (shown == number) shown = null
    }

    /** True when a message from [number] would only repeat what the screen already shows. */
    fun isShown(number: String?): Boolean = inFront || (number != null && number == shown)
}
