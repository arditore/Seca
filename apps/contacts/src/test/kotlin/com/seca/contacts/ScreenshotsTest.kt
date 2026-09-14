package com.seca.contacts

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.seca.core.contacts.PhoneNumbers
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
 * Draws the main screens with made-up contacts, to look at them or to
 * illustrate the app's listing. Skipped unless SECA_SCREENSHOTS names the
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
    fun home() = shoot("contacts-home") {
        HomeScreen(sampleUi(), viewModel(), rememberLazyListState(), onOpenSibling = {})
    }

    @Test
    fun homeDark() = shoot("contacts-home-dark", dark = true) {
        HomeScreen(sampleUi(), viewModel(), rememberLazyListState(), onOpenSibling = {})
    }

    @Test
    fun homeProfile() = shoot("contacts-home-famille") {
        HomeScreen(sampleUi().copy(currentProfileId = "famille"), viewModel(), rememberLazyListState(), onOpenSibling = {})
    }

    /** The launcher icon at the size app stores ask for. */
    @Test
    fun icon() {
        val drawable = ApplicationProvider.getApplicationContext<Application>().getDrawable(R.mipmap.ic_launcher) ?: return
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PX, ICON_PX)
        drawable.draw(android.graphics.Canvas(bitmap))
        val target = folder ?: return
        target.mkdirs()
        File(target, "contacts-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        const val ICON_PX = 512
    }

    private fun viewModel() = ContactsViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            SecaTheme(identity = SecaAppIdentity.Contacts, palette = SecaPalette.Ocean, darkTheme = dark) { content() }
        }
        composeRule.waitForIdle()
        val image = composeRule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val target = folder ?: return
        target.mkdirs()
        File(target, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleUi(): ContactsUi {
        val names = listOf(
            "Alice Garnier" to "principal", "Antoine Roux" to "travail", "Camille Durand" to "famille",
            "Chloé Fontaine" to "sport", "Élodie Mercier" to "principal", "Hugo Martin" to "travail",
            "Inès Moreau" to "travail", "Julien Lambert" to "sport", "Léa Petit" to "famille",
            "Lucas Girard" to "principal", "Manon Chevalier" to "famille", "Nicolas Bernard" to "principal",
        )
        val contacts = names.mapIndexed { index, (name, _) ->
            SecaContact(
                id = index + 1L,
                displayName = name,
                phoneNumbers = listOf(PhoneNumber("06 12 34 56 %02d".format(index))),
                isFavorite = index % 4 == 2,
                photoUri = null,
                lookupKey = "contact-$index",
            )
        }
        return ContactsUi(
            numbers = PhoneNumbers("FR"),
            loaded = true,
            contacts = contacts,
            profiles = listOf(
                ProfileStore.Principal,
                Profile("famille", "Famille"),
                Profile("travail", "Travail"),
                Profile("sport", "Sport"),
            ),
            currentProfileId = ProfileStore.ALL,
            assignments = names.mapIndexed { index, (_, profile) -> "contact-$index" to profile }.toMap(),
        )
    }
}
