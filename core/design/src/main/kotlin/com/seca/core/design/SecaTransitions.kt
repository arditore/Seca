package com.seca.core.design

import android.app.Activity
import android.os.Build

/**
 * Moving between Seca apps, and back from one, happens without animation, as
 * between tabs. Android 14 lets an activity say so for itself; before, the apps
 * still open each other without animation, and only going back keeps the
 * system's own.
 */
fun Activity.switchWithoutAnimation() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
