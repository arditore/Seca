package com.seca.phone

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The names carriers sent with incoming calls. Telecom does not keep them in the call
 * history, so the phone app keeps the latest per number, on this phone only, for the
 * 200 most recent callers.
 *
 * Read once into memory, then every lookup is a map hit; the file is written only when a
 * name is new or changed.
 *
 * That first read is a file, and the thing that asks for it is a call arriving: Telecom drives
 * the call views from its own callbacks on the thread that draws, so [warm] gets the read out
 * of the way while nothing is happening, and the lookups that matter find the map already
 * there. A call that beats it to the punch still works; it only pays for the read.
 */
object CarrierNames {
    private const val FILE = "carrier_names"
    private const val ALL = "all"
    private const val LIMIT = 200

    /** Oldest first, so the first entry is the one to drop. */
    private var names: LinkedHashMap<String, String>? = null

    /** Numbers match by their last nine digits, so "+33 6 12 34 56 78" and "06 12 34 56 78" are one. */
    internal fun keyOf(number: String): String = number.filter(Char::isDigit).takeLast(9)

    /** Reads the file before a call needs it, off the thread that draws. */
    fun warm(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch { synchronized(this@CarrierNames) { loaded(app) } }
    }

    fun remember(context: Context, number: String, name: String) {
        val key = keyOf(number).ifEmpty { return }
        // A tab or a newline would be read back as the end of this entry; a carriage return
        // would too, because lineSequence() breaks on a bare one as readily as on a newline.
        val clean = name.filterNot { it == '\n' || it == '\r' || it == '\t' }.trim().ifEmpty { return }
        synchronized(this) {
            val all = loaded(context)
            if (all[key] == clean) return
            all.remove(key)
            all[key] = clean
            while (all.size > LIMIT) all.remove(all.keys.first())
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
                putString(ALL, all.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            }
        }
    }

    fun of(context: Context, number: String): String? {
        val key = keyOf(number).ifEmpty { return null }
        return synchronized(this) { loaded(context)[key] }
    }

    private fun loaded(context: Context): LinkedHashMap<String, String> = names ?: run {
        val read = LinkedHashMap<String, String>()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ALL, null)
            ?.lineSequence()
            ?.forEach { line ->
                val at = line.indexOf('\t')
                if (at > 0 && at < line.length - 1) read[line.substring(0, at)] = line.substring(at + 1)
            }
        read.also { names = it }
    }
}
