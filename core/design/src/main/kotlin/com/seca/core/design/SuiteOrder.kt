package com.seca.core.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

/**
 * The order the three Seca apps stand in, in the bar along the bottom of all three.
 *
 * Asked for by somebody who reaches for the telephone and keeps finding the contacts. One app
 * cannot read another's settings, so each keeps its own copy and whichever one is changed tells
 * the other two; without that the bar would stand in a different order depending on which app
 * you happened to be looking at it from.
 *
 * Kept in memory as well as on disk, so a bar on one screen follows a change made on another
 * without waiting to be built again.
 */
object SuiteOrder {

    private var remembered: MutableState<List<SecaAppIdentity>>? = null

    /** The order to show the apps in. Reads as state, so a bar follows a change at once. */
    @Composable
    fun current(): List<SecaAppIdentity> = state(LocalContext.current).value

    /** Changes the order on this phone. Telling the sibling apps is `:core:suite`'s to do. */
    fun store(context: Context, order: List<SecaAppIdentity>) {
        val whole = complete(order)
        notes(context).edit { putString(KEY, whole.joinToString(SEPARATOR) { it.name }) }
        state(context).value = whole
    }

    /** The order as it stands on disk, for the first read and after a sibling has written. */
    fun read(context: Context): List<SecaAppIdentity> = complete(
        notes(context).getString(KEY, null)
            ?.split(SEPARATOR)
            ?.mapNotNull { name -> SecaAppIdentity.entries.firstOrNull { it.name == name } }
            .orEmpty(),
    )

    /**
     * The three apps, in the order given, whatever was in it.
     *
     * A stored order written by a newer version, or a half-written one, would otherwise leave
     * an app out of the bar altogether: anything missing goes on the end in its usual place.
     */
    private fun complete(order: List<SecaAppIdentity>): List<SecaAppIdentity> =
        order.distinct() + SecaAppIdentity.entries.filterNot { it in order }

    private fun state(context: Context): MutableState<List<SecaAppIdentity>> =
        remembered ?: mutableStateOf(read(context)).also { remembered = it }

    private fun notes(context: Context) =
        context.getSharedPreferences("suite-order", Context.MODE_PRIVATE)

    private const val KEY = "order"
    private const val SEPARATOR = ","
}
