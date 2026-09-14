package com.seca.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * A QR code drawn on the phone, for someone standing by to scan: dark modules
 * on white whatever the theme, so any camera reads it. Nothing goes through a
 * network.
 */
@Composable
fun SecaQrCode(content: String, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val image = remember(content) {
        val hints = mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val dark = Color.Black.toArgb()
        val light = Color.White.toArgb()
        val bitmap = createBitmap(matrix.width, matrix.height)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) bitmap[x, y] = if (matrix[x, y]) dark else light
        }
        bitmap.asImageBitmap()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(20.dp),
    ) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            filterQuality = FilterQuality.None,
            modifier = Modifier.size(size),
        )
    }
}
