package com.seca.core.design.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.core.content.edit
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaGroupItem
import com.seca.core.design.component.SecaSettingRow
import androidx.compose.ui.res.stringResource
import com.seca.core.design.R

/** The protections an app's owner can turn on; each Seca app keeps its own. Both are off by default. */
class SecaPrivacySettings(context: Context) {

    private val prefs = context.getSharedPreferences("seca_privacy", Context.MODE_PRIVATE)

    var lockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK, false)
        set(value) = prefs.edit { putBoolean(KEY_LOCK, value) }

    var privateScreen: Boolean
        get() = prefs.getBoolean(KEY_PRIVATE_SCREEN, false)
        set(value) = prefs.edit { putBoolean(KEY_PRIVATE_SCREEN, value) }

    private companion object {
        const val KEY_LOCK = "lock"
        const val KEY_PRIVATE_SCREEN = "private_screen"
    }
}

/**
 * Asks for the fingerprint, the face or the phone's own code before an app
 * shows anything, when its owner turned the lock on: on opening, and when
 * coming back after more than [GRACE_MILLIS] away. The activity calls
 * [onStart] and [onStop] from its own.
 */
class SecaAppLock(private val activity: Activity, val appName: String) {

    private val settings = SecaPrivacySettings(activity)
    private var leftAt = 0L

    var locked by mutableStateOf(settings.lockEnabled)
        private set

    fun onStart() {
        val away = leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > GRACE_MILLIS
        if (settings.lockEnabled && away) locked = true
        applyPrivateScreen(activity, settings.privateScreen)
    }

    fun onStop() {
        if (!activity.isChangingConfigurations) leftAt = SystemClock.elapsedRealtime()
    }

    /** Shows the system's own prompt; the app opens only when it succeeds. */
    fun unlock() {
        if (!canLock(activity)) {
            // The phone has no screen lock left: there is nothing to check against.
            locked = false
            return
        }
        BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.design_unlock_app, appName))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
            .authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        locked = false
                    }
                },
            )
    }

    companion object {
        /** Switching to another app for a moment, to copy a code for instance, does not lock. */
        private const val GRACE_MILLIS = 30_000L
        private const val AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /** Whether the phone has a screen lock to check against. */
        fun canLock(context: Context): Boolean =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

        /** Blocks screenshots and hides the app's preview in the recent apps, or lifts both. */
        fun applyPrivateScreen(activity: Activity, on: Boolean) {
            if (on) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            activity.showInRecentsPreview(!on)
        }
    }
}

/** Shows [content] only once the app is unlocked; asks for the unlock straight away. */
@Composable
fun SecaLockGate(lock: SecaAppLock, identity: SecaAppIdentity, content: @Composable () -> Unit) {
    if (lock.locked) {
        LaunchedEffect(Unit) { lock.unlock() }
        SecaTheme(identity = identity) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                SecaEmptyState(
                    icon = SecaIcons.Lock,
                    title = stringResource(R.string.design_app_locked, lock.appName),
                    description = stringResource(R.string.design_app_locked_hint),
                    modifier = Modifier.systemBarsPadding(),
                    action = { Button(onClick = lock::unlock) { Text(stringResource(R.string.design_unlock)) } },
                )
            }
        }
    } else {
        content()
    }
}

/** The lock and private-screen switches, for an app's settings. */
@Composable
fun SecaPrivacySettingsGroup(appName: String) {
    val context = LocalContext.current
    val settings = remember { SecaPrivacySettings(context) }
    val canLock = remember { SecaAppLock.canLock(context) }
    var lock by remember { mutableStateOf(settings.lockEnabled && canLock) }
    var privateScreen by remember { mutableStateOf(settings.privateScreen) }

    SecaGroupItem(index = 0, count = 2) {
        SecaSettingRow(
            icon = SecaIcons.Lock,
            title = stringResource(R.string.design_lock_app, appName),
            subtitle = stringResource(if (canLock) R.string.design_lock_app_on else R.string.design_lock_app_unavailable),
            modifier = Modifier.toggleable(value = lock, enabled = canLock, role = Role.Switch) {
                lock = it
                settings.lockEnabled = it
            },
            trailing = { Switch(checked = lock, onCheckedChange = null, enabled = canLock) },
        )
    }
    SecaGroupItem(index = 1, count = 2) {
        SecaSettingRow(
            icon = SecaIcons.VisibilityOff,
            title = stringResource(R.string.design_private_screen),
            subtitle = stringResource(R.string.design_private_screen_hint),
            modifier = Modifier.toggleable(value = privateScreen, role = Role.Switch) {
                privateScreen = it
                settings.privateScreen = it
                context.findActivity()?.let { activity -> SecaAppLock.applyPrivateScreen(activity, it) }
            },
            trailing = { Switch(checked = privateScreen, onCheckedChange = null) },
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
