package com.seca.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.privacy.SecaAppLock
import com.seca.core.design.privacy.SecaLockGate

/** A request to open the keypad, from a tel: link or another app's "dial". */
data class DialRequest(val number: String, val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {

    /** Re-read on every resume: the user may grant or revoke access in Settings. */
    private val callLogGranted = mutableStateOf(false)
    private val contactsGranted = mutableStateOf(false)
    private val defaultDialer = mutableStateOf(false)
    private val dialRequest = mutableStateOf<DialRequest?>(null)

    /** Locks the history and the keypad only; the call screen is never locked, so a call can always be answered. */
    private val lock by lazy { SecaAppLock(this, "Seca Téléphone") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Moving between Seca apps, and back from one, happens without animation, as between tabs.
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            SecaLockGate(lock, SecaAppIdentity.Phone) {
                PhoneApp(
                    callLogGranted = callLogGranted.value,
                    contactsGranted = contactsGranted.value,
                    isDefaultDialer = defaultDialer.value,
                    onPermissionsResult = ::refreshPermissions,
                    dialRequest = dialRequest.value,
                    onDialRequestHandled = { dialRequest.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        lock.onStart()
    }

    override fun onStop() {
        super.onStop()
        lock.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        callLogGranted.value = granted(Manifest.permission.READ_CALL_LOG)
        contactsGranted.value = granted(Manifest.permission.READ_CONTACTS)
        // Being the phone app is what lets Seca show the call screen; the user may change it in Settings.
        defaultDialer.value = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            dialRequest.value = DialRequest(number = intent.data?.schemeSpecificPart.orEmpty())
        }
    }
}
