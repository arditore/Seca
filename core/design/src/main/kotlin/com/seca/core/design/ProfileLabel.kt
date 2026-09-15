package com.seca.core.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.seca.core.model.Profile

/** A profile's name on screen. Principal, which the owner cannot rename, is named in the phone's language. */
@Composable
fun Profile.label(): String = if (id == Profile.Principal.id) stringResource(R.string.design_profile_principal) else name

/** [label] outside a composition, for notifications and the like. */
fun Profile.label(context: Context): String =
    if (id == Profile.Principal.id) context.getString(R.string.design_profile_principal) else name
