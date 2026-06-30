package com.example.multibarcode.ui.components

import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.multibarcode.BarcodeAnalyzer
import com.example.multibarcode.BarcodeFrame
import com.example.multibarcode.DetectedBarcode
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Live camera preview that detects every barcode/QR in view, draws a box around each, and calls
 * [onScan] exactly once each time a code newly appears (a code must leave the frame for
 * [reappearMillis] before it can fire again — this is what lets continuous scanning add each
 * distinct product once instead of hundreds of times).
 *
 * @param highlighted values already in the basket, drawn in the accent colour with a tick-like ring.
 */
@Composable
fun CameraScanner(
    onScan: (DetectedBarcode) -> Unit,
    modifier: Modifier = Modifier,
    torchEnabled: Boolean = false,
    highlighted: Set<String> = emptySet(),
    reappearMillis: Long = 1200L,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val textMeasurer = rememberTextMeasurer()

    val currentOnScan by rememberUpdatedState(onScan)
    val currentHighlighted by rememberUpdatedState(highlighted)

    var frame by remember { mutableStateOf(BarcodeFrame(emptyList(), 0, 0)) }

    // value -> last time it was seen; used to decide when a sighting counts as "new".
    val lastSeen = remember { HashMap<String, Long>() }
    val analyzer = remember {
        BarcodeAnalyzer { f ->
            frame = f
            val now = SystemClock.uptimeMillis()
            f.barcodes.forEach { code ->
                val prev = lastSeen[code.value]
                if (prev == null || now - prev > reappearMillis) {
                    currentOnScan(code)
                }
                lastSeen[code.value] = now
            }
            // Drop stale entries so a code can be re-added after it disappears.
            lastSeen.entries.removeAll { now - it.value > reappearMillis }
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraHolder = remember { CameraHolder() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            analyzer.close()
        }
    }

    // React to torch toggles after the camera is bound.
    DisposableEffect(torchEnabled) {
        cameraHolder.camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torchEnabled)
        }
        onDispose { }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, analyzer) }
                    try {
                        provider.unbindAll()
                        cameraHolder.camera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        cameraHolder.camera?.let { cam ->
                            if (cam.cameraInfo.hasFlashUnit()) cam.cameraControl.enableTorch(torchEnabled)
                        }
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val imgW = frame.imageWidth
            val imgH = frame.imageHeight
            if (imgW <= 0 || imgH <= 0) return@Canvas

            val scale = max(size.width / imgW, size.height / imgH)
            val dx = (size.width - imgW * scale) / 2f
            val dy = (size.height - imgH * scale) / 2f

            frame.barcodes.forEach { code ->
                drawBarcode(code, scale, dx, dy, currentHighlighted.contains(code.value), textMeasurer)
            }
        }
    }
}

private class CameraHolder {
    var camera: androidx.camera.core.Camera? = null
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBarcode(
    code: DetectedBarcode,
    scale: Float,
    dx: Float,
    dy: Float,
    highlighted: Boolean,
    textMeasurer: TextMeasurer,
) {
    val b = code.boundingBox
    val left = b.left * scale + dx
    val top = b.top * scale + dy
    val right = b.right * scale + dx
    val bottom = b.bottom * scale + dy

    val accent = Color(0xFF00E676)
    val color = if (highlighted) Color(0xFFFFD54F) else accent

    drawRect(
        color = color.copy(alpha = 0.18f),
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
    )
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        style = Stroke(width = 6f),
    )

    val label = code.value
    val measured = textMeasurer.measure(
        text = label,
        style = TextStyle(color = Color.White, fontSize = 13.sp),
    )
    val padding = 8f
    val bgWidth = measured.size.width + padding * 2
    val bgHeight = measured.size.height + padding * 2
    val bgTop = (top - bgHeight).coerceAtLeast(0f)
    drawRect(
        color = Color(0xCC000000),
        topLeft = Offset(left, bgTop),
        size = Size(bgWidth, bgHeight),
    )
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(left + padding, bgTop + padding),
    )
}
