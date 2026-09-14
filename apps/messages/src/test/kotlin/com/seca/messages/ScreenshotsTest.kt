package com.seca.messages

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.seca.messages.sms.Conversation
import com.seca.messages.sms.Message
import com.seca.messages.sms.MessageStatus
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
 * Draws the main screens with made-up conversations, to look at them or to
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
    fun conversations() = shoot("messages-conversations") {
        ConversationsScreen(sampleUi(), viewModel(), rememberLazyListState(), isDefaultApp = true, onBecomeDefault = {})
    }

    @Test
    fun conversationsDark() = shoot("messages-conversations-dark", dark = true) {
        ConversationsScreen(sampleUi(), viewModel(), rememberLazyListState(), isDefaultApp = true, onBecomeDefault = {})
    }

    @Test
    fun conversation() = shoot("messages-conversation") {
        ConversationContent(MessagesScreen.Conversation(1, CAMILLE), sampleUi(), viewModel(), isDefaultApp = true, sampleMessages())
    }

    @Test
    fun conversationDark() = shoot("messages-conversation-dark", dark = true) {
        ConversationContent(MessagesScreen.Conversation(1, CAMILLE), sampleUi(), viewModel(), isDefaultApp = true, sampleMessages())
    }

    @Test
    fun settings() = shoot("messages-settings") {
        SettingsScreen(sampleUi(), viewModel(), isDefaultApp = true, onBecomeDefault = {})
    }

    private fun viewModel() = MessagesViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            SecaTheme(identity = SecaAppIdentity.Messages, palette = SecaPalette.Ocean, darkTheme = dark) { content() }
        }
        composeRule.waitForIdle()
        val image = composeRule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap()
        val target = folder ?: return
        target.mkdirs()
        File(target, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleUi(): MessagesUi {
        val numbers = PhoneNumbers("FR")
        val contacts = listOf(
            SecaContact(1, "Camille Durand", listOf(PhoneNumber(CAMILLE)), isFavorite = true, photoUri = null, lookupKey = "camille"),
            SecaContact(2, "Hugo Martin", listOf(PhoneNumber("06 98 76 54 32")), isFavorite = false, photoUri = null, lookupKey = "hugo"),
            SecaContact(3, "Léa Petit", listOf(PhoneNumber("07 11 22 33 44")), isFavorite = false, photoUri = null, lookupKey = "lea"),
            SecaContact(4, "Nicolas Bernard", listOf(PhoneNumber("06 55 44 33 22")), isFavorite = false, photoUri = null, lookupKey = "nicolas"),
        )
        val now = System.currentTimeMillis()
        val minute = 60_000L
        val hour = 60 * minute
        return MessagesUi(
            numbers = numbers,
            loaded = true,
            contacts = contacts,
            index = NumberIndex(contacts, numbers),
            profiles = SharedProfiles(
                profiles = listOf(Profile.Principal, Profile("famille", "Famille"), Profile("travail", "Travail")),
                assignments = mapOf("camille" to "famille", "lea" to "famille", "hugo" to "travail"),
                connected = true,
            ),
            conversations = listOf(
                Conversation(1, CAMILLE, "Super, à tout à l'heure !", now - 4 * minute, unread = 2, outgoing = false),
                Conversation(2, "06 98 76 54 32", "Merci pour le document, je regarde ce soir.", now - hour, unread = 0, outgoing = true),
                Conversation(3, "38123", "Votre code de vérification est 482913", now - 3 * hour, unread = 0, outgoing = false),
                Conversation(4, "07 11 22 33 44", "Bonne soirée !", now - 26 * hour, unread = 0, outgoing = false),
                Conversation(5, "06 55 44 33 22", "Tu peux me rappeler quand tu as un moment ?", now - 3 * 24 * hour, unread = 0, outgoing = false),
            ),
        )
    }

    private fun sampleMessages(): List<Message> {
        val now = System.currentTimeMillis()
        val minute = 60_000L
        return listOf(
            Message(1, 1, CAMILLE, "Tu as vu le programme de samedi ?", now - 26 * 60 * minute, MessageStatus.Received),
            Message(2, 1, CAMILLE, "Pas encore, je regarde demain", now - 26 * 60 * minute + 3 * minute, MessageStatus.Delivered),
            Message(3, 1, CAMILLE, "Coucou ! Tu es dispo ce soir ?", now - 50 * minute, MessageStatus.Received),
            Message(4, 1, CAMILLE, "Il y a un nouveau café près de la gare", now - 49 * minute, MessageStatus.Received),
            Message(5, 1, CAMILLE, "Oui, après 19 h", now - 40 * minute, MessageStatus.Sent),
            Message(-6, 1, CAMILLE, "Je réserve une table pour quatre.", now - 10 * minute, MessageStatus.Read, encrypted = true, linkId = "a"),
            Message(-7, 1, CAMILLE, "Super, à tout à l'heure !", now - 4 * minute, MessageStatus.Received, encrypted = true, linkId = "b"),
        )
    }

    private companion object {
        const val CAMILLE = "06 12 34 56 78"
    }
}
