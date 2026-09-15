package com.seca.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.seca.core.contacts.ContactInput
import com.seca.core.contacts.ContactsRepository
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.VCard
import com.seca.core.contacts.VCardContact
import com.seca.core.design.SecaPalette
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import com.seca.core.suite.SuiteBackup
import com.seca.core.suite.SuiteBackupProvider
import org.json.JSONArray
import org.json.JSONObject
import androidx.annotation.PluralsRes

/**
 * Seca Contacts' part of a backup: every contact with its profile, the
 * profiles, "My card" and the suite's colours. The app's own encrypted backup
 * and the suite's share it.
 */
internal class ContactsBackup(private val context: Context) {

    private val numbers = PhoneNumbers(PhoneNumbers.detectRegion(context))
    private val repository = ContactsRepository(context.contentResolver, numbers)
    private val store = ProfileStore(context)
    private val myCards = MyCardStore(context)

    suspend fun export(): JSONObject {
        val readable = granted(Manifest.permission.READ_CONTACTS)
        val entries = if (readable) repository.exportEntries() else emptyList()
        val profiles = store.profiles()
        val assignments = store.assignments()
        val myCard = myCards.load()
        return JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("profiles", JSONArray().apply { profiles.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
            .put(
                "contacts",
                JSONArray().apply {
                    entries.forEach { (key, card) ->
                        // A contact whose profile was deleted is in Principal.
                        val profile = assignments[key]?.takeIf { id -> profiles.any { it.id == id } } ?: ProfileStore.Principal.id
                        put(JSONObject().put("profile", profile).put("key", key).put("vcard", VCard.write(listOf(card))))
                    }
                },
            )
            .put("myCard", JSONObject().put("name", myCard.name).put("numbers", JSONArray(myCard.numbers)))
            .apply { store.palette()?.let { put("palette", it.name) } }
            .put(
                SuiteBackup.KEY_SUMMARY,
                if (readable) quantity(R.plurals.contacts_count, entries.size) else context.getString(R.string.backup_profiles_only),
            )
    }

    /** Puts back what this phone is missing: profiles, contacts each in its profile, "My card" when empty. Returns what came back. */
    suspend fun restore(part: JSONObject): String {
        val profiles = part.optJSONArray("profiles") ?: JSONArray()
        for (index in 0 until profiles.length()) {
            val item = profiles.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id.isNotEmpty()) store.restoreProfile(Profile(id, item.optString("name")))
        }
        // The suite's colours come back only onto a phone that never had any chosen.
        if (store.palette() == null) {
            SecaPalette.entries.firstOrNull { it.name == part.optString("palette") }?.let(store::setPalette)
        }
        // "My card" only comes back onto a phone where it was never filled in.
        part.optJSONObject("myCard")?.let { card ->
            val current = myCards.load()
            if (current.name.isBlank() && current.numbers.isEmpty()) {
                val saved = card.optJSONArray("numbers") ?: JSONArray()
                myCards.save(card.optString("name"), (0 until saved.length()).map(saved::getString))
            }
        }
        val contacts = part.optJSONArray("contacts") ?: JSONArray()
        if (contacts.length() == 0) return context.getString(R.string.backup_profiles_restored)
        if (!granted(Manifest.permission.READ_CONTACTS)) return context.getString(R.string.backup_profiles_restored_no_access)
        // Filing a contact already on the phone only needs to read the contacts; bringing back a
        // missing one needs to write them, which Seca Contacts asks for only when it is needed.
        val canWrite = granted(Manifest.permission.WRITE_CONTACTS)
        val existing = repository.contacts()
        val profileIds = store.profiles().map { it.id }.toSet()
        val filed = store.assignments().filterValues { it in profileIds }.toMutableMap()
        var added = 0
        var inProfile = 0
        var missing = 0
        for (index in 0 until contacts.length()) {
            val item = contacts.optJSONObject(index) ?: continue
            val card = VCard.parse(item.optString("vcard")).firstOrNull() ?: continue
            // The same phone knows the contact by its key; another phone recognises it by name and number.
            val savedKey = item.optString("key")
            val key = existing.firstOrNull { savedKey.isNotEmpty() && it.lookupKey == savedKey }?.lookupKey
                ?: existing.firstOrNull { it.isSameAs(card, numbers) }?.lookupKey
                ?: if (canWrite) repository.createContact(card.toInput())?.also { added++ }?.let { repository.lookupKeyOf(it) } else null
            if (key.isNullOrEmpty()) {
                missing++
                continue
            }
            val profile = item.optString("profile", ProfileStore.Principal.id)
            // A contact the owner already filed on this phone stays where it is.
            if (profile == ProfileStore.Principal.id || profile !in profileIds || key in filed) continue
            filed[key] = profile
            inProfile++
        }
        store.assignAll(filed)
        return buildList {
            add(quantity(R.plurals.backup_contacts_filed, inProfile))
            if (added > 0) add(quantity(R.plurals.contacts_added, added))
            if (missing > 0) add(quantity(R.plurals.backup_contacts_missing, missing))
        }.joinToString(", ")
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun quantity(@PluralsRes id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    companion object {
        const val APP = "seca-contacts"
        private const val VERSION = 1
    }
}

/** Same name, and a number in common when the card has one: importing the same file twice adds nothing. */
internal fun SecaContact.isSameAs(card: VCardContact, numbers: PhoneNumbers): Boolean =
    displayName.equals(card.displayName, ignoreCase = true) &&
        (card.phones.isEmpty() || card.phones.any { phone -> phoneNumbers.any { numbers.key(it.raw) == numbers.key(phone.value) } })

internal fun VCardContact.toInput() = ContactInput(
    givenName = givenName.ifBlank { if (familyName.isBlank()) displayName else "" },
    familyName = familyName,
    phones = phones,
    emails = emails,
    addresses = addresses,
    organization = organization,
    jobTitle = jobTitle,
    website = website,
    birthday = birthday,
    note = note,
)

/** Seca Contacts' part of the suite's backup, for the Seca apps signed with the same key only. */
class ContactsSuiteBackup : SuiteBackupProvider() {

    override suspend fun exportPart(): JSONObject = ContactsBackup(requireContext()).export()

    override suspend fun importPart(part: JSONObject): String = ContactsBackup(requireContext()).restore(part)
}
