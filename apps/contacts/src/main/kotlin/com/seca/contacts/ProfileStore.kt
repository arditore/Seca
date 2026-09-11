package com.seca.contacts

import android.content.Context
import androidx.core.content.edit
import com.seca.core.design.SecaPalette
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A category of contacts, such as "Travail". */
data class Profile(val id: String, val name: String)

/**
 * Seca's own contact categories ("profiles") and preferences.
 *
 * Kept in this app's private storage, on the device only: other apps on the
 * phone cannot see which profile a contact belongs to. Assignments are keyed by
 * the contact's lookup key, which survives the provider re-aggregating
 * contacts, unlike its row id. Seca Phone will read them to show a caller's
 * profile.
 */
class ProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("seca_contacts", Context.MODE_PRIVATE)

    /** Principal first, then the profiles the user created, in creation order. */
    fun profiles(): List<Profile> = listOf(Principal) + customProfiles()

    fun addProfile(name: String): Profile {
        val profile = Profile(id = UUID.randomUUID().toString(), name = name.trim())
        saveCustomProfiles(customProfiles() + profile)
        return profile
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

    fun currentProfileId(): String = prefs.getString(KEY_CURRENT, null) ?: Principal.id

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
    }

    private fun saveAssignments(assignments: Map<String, String>) {
        prefs.edit { putString(KEY_ASSIGNMENTS, JSONObject(assignments).toString()) }
    }

    companion object {
        val Principal = Profile(id = "principal", name = "Principal")
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ASSIGNMENTS = "assignments"
        private const val KEY_CURRENT = "current_profile"
        private const val KEY_PALETTE = "palette"
    }
}
