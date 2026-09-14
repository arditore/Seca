package com.seca.messages

/**
 * The conversation on screen while the app is in front, by its number in
 * international format: what arrives in it needs no notification.
 */
internal object ActiveConversation {

    @Volatile
    private var shown: String? = null

    fun show(number: String) {
        shown = number
    }

    fun hide(number: String) {
        if (shown == number) shown = null
    }

    fun isShown(number: String?): Boolean = number != null && number == shown
}
