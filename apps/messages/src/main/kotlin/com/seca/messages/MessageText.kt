package com.seca.messages

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri

/** One thing in a message worth touching: where it is in the text, and what opens it. */
private class Found(val start: Int, val end: Int, val uri: String)

/**
 * A message with its web addresses, e-mail addresses and phone numbers made touchable.
 *
 * Nothing is fetched. The text is read for things that look like addresses and they are marked
 * so that touching one hands it to whichever app the owner has for it — the browser, their mail
 * app, Seca Phone. Seca itself never opens the link: see the note on previews below.
 *
 * Underlined rather than coloured. A bubble is drawn in the contact's own colour, so a blue link
 * would be unreadable in half of them; an underline reads on every background there is.
 */
@Composable
internal fun linkedMessage(body: String): AnnotatedString {
    val context = LocalContext.current
    return buildAnnotatedString {
        append(body)
        findLinks(body).forEach { found ->
            addLink(
                LinkAnnotation.Clickable(
                    tag = found.uri,
                    styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                ) { open(context, found.uri) },
                found.start,
                found.end,
            )
        }
    }
}

/**
 * Hands [uri] to whatever app the phone has for it, and does nothing if it has none.
 *
 * A message can carry an address of a kind nothing on this phone opens, and a text message is
 * not worth crashing over.
 */
private fun open(context: Context, uri: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { if (it !is ActivityNotFoundException) throw it }
}

/** What a message offers to open, in order: for the tests, and for reading the rule plainly. */
internal fun linksIn(body: String): List<String> = findLinks(body).map(Found::uri)

/**
 * The addresses in [body], left to right and never overlapping.
 *
 * The order is the rule, and it is the whole of the difficulty: every one of these patterns
 * matches pieces of the others. A written-out link is claimed first and keeps everything inside
 * it, because the inside of a link looks like all the rest — a number in a path, an at-sign in a
 * query — and splitting one would open something nobody wrote. Then e-mail addresses, which
 * would otherwise be read as a bare domain with a name stuck to the front. Then domains written
 * without a scheme. Phone numbers last, having the loosest shape of the four.
 */
private fun findLinks(body: String): List<Found> {
    val found = mutableListOf<Found>()

    fun claim(start: Int, end: Int, uri: String) {
        if (found.none { start < it.end && it.start < end }) found += Found(start, end, uri)
    }

    val webAddresses = Patterns.WEB_URL.toRegex().findAll(body).toList()
    webAddresses.filter { "://" in it.value }.forEach { match ->
        claim(match.range.first, match.range.last + 1, match.value)
    }
    Patterns.EMAIL_ADDRESS.toRegex().findAll(body).forEach { match ->
        claim(match.range.first, match.range.last + 1, "mailto:${match.value}")
    }
    // "f-droid.org" is an address without saying so; the browser needs the scheme spelled out.
    webAddresses.filterNot { "://" in it.value }.forEach { match ->
        claim(match.range.first, match.range.last + 1, "https://${match.value}")
    }
    Patterns.PHONE.toRegex().findAll(body).forEach { match ->
        // Android's pattern calls almost any run of digits a phone number, so a price or a date
        // would become a call button. Enough digits to be a number someone could ring.
        if (match.value.count(Char::isDigit) >= SHORTEST_NUMBER) {
            claim(match.range.first, match.range.last + 1, "tel:${match.value.filterNot(Char::isWhitespace)}")
        }
    }
    return found.sortedBy(Found::start)
}

/** Below this many digits, a run of numbers is a price, a date or a reference - not a number to ring. */
private const val SHORTEST_NUMBER = 6
