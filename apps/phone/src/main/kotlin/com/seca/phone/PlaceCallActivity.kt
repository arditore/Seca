package com.seca.phone

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Places a call for Seca Contacts' "Appeler", in one tap. Reachable only by
 * apps holding the signature permission set in the manifest.
 *
 * Without the right to call yet, it opens the keypad with the number instead,
 * one tap away, where the app can ask for it.
 */
class PlaceCallActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val number = data?.schemeSpecificPart
        if (number.isNullOrBlank() || !placeCall(this, number)) {
            startActivity(Intent(Intent.ACTION_DIAL, data, this, MainActivity::class.java))
        }
        finish()
    }
}
