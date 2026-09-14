package com.seca.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Places a call for Seca Contacts' "Appeler", in one tap. Reachable only by
 * apps holding the signature permission set in the manifest.
 *
 * Without the right to call yet, it opens the keypad with the number instead,
 * one tap away, where the app can ask for it. With several SIMs and none chosen
 * for this contact, the app opens to ask which one.
 */
class PlaceCallActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val number = data?.schemeSpecificPart
        val allowed = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val placed = when {
            number.isNullOrBlank() || !allowed -> false
            else -> when (val route = routeCall(this, number)) {
                is SimRoute.Direct -> placeCall(this, number, route.account)
                is SimRoute.Ask -> {
                    startActivity(Intent(MainActivity.ACTION_CHOOSE_SIM, data, this, MainActivity::class.java))
                    true
                }
            }
        }
        if (!placed) startActivity(Intent(Intent.ACTION_DIAL, data, this, MainActivity::class.java))
        finish()
    }
}
