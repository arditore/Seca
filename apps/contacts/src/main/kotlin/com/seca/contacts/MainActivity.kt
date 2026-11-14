package com.seca.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.privacy.SecaAppLock
import com.seca.core.design.privacy.SecaLockGate
import com.seca.core.design.switchWithoutAnimation

class MainActivity : ComponentActivity() {

    /** Re-read on every resume: the user may grant or revoke access in Settings. */
    private val permissionGranted = mutableStateOf(false)
    private val viewModel: ContactsViewModel by viewModels()
    private val lock by lazy { SecaAppLock(this, getString(R.string.app_name)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        switchWithoutAnimation()
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        val picking = asksForOne(intent)
        setContent {
            SecaLockGate(lock, SecaAppIdentity.Contacts) {
                ContactsApp(
                    permissionGranted = permissionGranted.value,
                    onPermissionResult = { permissionGranted.value = it },
                    viewModel = viewModel,
                    onPick = if (picking) ::answerWith else null,
                )
            }
        }
    }

    /**
     * Another app asked for a contact: hand back where it lives and get out of the way.
     *
     * The read permission goes with it, so an app that wants one contact does not have to ask
     * the owner for the whole address book to get it.
     */
    private fun answerWith(id: Long) {
        val contact = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())
        setResult(
            RESULT_OK,
            Intent().setData(contact).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        finish()
    }

    /** Whether this was a request for one contact rather than a request to be opened. */
    private fun asksForOne(intent: Intent?): Boolean = when (intent?.action) {
        Intent.ACTION_PICK -> true
        Intent.ACTION_GET_CONTENT -> intent.type in PICKABLE
        else -> false
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
        permissionGranted.value =
            checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    /** Another app asked to add a number, or to show a contact: open straight on it. */
    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_INSERT, Intent.ACTION_INSERT_OR_EDIT -> viewModel.startWith(
                Screen.Edit(
                    id = null,
                    prefillPhone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE),
                    prefillName = intent.getStringExtra(ContactsContract.Intents.Insert.NAME),
                ),
            )
            // A card from somewhere else is read and added; one of our own rows is opened.
            Intent.ACTION_VIEW -> intent.data?.let { opened ->
                if (isCard(intent.type, opened.toString())) viewModel.offer(opened) else viewModel.openExternal(opened)
            }
        }
    }
}

/** Whether what was opened is a contact card from elsewhere rather than a contact of ours. */
private fun isCard(type: String?, where: String): Boolean =
    type?.contains("vcard", ignoreCase = true) == true ||
        type.equals("text/directory", ignoreCase = true) ||
        where.endsWith(".vcf", ignoreCase = true)

/** The kinds of "one contact, please" another app may ask for. */
private val PICKABLE = setOf(
    ContactsContract.Contacts.CONTENT_ITEM_TYPE,
    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
)
