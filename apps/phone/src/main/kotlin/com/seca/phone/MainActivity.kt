package com.seca.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.privacy.SecaAppLock
import com.seca.core.design.privacy.SecaLockGate
import com.seca.core.design.switchWithoutAnimation

/** A request to open the keypad, from a tel: link or another app's "dial". */
data class DialRequest(val number: String, val nonce: Long = System.nanoTime())

/** A call another Seca app asked for, waiting for the owner to pick a SIM. */
data class CallRequest(val number: String, val nonce: Long = System.nanoTime())

class MainActivity : ComponentActivity() {

    /** Re-read on every resume: the user may grant or revoke access in Settings. */
    private val callLogGranted = mutableStateOf(false)
    private val contactsGranted = mutableStateOf(false)
    private val defaultDialer = mutableStateOf(false)
    private val dialRequest = mutableStateOf<DialRequest?>(null)
    private val callRequest = mutableStateOf<CallRequest?>(null)

    /** Locks the history and the keypad only; the call screen is never locked, so a call can always be answered. */
    private val lock by lazy { SecaAppLock(this, getString(R.string.app_name)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        switchWithoutAnimation()
        enableEdgeToEdge()
        // Read before the first frame: otherwise the screen asking for access flashes before the history.
        refreshPermissions()
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
                    callRequest = callRequest.value,
                    onCallRequestHandled = { callRequest.value = null },
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
        if (defaultDialer.value) clearMissedCalls()
    }

    private fun refreshPermissions() {
        callLogGranted.value = granted(Manifest.permission.READ_CALL_LOG)
        contactsGranted.value = granted(Manifest.permission.READ_CONTACTS)
        // Being the phone app is what lets Seca show the call screen; the user may change it in Settings.
        defaultDialer.value = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    /** Opening the history counts as having seen the missed calls; Android then takes their notification away. */
    @SuppressLint("MissingPermission") // The default phone app may do it without MODIFY_PHONE_STATE.
    private fun clearMissedCalls() {
        runCatching { getSystemService(TelecomManager::class.java)?.cancelMissedCallsNotification() }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> dialRequest.value = DialRequest(number = intent.data?.schemeSpecificPart.orEmpty())
            ACTION_CHOOSE_SIM -> intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }?.let { callRequest.value = CallRequest(it) }
            // The app's icon, touched during a call, brings the call back, as on any phone.
            Intent.ACTION_MAIN -> if (callInProgress()) openCallScreen(this)
        }
    }

    companion object {
        /** From [PlaceCallActivity]: a call to place once the owner has picked a SIM. */
        const val ACTION_CHOOSE_SIM = "com.seca.phone.action.CHOOSE_SIM"
    }
}
