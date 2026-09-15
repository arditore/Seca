package com.seca.messages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.seca.core.design.SecaIcons
import com.seca.core.design.component.SecaEmptyState
import com.seca.core.design.component.SecaTopBar
import com.seca.core.link.SafetyNumber
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.encoding.Base64
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel
import androidx.compose.ui.res.stringResource

/**
 * Scanning a code on the contact's phone: their safety number, to verify an
 * open session, or with [connect], their Seca Link code, to open one.
 */
data class SafetyScanRoute(val address: String, val connect: Boolean = false) : MessagesScreen

/**
 * Reads the QR code on the contact's phone. A safety number that matches this
 * phone's marks the contact as verified; a Seca Link code opens the session.
 * The camera runs only on this screen, and no image is kept.
 */
@Composable
internal fun SafetyScanScreen(route: SafetyScanRoute, ui: MessagesUi, viewModel: MessagesViewModel) {
    val context = LocalContext.current
    val name = ui.nameOf(route.address)
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = stringResource(R.string.scan_their_code), onBack = { viewModel.back() }) },
    ) { padding ->
        when {
            !granted -> SecaEmptyState(
                icon = SecaIcons.Shield,
                title = stringResource(R.string.camera_access),
                description = stringResource(R.string.camera_access_hint, name),
                modifier = Modifier.padding(padding),
                action = { Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.allow)) } },
            )
            route.connect -> ConnectScan(route.address, name, viewModel, Modifier.padding(padding))
            else -> VerifyScan(route.address, name, viewModel, Modifier.padding(padding))
        }
    }
}

/** The contact's safety number, compared with this phone's. */
@Composable
private fun VerifyScan(address: String, name: String, viewModel: MessagesViewModel, modifier: Modifier) {
    val links: LinkPeersViewModel = screenViewModel()
    val safety by produceState<SafetyNumber?>(null) { value = links.safetyNumber(address) }
    var matched by remember { mutableStateOf<Boolean?>(null) }
    val number = safety
    val result = matched
    when {
        result != null -> SecaEmptyState(
            icon = if (result) SecaIcons.Check else SecaIcons.Shield,
            title = stringResource(if (result) R.string.codes_match else R.string.codes_mismatch),
            description = stringResource(if (result) R.string.verified_named else R.string.mismatch_hint, name),
            modifier = modifier,
            action = {
                if (result) {
                    Button(onClick = { viewModel.back() }) { Text(stringResource(R.string.done)) }
                } else {
                    Button(onClick = { matched = null }) { Text(stringResource(R.string.scan_again)) }
                }
            },
        )
        number != null -> ScanFrame(
            hint = stringResource(R.string.aim_safety_code, name),
            modifier = modifier,
            onCode = { text ->
                val same = runCatching { number.matches(Base64.decode(text)) }.getOrDefault(false)
                if (same) links.setVerified(address, true)
                matched = same
            },
        )
    }
}

/** The contact's Seca Link code, which opens the session. */
@Composable
private fun ConnectScan(address: String, name: String, viewModel: MessagesViewModel, modifier: Modifier) {
    val links: LinkPeersViewModel = screenViewModel()
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<LinkPeersViewModel.Scanned?>(null) }
    var connecting by remember { mutableStateOf(false) }
    val result = outcome
    when {
        connecting -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            CircularProgressIndicator(Modifier.padding(top = 96.dp).size(56.dp))
            Text(
                text = stringResource(R.string.connecting_to, name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        result == LinkPeersViewModel.Scanned.Connected -> SecaEmptyState(
            icon = SecaIcons.Lock,
            title = stringResource(R.string.connected_to, name),
            description = stringResource(R.string.connected_hint),
            modifier = modifier,
            action = { Button(onClick = { viewModel.back() }) { Text(stringResource(R.string.done)) } },
        )
        result != null -> SecaEmptyState(
            icon = SecaIcons.Shield,
            title = stringResource(if (result == LinkPeersViewModel.Scanned.NotSeca) R.string.not_link_code else R.string.connection_failed),
            description = when (result) {
                is LinkPeersViewModel.Scanned.Failed -> result.reason
                else -> stringResource(R.string.open_connect_hint, name)
            },
            modifier = modifier,
            action = { Button(onClick = { outcome = null }) { Text(stringResource(R.string.scan_again)) } },
        )
        else -> ScanFrame(
            hint = stringResource(R.string.aim_link_code, name),
            modifier = modifier,
            onCode = { text ->
                connecting = true
                scope.launch {
                    outcome = links.connectScanned(address, text)
                    connecting = false
                }
            },
        )
    }
}

@Composable
private fun ScanFrame(hint: String, onCode: (String) -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        QrScanner(
            onCode = onCode,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(32.dp)),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/** The back camera's picture, read for a QR code; reports the first one found. */
@Composable
private fun QrScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val report by rememberUpdatedState(onCode)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().apply { setSurfaceProvider { request -> surfaceRequest = request } }
        val executor = Executors.newSingleThreadExecutor()
        val reader = QRCodeReader()
        val found = AtomicBoolean(false)
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(executor) { image ->
                    image.use {
                        if (found.get()) return@use
                        val text = decode(reader, it) ?: return@use
                        if (found.compareAndSet(false, true)) context.mainExecutor.execute { report(text) }
                    }
                }
            }
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        try {
            awaitCancellation()
        } finally {
            provider.unbind(preview, analysis)
            executor.shutdown()
        }
    }

    Box(modifier.background(Color.Black)) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
    }
}

/** The luminance of the frame is all a QR code needs; its rotation does not matter. */
private fun decode(reader: QRCodeReader, image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining()).also(buffer::get)
    val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
    return try {
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (none: ReaderException) {
        null
    } finally {
        reader.reset()
    }
}
