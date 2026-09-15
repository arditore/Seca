package com.seca.phone

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seca.core.design.SecaAppIdentity
import com.seca.core.design.SecaIcons
import com.seca.core.design.SecaMotion
import com.seca.core.design.SecaTheme
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaSuiteBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

/** Stands for the voicemail in the pending-call slot, which otherwise holds a number. */
private const val VOICEMAIL = "voicemail"

@Composable
fun PhoneApp(
    callLogGranted: Boolean,
    contactsGranted: Boolean,
    isDefaultDialer: Boolean,
    onPermissionsResult: () -> Unit,
    dialRequest: DialRequest?,
    onDialRequestHandled: () -> Unit,
    callRequest: CallRequest?,
    onCallRequestHandled: () -> Unit,
    viewModel: PhoneViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()
    val scope = rememberCoroutineScope()

    var permanentlyDenied by remember { mutableStateOf(false) }
    val accessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onPermissionsResult()
        // After a refusal, no rationale means "don't ask again": only Settings can grant it now.
        permanentlyDenied = result[Manifest.permission.READ_CALL_LOG] != true &&
            (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG) == false
    }

    // With several SIMs, a contact is called with the SIM chosen for them; otherwise the owner picks one.
    var simChoice by remember { mutableStateOf<SimRoute.Ask?>(null) }
    val startCall: (String) -> Unit = { number ->
        scope.launch {
            when (val route = withContext(Dispatchers.IO) { routeCall(context, number) }) {
                is SimRoute.Direct -> placeCall(context, number, route.account)
                is SimRoute.Ask -> simChoice = route
            }
        }
    }

    // The right to call is asked the first time the user places a call, and the call then goes through.
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val allowCallsText = stringResource(R.string.allow_calls)
    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val target = pendingCall
        pendingCall = null
        when {
            !granted -> Toast.makeText(context, allowCallsText, Toast.LENGTH_LONG).show()
            target == VOICEMAIL -> callVoicemail(context)
            target != null -> startCall(target)
        }
    }
    val onCall: (String) -> Unit = { number ->
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startCall(number)
        } else {
            pendingCall = number
            callLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }
    val onVoicemail: () -> Unit = {
        if (!callVoicemail(context)) {
            pendingCall = VOICEMAIL
            callLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    // Deleting from the history is asked the first time the user does it.
    var pendingLogWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val logWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingLogWrite?.invoke()
        pendingLogWrite = null
    }
    val withCallLogWrite: (() -> Unit) -> Unit = { action ->
        if (context.checkSelfPermission(Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingLogWrite = action
            logWriteLauncher.launch(Manifest.permission.WRITE_CALL_LOG)
        }
    }
    val onDeleteCalls: (List<Long>) -> Unit = { ids -> withCallLogWrite { viewModel.deleteCalls(ids) } }

    // Becoming the phone app: notifications are asked first, since the incoming-call
    // notification is what shows a call while the phone is in use.
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onPermissionsResult()
    }
    val requestRole = {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_DIALER) && !roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestRole()
    }
    val onBecomeDefault: () -> Unit = { withNotificationPermission(context, notificationLauncher, requestRole) }

    // Held above the screen transitions, so the history keeps its scroll position.
    val homeListState = rememberLazyListState()

    LaunchedEffect(callLogGranted, contactsGranted) { viewModel.start(callLogGranted, contactsGranted) }
    LaunchedEffect(dialRequest) {
        if (dialRequest != null) {
            viewModel.openDialer(dialRequest.number)
            onDialRequestHandled()
        }
    }
    LaunchedEffect(callRequest) {
        if (callRequest != null) {
            onCall(callRequest.number)
            onCallRequestHandled()
        }
    }
    // A block with an end may have lifted itself while the app was away.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshScreening()
        onPauseOrDispose { }
    }
    BackHandler(enabled = viewModel.backStack.size > 1) { viewModel.back() }

    SecaTheme(identity = SecaAppIdentity.Phone, palette = ui.palette) {
        simChoice?.let { ask ->
            SimChooserDialog(
                sims = ask.sims,
                title = stringResource(R.string.which_sim),
                rememberLabel = stringResource(R.string.always_use_sim, ask.contactName ?: ui.numbers.display(ask.number)),
                onDismiss = { simChoice = null },
                onPick = { sim, keep ->
                    if (keep) SimPreferences(context)[ask.key] = sim.handle
                    placeCall(context, ask.number, sim.handle)
                    simChoice = null
                },
            )
        }
        // The keypad works without access to the history: calling must never depend on it.
        if (!callLogGranted && viewModel.backStack.last() !is PhoneScreen.Dialer) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                bottomBar = { SecaSuiteBar(current = SecaAppIdentity.Phone, onSelect = { openSibling(context, it) }) },
            ) { padding ->
                SecaEmptyState(
                    icon = SecaIcons.Phone,
                    title = stringResource(R.string.permission_title),
                    description = stringResource(R.string.permission_description),
                    modifier = Modifier.padding(padding),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (permanentlyDenied) {
                                Button(onClick = { openAppSettings(context) }) { Text(stringResource(R.string.open_settings)) }
                            } else {
                                Button(
                                    onClick = {
                                        accessLauncher.launch(
                                            arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS),
                                        )
                                    },
                                ) { Text(stringResource(R.string.allow_access)) }
                            }
                            TextButton(onClick = { viewModel.openDialer("") }) { Text(stringResource(R.string.open_keypad)) }
                        }
                    },
                )
            }
        } else {
            AnimatedContent(
                targetState = viewModel.backStack.last() to viewModel.backStack.size,
                transitionSpec = {
                    val forward = targetState.second >= initialState.second
                    val keypad = targetState.first is PhoneScreen.Dialer || initialState.first is PhoneScreen.Dialer
                    if (keypad) {
                        // The keypad rises from the bottom and sinks back down.
                        (slideInVertically(SecaMotion.emphasized()) { if (forward) it / 3 else -it / 12 } + fadeIn(SecaMotion.standard()))
                            .togetherWith(
                                slideOutVertically(SecaMotion.emphasized()) { if (forward) -it / 12 else it / 3 } +
                                    fadeOut(SecaMotion.standard()),
                            )
                    } else {
                        val direction = if (forward) 1 else -1
                        (slideInHorizontally(SecaMotion.emphasized()) { direction * it / 4 } + fadeIn(SecaMotion.standard()))
                            .togetherWith(
                                slideOutHorizontally(SecaMotion.emphasized()) { -direction * it / 4 } +
                                    fadeOut(SecaMotion.standard()),
                            )
                    }
                },
                label = "screens",
            ) { (screen, _) ->
                when (screen) {
                    PhoneScreen.Home -> HomeScreen(
                        ui = ui,
                        viewModel = viewModel,
                        listState = homeListState,
                        onCall = onCall,
                        onDeleteCalls = onDeleteCalls,
                        isDefaultDialer = isDefaultDialer,
                        onBecomeDefault = onBecomeDefault,
                    )
                    is PhoneScreen.Dialer -> DialerScreen(screen.initial, ui, viewModel, onCall, onVoicemail)
                    is PhoneScreen.CallDetail -> CallDetailScreen(screen.number, ui, viewModel, onCall, onDeleteCalls)
                    PhoneScreen.Settings -> SettingsScreen(ui, viewModel, withCallLogWrite, isDefaultDialer, onBecomeDefault)
                }
            }
        }
    }
}
