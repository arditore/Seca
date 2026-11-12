package com.seca.core.design.component

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.seca.core.design.R
import com.seca.core.design.SecaIcons

/**
 * Whether a face takes a colour of its own rather than its profile's (#52).
 *
 * By profile is the default and says where a contact is filed. Most people have one profile,
 * so in practice every face is the same colour and the colour says nothing, which is what
 * this is for.
 */
@Composable
fun SecaColourPerPersonRow(on: Boolean, onChange: (Boolean) -> Unit) {
    SecaSettingRow(
        icon = SecaIcons.Palette,
        title = stringResource(R.string.design_colour_per_person),
        subtitle = stringResource(R.string.design_colour_per_person_hint),
        modifier = Modifier.toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}
