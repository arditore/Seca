package com.seca.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact
import androidx.compose.foundation.layout.Row

/**
 * French display names for the identity chips.
 *
 * Identifiers stay English per the project constraint, but UI strings are
 * French — `SecaAppIdentity.name` would print "Phone" on an otherwise French
 * screen. This lives in the catalog because it is the only screen that lists
 * all three apps; a real Seca app never shows a picker.
 */
private val SecaAppIdentity.frenchLabel: String
    get() = when (this) {
        SecaAppIdentity.Contacts -> "Contacts"
        SecaAppIdentity.Phone -> "Téléphone"
        SecaAppIdentity.Messages -> "Messages"
    }

private val sampleContact = SecaContact(
    id = 1L,
    displayName = "Camille Durand",
    phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
    isFavorite = true,
    photoUri = null,
)

/** A gallery of every shared component, so the design can be judged as a whole. */
@Composable
fun CatalogScreen() {
    var identity by remember { mutableStateOf(SecaAppIdentity.Contacts) }
    // Dark and dynamic colour are the catalog's starting state by request:
    // it should open looking the way the user expects to use their phone.
    var dark by remember { mutableStateOf(true) }
    var dynamic by remember { mutableStateOf(true) }

    SecaTheme(identity = identity, darkTheme = dark, dynamicColor = dynamic) {
        // Animating the background makes the shared motion spec visible: switching
        // identity or theme should feel like one system reacting, not a hard cut.
        val background by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.background,
            animationSpec = SecaMotion.emphasized(),
            label = "background",
        )
        // The Surface paints edge to edge so no band of stale colour shows behind
        // the system bars; the content itself is inset so nothing collides with
        // the clock or the navigation bar.
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
                    .padding(vertical = 24.dp),
            ) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    SecaAppIdentity.entries.forEach { entry ->
                        FilterChip(
                            selected = identity == entry,
                            onClick = { identity = entry },
                            label = { Text(entry.frenchLabel) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }

                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    FilterChip(
                        selected = dark,
                        onClick = { dark = !dark },
                        label = { Text("Sombre") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = dynamic,
                        onClick = { dynamic = !dynamic },
                        label = { Text("Couleur dynamique") },
                    )
                }

                Text(
                    text = "Avatars",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                )
                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SecaAvatar("CD", null, size = 40.dp, modifier = Modifier.padding(end = 12.dp))
                    SecaAvatar("AB", null, size = 56.dp, modifier = Modifier.padding(end = 12.dp))
                    SecaAvatar("Z", null, size = 72.dp)
                }

                Text(
                    text = "Ligne de contact",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                )
                SecaContactRow(contact = sampleContact, onClick = {})

                Text(
                    text = "État vide",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                )
                SecaEmptyState(
                    title = "Aucun contact",
                    description = "Les contacts que vous ajoutez apparaîtront ici.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
            }
        }
    }
}
