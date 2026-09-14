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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.encoding.Base64
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel

/** Scanning the safety number shown on the contact's phone. */
data class SafetyScanRoute(val address: String) : MessagesScreen

/**
 * Reads the QR code on the contact's phone and compares it with this one's
 * safety number. A match marks the contact as verified. The camera runs only
 * on this screen, and no image is kept.
 */
@Composable
internal fun SafetyScanScreen(route: SafetyScanRoute, ui: MessagesUi, viewModel: MessagesViewModel) {
    val context = LocalContext.current
    val links: LinkPeersViewModel = screenViewModel()
    val name = ui.nameOf(route.address)
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }
    val safety by produceState<SafetyNumber?>(null) { value = links.safetyNumber(route.address) }
    var matched by remember { mutableStateOf<Boolean?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { SecaTopBar(title = "Scanner son code", onBack = { viewModel.back() }) },
    ) { padding ->
        val number = safety
        val result = matched
        when {
            !granted -> SecaEmptyState(
                icon = SecaIcons.Shield,
                title = "Accès à l'appareil photo",
                description = "Pour lire le code affiché sur le téléphone de $name. Aucune image n'est gardée.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Autoriser") } },
            )
            result != null -> SecaEmptyState(
                icon = if (result) SecaIcons.Check else SecaIcons.Shield,
                title = if (result) "Codes identiques" else "Les codes ne correspondent pas",
                description = if (result) {
                    "$name est vérifié : personne ne s'interpose entre vos deux téléphones."
                } else {
                    "Vérifiez que c'est bien le code de $name. S'il ne correspond toujours pas, quelqu'un pourrait " +
                        "s'interposer : n'échangez rien de sensible."
                },
                modifier = Modifier.padding(padding),
                action = {
                    if (result) {
                        Button(onClick = { viewModel.back() }) { Text("Terminé") }
                    } else {
                        Button(onClick = { matched = null }) { Text("Scanner à nouveau") }
                    }
                },
            )
            number != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            ) {
                QrScanner(
                    onCode = { text ->
                        val same = runCatching { number.matches(Base64.decode(text)) }.getOrDefault(false)
                        if (same) links.setVerified(route.address, true)
                        matched = same
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp)),
                )
                Text(
                    text = "Visez le code de sécurité affiché sur le téléphone de $name.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
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
