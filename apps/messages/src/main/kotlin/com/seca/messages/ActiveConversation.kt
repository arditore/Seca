package com.seca.messages

/**
 * Whether the owner is already looking at the conversation a message just arrived in.
 *
 * Only that conversation. This used to suppress the notification whenever the app was in
 * front at all, on any screen, on the reasoning that what arrives "shows up there" — and on
 * the conversation list it does not: a row updates somewhere down the list, often below the
 * fold, and nothing tells you anything happened. A user reported getting every message with
 * no notification and finding them later in the conversation, which is this exactly (#55).
 *
 * The conversation on screen is known by its number in international format.
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

    /**
     * True when a message from [number] would only repeat what the screen already shows.
     *
     * Both halves are needed. The conversation being the last one opened is not enough —
     * Android leaves it there behind another app — and the app being in front is not enough
     * either, which was the bug.
     */
    fun isShown(number: String?): Boolean = inFront && number != null && number == shown
}
