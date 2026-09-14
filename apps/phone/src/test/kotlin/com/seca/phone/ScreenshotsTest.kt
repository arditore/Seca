package com.seca.phone

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.test.core.app.ApplicationProvider
import com.seca.core.contacts.NumberIndex
import com.seca.core.contacts.PhoneNumbers
import com.seca.core.contacts.SharedProfiles
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import com.seca.core.model.PhoneNumber
import com.seca.core.model.Profile
import com.seca.core.model.SecaContact
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the main screens with made-up contacts and calls, to look at them or
 * to illustrate the app's listing. Skipped unless SECA_SCREENSHOTS names the
 * folder to write the images to.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "fr-rFR-w411dp-h914dp-xxhdpi")
class ScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val folder: File? = System.getenv("SECA_SCREENSHOTS")?.let(::File)

    @Before
    fun onlyWhenAsked() {
        assumeTrue("SECA_SCREENSHOTS names no folder", folder != null)
    }

    @Test
    fun home() = shoot("phone-home") {
        HomeScreen(sampleUi(), viewModel(), rememberLazyListState(), onCall = {}, onDeleteCalls = {}, isDefaultDialer = true, onBecomeDefault = {})
    }

    @Test
    fun homeDark() = shoot("phone-home-dark", dark = true) {
        HomeScreen(sampleUi(), viewModel(), rememberLazyListState(), onCall = {}, onDeleteCalls = {}, isDefaultDialer = true, onBecomeDefault = {})
    }

    @Test
    fun dialer() = shoot("phone-dialer") {
        DialerScreen("0612", sampleUi(), viewModel(), onCall = {}, onVoicemail = {})
    }

    @Test
    fun settings() = shoot("phone-settings") {
        SettingsScreen(sampleUi(), viewModel(), withCallLogWrite = {}, isDefaultDialer = true, onBecomeDefault = {})
    }

    @Test
    fun callDetail() = shoot("phone-call-detail") {
        CallDetailScreen("0698765432", sampleUi(), viewModel(), onCall = {}, onDeleteCalls = {})
    }

    private fun viewModel() = PhoneViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun shoot(name: String, dark: Boolean = false, lastWindow: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            SecaTheme(identity = SecaAppIdentity.Phone, palette = SecaPalette.Ocean, darkTheme = dark) { content() }
        }
        composeRule.waitForIdle()
        // A dialog draws in a window of its own, after the screen's.
        val roots = composeRule.onAllNodes(isRoot())
        val image = (if (lastWindow) roots.onLast() else roots[0]).captureToImage().asAndroidBitmap()
        val target = folder ?: return
        target.mkdirs()
        File(target, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleUi(): PhoneUi {
        val numbers = PhoneNumbers("FR")
        val profiles = SharedProfiles(
            profiles = listOf(Profile.Principal, Profile("famille", "Famille"), Profile("travail", "Travail")),
            assignments = mapOf("camille" to "famille", "lea" to "famille", "hugo" to "travail", "ines" to "travail"),
            connected = true,
        )
        val contacts = listOf(
            SecaContact(1, "Camille Durand", listOf(PhoneNumber("06 12 34 56 78")), isFavorite = true, photoUri = null, lookupKey = "camille"),
            SecaContact(2, "Hugo Martin", listOf(PhoneNumber("06 98 76 54 32")), isFavorite = true, photoUri = null, lookupKey = "hugo"),
            SecaContact(3, "Léa Petit", listOf(PhoneNumber("07 11 22 33 44")), isFavorite = true, photoUri = null, lookupKey = "lea"),
            SecaContact(4, "Nicolas Bernard", listOf(PhoneNumber("06 55 44 33 22")), isFavorite = false, photoUri = null, lookupKey = "nicolas"),
            SecaContact(5, "Inès Moreau", listOf(PhoneNumber("06 01 02 03 04")), isFavorite = true, photoUri = null, lookupKey = "ines"),
        )
        val now = System.currentTimeMillis()
        val minute = 60_000L
        val hour = 60 * minute
        val calls = listOf(
            CallRecord(1, "0612345678", CallType.Incoming, now - 12 * minute, 320, Presentation.Allowed, null),
            CallRecord(2, "0698765432", CallType.Missed, now - 2 * hour, 0, Presentation.Allowed, null),
            CallRecord(3, "0698765432", CallType.Missed, now - 2 * hour - 10 * minute, 0, Presentation.Allowed, null),
            CallRecord(4, "0162123456", CallType.Blocked, now - 5 * hour, 0, Presentation.Allowed, null),
            CallRecord(5, "0711223344", CallType.Outgoing, now - 26 * hour, 125, Presentation.Allowed, null),
            CallRecord(6, "+32 470 12 34 56", CallType.Incoming, now - 28 * hour, 40, Presentation.Allowed, null),
            CallRecord(7, "", CallType.Missed, now - 30 * hour, 0, Presentation.Restricted, null),
            CallRecord(8, "0655443322", CallType.Outgoing, now - 50 * hour, 600, Presentation.Allowed, null),
            CallRecord(9, "0601020304", CallType.Incoming, now - 52 * hour, 60, Presentation.Allowed, null),
        )
        val base = PhoneUi(
            numbers = numbers,
            loaded = true,
            calls = calls,
            contacts = contacts,
            index = NumberIndex(contacts, numbers),
            profiles = profiles,
            blockedProfiles = setOf("travail"),
        )
        val keyOf = { call: CallRecord -> if (call.presentation == Presentation.Allowed) numbers.key(call.number) else "hidden" }
        val groups = groupCalls(calls, keyOf, dayOf = { dayOf(it) }).map { CallGroup(it, base.matchOf(it.first())) }
        return base.copy(groups = groups)
    }
}
