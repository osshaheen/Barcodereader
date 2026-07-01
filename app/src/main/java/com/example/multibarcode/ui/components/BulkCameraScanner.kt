package com.example.multibarcode.ui.components

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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.multibarcode.BarcodeFrame
import com.example.multibarcode.BulkBarcodeAnalyzer
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Camera view for bulk product capture: draws a box over each code and reports a JPEG thumbnail the
 * moment a code newly appears (via [onCapture]). [highlighted] marks codes already captured.
 */
@Composable
fun BulkCameraScanner(
    onCapture: (value: String, jpeg: ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    torchEnabled: Boolean = false,
    highlighted: Set<String> = emptySet(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val textMeasurer = rememberTextMeasurer()
    val currentOnCapture by rememberUpdatedState(onCapture)
    val currentHighlighted by rememberUpdatedState(highlighted)

    var frame by remember { mutableStateOf(BarcodeFrame(emptyList(), 0, 0)) }

    val analyzer = remember {
        BulkBarcodeAnalyzer(
            onFrame = { frame = it },
            onCapture = { value, jpeg -> currentOnCapture(value, jpeg) },
        )
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraHolder = remember { arrayOfNulls<androidx.camera.core.Camera>(1) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            analyzer.close()
        }
    }

    DisposableEffect(torchEnabled) {
        cameraHolder[0]?.let { cam ->
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
                        cameraHolder[0] = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
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
