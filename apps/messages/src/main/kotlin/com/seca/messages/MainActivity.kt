package com.seca.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.privacy.SecaAppLock
import com.seca.core.design.privacy.SecaLockGate
import com.seca.core.design.switchWithoutAnimation
import com.seca.messages.link.LinkService
import com.seca.messages.sms.CodeCleanup
import com.seca.messages.sms.MessageNotifications
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Re-read on every resume: the owner may grant, revoke or change the default app in Settings. */
    private val smsGranted = mutableStateOf(false)
    private val contactsGranted = mutableStateOf(false)
    private val defaultApp = mutableStateOf(false)
    private val viewModel: MessagesViewModel by viewModels()
    private val lock by lazy { SecaAppLock(this, "Seca Messages") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        switchWithoutAnimation()
        enableEdgeToEdge()
        refresh()
        if (savedInstanceState == null) {
            handle(intent)
            // Verification codes past the delay the owner chose go as the app opens.
            lifecycleScope.launch { runCatching { CodeCleanup.sweep(applicationContext) } }
        }
        setContent {
            SecaLockGate(lock, SecaAppIdentity.Messages) {
                MessagesApp(
                    smsGranted = smsGranted.value,
                    contactsGranted = contactsGranted.value,
                    isDefaultApp = defaultApp.value,
                    onPermissionsResult = ::refresh,
                    viewModel = viewModel,
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
        ActiveConversation.appInFront(true)
        refresh()
        // Seca Link listens again if Android stopped it, now that the app is in front and may start it.
        LinkService.start(this)
    }

    override fun onPause() {
        // Out of sight, messages are announced again.
        ActiveConversation.appInFront(false)
        super.onPause()
    }

    private fun refresh() {
        smsGranted.value = granted(Manifest.permission.READ_SMS)
        contactsGranted.value = granted(Manifest.permission.READ_CONTACTS)
        defaultApp.value = getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_SMS) == true
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** A notification, a link or another app asked to open a conversation. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_CONVERSATION -> {
                val address = intent.getStringExtra(MessageNotifications.EXTRA_ADDRESS) ?: return
                viewModel.openConversation(address)
            }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                // "smsto:0612345678?body=…"; several recipients are not handled yet, the first one is used.
                val address = intent.data?.schemeSpecificPart
                    ?.substringBefore('?')
                    ?.split(',', ';')
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                val draft = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (address.isEmpty()) {
                    viewModel.startWith(MessagesScreen.NewMessage)
                } else {
                    viewModel.openConversation(address, draft)
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_CONVERSATION = "com.seca.messages.OPEN_CONVERSATION"
    }
}
