package com.seca.phone

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog.Calls
import com.seca.core.suite.SuiteBackup
import com.seca.core.suite.SuiteBackupProvider
import com.seca.phone.screening.BlockFor
import com.seca.phone.screening.BlockMode
import com.seca.phone.screening.ScreeningSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.annotation.PluralsRes

/**
 * Seca Phone's part of the suite's backup: the call history and how calls
 * are filtered. The SIM chosen for each contact belongs to this phone's SIMs
 * and stays behind.
 */
internal class PhoneBackup(private val context: Context) {

    private val screening = ScreeningSettings(context)
    private val resolver = context.contentResolver

    suspend fun export(): JSONObject = withContext(Dispatchers.IO) {
        val calls = if (granted(Manifest.permission.READ_CALL_LOG)) readCalls() else JSONArray()
        // A block with an end belongs to the moment it was set; only a lasting one is carried over.
        val blocked = if (screening.blockFor == BlockFor.UntilLifted) screening.blockedProfiles.toList() else emptyList()
        JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put(
                "screening",
                JSONObject()
                    .put("blockTelemarketing", screening.blockTelemarketing)
                    .put("silenceUnknown", screening.silenceUnknown)
                    .put("blockMode", screening.blockMode.name)
                    .put("blockedProfiles", JSONArray(blocked)),
            )
            .put("calls", calls)
            .put(SuiteBackup.KEY_SUMMARY, quantity(R.plurals.backup_calls_and_filtering, calls.length()))
    }

    /** Puts the filtering back, and the calls this phone does not have yet. Returns what came back. */
    suspend fun restore(part: JSONObject): String = withContext(Dispatchers.IO) {
        part.optJSONObject("screening")?.let { saved ->
            screening.blockTelemarketing = saved.optBoolean("blockTelemarketing", true)
            screening.silenceUnknown = saved.optBoolean("silenceUnknown", false)
            BlockMode.entries.firstOrNull { it.name == saved.optString("blockMode") }?.let { screening.blockMode = it }
            val blocked = saved.optJSONArray("blockedProfiles")?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
            if (blocked.isNotEmpty()) screening.blockedProfiles = blocked.toSet()
        }
        val calls = part.optJSONArray("calls") ?: JSONArray()
        if (calls.length() == 0) return@withContext context.getString(R.string.backup_filtering_restored)
        if (!granted(Manifest.permission.READ_CALL_LOG) || !granted(Manifest.permission.WRITE_CALL_LOG)) {
            return@withContext context.getString(R.string.backup_filtering_restored_no_access)
        }
        val present = HashSet<String>()
        resolver.query(Calls.CONTENT_URI, arrayOf(Calls.NUMBER, Calls.DATE), null, null, null)?.use { c ->
            while (c.moveToNext()) present += "${c.getString(0)}|${c.getLong(1)}"
        }
        var added = 0
        for (index in 0 until calls.length()) {
            val call = calls.getJSONObject(index)
            val key = "${call.optString("number")}|${call.optLong("date")}"
            if (key in present) continue
            val values = ContentValues().apply {
                put(Calls.NUMBER, call.optString("number"))
                put(Calls.DATE, call.optLong("date"))
                put(Calls.DURATION, call.optLong("duration"))
                put(Calls.TYPE, call.optInt("type", Calls.INCOMING_TYPE))
                put(Calls.NUMBER_PRESENTATION, call.optInt("presentation", Calls.PRESENTATION_ALLOWED))
                put(Calls.NEW, 0)
                put(Calls.IS_READ, 1)
            }
            if (runCatching { resolver.insert(Calls.CONTENT_URI, values) }.getOrNull() != null) {
                added++
                present += key
            }
        }
        quantity(R.plurals.backup_calls_added, added)
    }

    private fun readCalls(): JSONArray = JSONArray().apply {
        val projection = arrayOf(Calls.NUMBER, Calls.DATE, Calls.DURATION, Calls.TYPE, Calls.NUMBER_PRESENTATION)
        resolver.query(Calls.CONTENT_URI, projection, null, null, "${Calls.DATE} ASC")?.use { c ->
            while (c.moveToNext()) {
                put(
                    JSONObject()
                        .put("number", c.getString(0).orEmpty())
                        .put("date", c.getLong(1))
                        .put("duration", c.getLong(2))
                        .put("type", c.getInt(3))
                        .put("presentation", c.getInt(4)),
                )
            }
        }
    }

    private fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun quantity(@PluralsRes id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)

    private companion object {
        const val APP = "seca-phone"
        const val VERSION = 1
    }
}

/** Seca Phone's part of the suite's backup, for the Seca apps signed with the same key only. */
class PhoneSuiteBackup : SuiteBackupProvider() {

    override suspend fun exportPart(): JSONObject = PhoneBackup(requireContext()).export()

    override suspend fun importPart(part: JSONObject): String = PhoneBackup(requireContext()).restore(part)
}
