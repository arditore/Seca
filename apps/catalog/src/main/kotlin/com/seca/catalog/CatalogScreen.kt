package com.seca.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaPalette
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaAvatar
import com.seca.core.design.component.SecaContactRow
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaSuiteBar
import com.seca.core.model.PhoneNumber
import com.seca.core.model.SecaContact

private val sampleContact = SecaContact(
    id = 1L,
    displayName = "Camille Durand",
    phoneNumbers = listOf(PhoneNumber("06 12 34 56 78")),
    isFavorite = true,
    photoUri = null,
)

/**
 * A gallery of every shared component, so the design can be judged as a whole.
 *
 * There is no light/dark switch: the theme follows the system, exactly as the
 * real apps will. The only choice offered is the palette, which is also the
 * only choice a Seca user gets.
 */
@Composable
fun CatalogScreen() {
    var identity by remember { mutableStateOf(SecaAppIdentity.Contacts) }
    var palette by remember { mutableStateOf(SecaPalette.Ocean) }

    SecaTheme(identity = identity, palette = palette) {
        Scaffold(
            bottomBar = {
                SecaSuiteBar(current = identity, onSelect = { identity = it })
            },
        ) { innerPadding ->
            // Animating the background makes the shared motion spec visible:
            // switching app or palette should feel like one system reacting.
            val background by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.background,
                animationSpec = SecaMotion.emphasized(),
                label = "background",
            )
            Surface(color = background, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(vertical = 24.dp),
                ) {
                    Text(
                        text = "Palette",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                    )
                    Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SecaPalette.entries.forEach { entry ->
                            FilterChip(
                                selected = palette == entry,
                                onClick = { palette = entry },
                                label = { Text(entry.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }

                    Text(
                        text = "Avatars",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 12.dp),
                    )
                    // Centred, not the Row default of Alignment.Top: three
                    // different sizes sharing a top edge cascade downward.
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                    // Framed, not just shortened. SecaEmptyState carries 32dp of
                    // its own padding and centres its content — correct when it
                    // fills a real screen, but in a gallery strip that reads as
                    // an accidental gap under the heading. Shrinking the box only
                    // reduced it (261px to 156px, against 46-67px elsewhere on
                    // this page). A surface frame makes the component's own
                    // breathing room look deliberate instead, and the frame
                    // itself starts on the page's normal rhythm.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(200.dp),
                    ) {
                        SecaEmptyState(
                            title = "Aucun contact",
                            description = "Les contacts que vous ajoutez apparaîtront ici.",
                        )
                    }
                }
            }
        }
    }
}
