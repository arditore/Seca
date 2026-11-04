package com.seca.messages

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.seca.core.link.LinkSettings
import com.seca.core.link.handshake.Handshake
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Draws the main screens with made-up conversations, to look at them or to
 * illustrate the app's listing. Skipped unless SECA_SCREENSHOTS names the
 * folder to write the images to.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-xxhdpi")
class ScreenshotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val folder: File? = System.getenv("SECA_SCREENSHOTS")?.let(::File)

    @Before
    fun onlyWhenAsked() {
        assumeTrue("SECA_SCREENSHOTS names no folder", folder != null)
        speak(System.getenv("SECA_SCREENSHOTS_LANGUAGE") ?: "en-rUS")
    }

    /**
     * Draws in one language rather than in whichever one the machine happens to run.
     *
     * The listings carry one set of images per language, and they were all being drawn in the
     * language of the computer that made them. Both halves are set: the phone's own, which the
     * screens read their words from, and the one Java formats dates and numbers with.
     */
    private fun speak(language: String) {
        RuntimeEnvironment.setQualifiers("+$language")
        val parts = language.split("-")
        Locale.setDefault(Locale.Builder().setLanguage(parts[0]).setRegion(parts.getOrNull(1)?.removePrefix("r") ?: "").build())
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

    /** The launcher icon at the size app stores ask for. */
    @Test
    fun icon() {
        val drawable = ApplicationProvider.getApplicationContext<Application>().getDrawable(R.mipmap.ic_launcher) ?: return
        val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_PX, ICON_PX)
        drawable.draw(Canvas(bitmap))
        val target = folder ?: return
        target.mkdirs()
        File(target, "messages-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
                profiles = listOf(Profile.Principal, sampleProfile("famille"), sampleProfile("travail")),
                assignments = mapOf("camille" to "famille", "lea" to "famille", "hugo" to "travail"),
                connected = true,
            ),
            conversations = listOf(
                Conversation(1, CAMILLE, phrase("soon"), now - 4 * minute, unread = 2, outgoing = false),
                Conversation(2, "06 98 76 54 32", phrase("document"), now - hour, unread = 0, outgoing = true),
                Conversation(3, "38123", phrase("code"), now - 3 * hour, unread = 0, outgoing = false),
                Conversation(4, "07 11 22 33 44", "🔒 Seca Link", now - 26 * hour, unread = 0, outgoing = false),
                Conversation(5, "06 55 44 33 22", phrase("callback"), now - 3 * 24 * hour, unread = 0, outgoing = false),
            ),
            link = LinkUi(enabled = true),
        )
    }

    private fun sampleMessages(): List<Message> {
        val now = System.currentTimeMillis()
        val minute = 60_000L
        val invite = Handshake(Handshake.Type.Invite, "ab".repeat(32), "0123456789abcdef", LinkSettings.DefaultRelays.take(3))
        return listOf(
            Message(-1, 1, CAMILLE, phrase("market"), now - 27 * 60 * minute, MessageStatus.Received),
            Message(-2, 1, CAMILLE, phrase("bread"), now - 27 * 60 * minute + minute, MessageStatus.Read),
            Message(1, 1, CAMILLE, phrase("saturday"), now - 26 * 60 * minute, MessageStatus.Received),
            Message(2, 1, CAMILLE, invite.text("Seca Link"), now - 26 * 60 * minute + 2 * minute, MessageStatus.Sent),
            Message(3, 1, CAMILLE, invite.copy(type = Handshake.Type.Accept).text("Seca Link"), now - 26 * 60 * minute + 3 * minute, MessageStatus.Received),
            Message(4, 1, CAMILLE, phrase("tonight"), now - 50 * minute, MessageStatus.Received),
            Message(-5, 1, CAMILLE, phrase("yes"), now - 40 * minute, MessageStatus.Read, encrypted = true, linkId = "a", theirReaction = "❤️"),
            Message(
                -8, 1, CAMILLE, phrase("soon"), now - 4 * minute, MessageStatus.Received,
                encrypted = true, linkId = "c", replyTo = "a", myReaction = "👍", expiresAt = now + 60 * minute,
            ),
        )
    }

    private companion object {
        const val CAMILLE = "06 12 34 56 78"
        const val ICON_PX = 512
    }
}

/**
 * A profile named the way someone drawing these screens in this language would have named it.
 *
 * These are names a person types, not words the app ships, so they do not come from the
 * resources — and left as one language they made every listing's images look half translated.
 */
internal fun sampleProfile(key: String): Profile = Profile(
    key,
    when (Locale.getDefault().language) {
        "fr" -> mapOf("famille" to "Famille", "travail" to "Travail", "sport" to "Sport")
        "es" -> mapOf("famille" to "Familia", "travail" to "Trabajo", "sport" to "Deporte")
        "pl" -> mapOf("famille" to "Rodzina", "travail" to "Praca", "sport" to "Sport")
        else -> mapOf("famille" to "Family", "travail" to "Work", "sport" to "Sport")
    }.getValue(key),
)

/**
 * What the made-up people in these screens say, in the language the screens are drawn in.
 *
 * Words someone types, like the profile names, so they are not in the app's resources — and left
 * in one language they made the English and Spanish listings show a French conversation.
 */
internal fun phrase(key: String): String = when (Locale.getDefault().language) {
    "fr" -> mapOf(
        "market" to "Tu viens au marché dimanche ?",
        "bread" to "Oui, j'apporte le pain",
        "saturday" to "Tu as vu le programme de samedi ?",
        "tonight" to "Coucou ! Tu es dispo ce soir ?",
        "yes" to "Oui, après 19 h",
        "soon" to "Super, à tout à l'heure !",
        "document" to "Merci pour le document, je regarde ce soir.",
        "code" to "Votre code de vérification est 482913",
        "callback" to "Tu peux me rappeler quand tu as un moment ?",
    )
    "es" -> mapOf(
        "market" to "¿Vienes al mercado el domingo?",
        "bread" to "Sí, yo llevo el pan",
        "saturday" to "¿Viste el programa del sábado?",
        "tonight" to "¡Hola! ¿Estás libre esta noche?",
        "yes" to "Sí, después de las 19 h",
        "soon" to "¡Genial, hasta ahora!",
        "document" to "Gracias por el documento, lo miro esta noche.",
        "code" to "Tu código de verificación es 482913",
        "callback" to "¿Me puedes llamar cuando tengas un momento?",
    )
    else -> mapOf(
        "market" to "Are you coming to the market on Sunday?",
        "bread" to "Yes, I will bring the bread",
        "saturday" to "Did you see what is on on Saturday?",
        "tonight" to "Hey! Are you free tonight?",
        "yes" to "Yes, after seven",
        "soon" to "Great, see you shortly!",
        "document" to "Thanks for the document, I will look at it tonight.",
        "code" to "Your verification code is 482913",
        "callback" to "Could you call me back when you have a moment?",
    )
}.getValue(key)
