package com.seca.core.design

import android.app.ActivityOptions
import android.content.Context
import android.os.Bundle

/**
 * How one Seca app gives way to another, in the order of the suite bar:
 * Contacts, Téléphone, Messages. An app further right arrives from the right,
 * one further left from the left, so the three read as one row of screens
 * rather than three separate launches.
 */
fun secaSwitchAnimation(context: Context, from: SecaAppIdentity, to: SecaAppIdentity): Bundle? {
    val forward = to.ordinal > from.ordinal
    return ActivityOptions.makeCustomAnimation(
        context,
        if (forward) R.anim.seca_slide_in_right else R.anim.seca_slide_in_left,
        if (forward) R.anim.seca_slide_out_left else R.anim.seca_slide_out_right,
    ).toBundle()
}
