package com.seca.contacts

import android.content.Context
import androidx.core.content.edit
import com.seca.core.contacts.SharedProfilesContract
import com.seca.core.design.SecaPalette
import com.seca.core.model.Profile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Seca's own contact categories ("profiles") and preferences.
 *
 * Kept in this app's private storage, on the device only. Other apps on the
 * phone cannot see which profile a contact belongs to; the Seca apps signed
 * with the same key read them through [ProfilesProvider], and every change is
 * announced so they refresh. Assignments are keyed by the contact's lookup
 * key, which survives the provider re-aggregating contacts, unlike its row id.
 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("seca_contacts", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver

    /** Principal first, then the profiles the user created, in creation order. */
    fun profiles(): List<Profile> = listOf(Principal) + customProfiles()

    fun addProfile(name: String): Profile {
        val profile = Profile(id = UUID.randomUUID().toString(), name = name.trim())
        saveCustomProfiles(customProfiles() + profile)
        return profile
    }

    /** Puts back a profile from a backup, keeping its id so that its contacts find it again. */
    fun restoreProfile(profile: Profile) {
        if (profile.id == Principal.id || profile.id == ALL || customProfiles().any { it.id == profile.id }) return
        saveCustomProfiles(customProfiles() + profile)
    }

    fun renameProfile(id: String, name: String) {
        saveCustomProfiles(customProfiles().map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /** Removes a profile; its contacts fall back to Principal. No contact is deleted. */
    fun deleteProfile(id: String) {
        saveCustomProfiles(customProfiles().filterNot { it.id == id })
        saveAssignments(assignments().filterValues { it != id })
        if (currentProfileId() == id) setCurrentProfile(Principal.id)
    }

    fun assignments(): Map<String, String> {
        val json = prefs.getString(KEY_ASSIGNMENTS, null)?.let { JSONObject(it) } ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun assign(lookupKey: String, profileId: String) {
        if (lookupKey.isEmpty()) return
        val updated = assignments().toMutableMap()
        if (profileId == Principal.id) updated.remove(lookupKey) else updated[lookupKey] = profileId
        saveAssignments(updated)
    }

    /** Replaces every assignment at once, announcing a single change: for a backup being restored. */
    fun assignAll(assignments: Map<String, String>) {
        saveAssignments(assignments.filter { (key, profileId) -> key.isNotEmpty() && profileId != Principal.id })
    }

    /** [ALL] until the owner picks a profile: the list opens on every contact. */
    fun currentProfileId(): String = prefs.getString(KEY_CURRENT, null) ?: ALL

    fun setCurrentProfile(id: String) {
        prefs.edit { putString(KEY_CURRENT, id) }
    }

    /** Null, the default, means the wallpaper colours. */
    fun palette(): SecaPalette? {
        val name = prefs.getString(KEY_PALETTE, null) ?: return null
        return SecaPalette.entries.firstOrNull { it.name == name }
    }

    fun setPalette(palette: SecaPalette?) {
        prefs.edit {
            if (palette == null) remove(KEY_PALETTE) else putString(KEY_PALETTE, palette.name)
        }
        announceChange()
    }

    private fun customProfiles(): List<Profile> {
        val json = prefs.getString(KEY_PROFILES, null)?.let { JSONArray(it) } ?: return emptyList()
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            Profile(id = item.getString("id"), name = item.getString("name"))
        }
    }

    private fun saveCustomProfiles(profiles: List<Profile>) {
        val json = JSONArray()
        profiles.forEach { json.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit { putString(KEY_PROFILES, json.toString()) }
        announceChange()
    }

    private fun saveAssignments(assignments: Map<String, String>) {
        prefs.edit { putString(KEY_ASSIGNMENTS, JSONObject(assignments).toString()) }
        announceChange()
    }

    /** Tells Seca Phone and Seca Messages to read the profiles again. */
    private fun announceChange() {
        resolver.notifyChange(SharedProfilesContract.BASE_URI, null)
    }

    companion object {
        /** Not a profile: the choice that shows every contact, whatever their profile. */
        const val ALL = "all"

        val Principal = Profile.Principal
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ASSIGNMENTS = "assignments"
        private const val KEY_CURRENT = "current_profile"
        private const val KEY_PALETTE = "palette"
    }
}
