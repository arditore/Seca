package com.seca.core.link.handshake

import android.content.Context
import com.seca.core.link.R

/** The handshake as a text message, its short explanation in the phone's language. */
fun Handshake.textFor(context: Context): String =
    text(context.getString(if (type == Handshake.Type.Invite) R.string.link_invite_intro else R.string.link_accept_intro))
