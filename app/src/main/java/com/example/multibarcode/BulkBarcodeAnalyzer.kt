package com.example.multibarcode

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.multibarcode.util.ImageCapture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Like [BarcodeAnalyzer], but also crops a small product thumbnail for every barcode the moment it
 * newly appears — used by the bulk "scan many products" flow.
 *
 * @param onFrame   latest set of detected codes (for the live overlay).
 * @param onCapture invoked once per newly seen code with a JPEG thumbnail of the product.
 */
class BulkBarcodeAnalyzer(
    private val onFrame: (BarcodeFrame) -> Unit,
    private val onCapture: (value: String, jpeg: ByteArray) -> Unit,
    private val reappearMillis: Long = 1500L,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // Only standard retail product codes + QR; ML Kit validates EAN/UPC checksums,
            // so partial/garbage reads are rejected automatically.
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_QR_CODE,
            )
            .build()
    )
    private val lastSeen = HashMap<String, Long>()

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)
        val rotated = rotation == 90 || rotation == 270
        val width = if (rotated) imageProxy.height else imageProxy.width
        val height = if (rotated) imageProxy.width else imageProxy.height

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val detected = barcodes.mapNotNull { b ->
                    val box = b.boundingBox ?: return@mapNotNull null
                    val value = b.rawValue ?: b.displayValue ?: return@mapNotNull null
                    DetectedBarcode(box, value, "")
                }
                onFrame(BarcodeFrame(detected, width, height))

                val now = SystemClock.uptimeMillis()
                val fresh = detected.filter { d ->
                    val prev = lastSeen[d.value]
                    prev == null || now - prev > reappearMillis
                }
                detected.forEach { lastSeen[it.value] = now }
                lastSeen.entries.removeAll { now - it.value > reappearMillis }

                if (fresh.isNotEmpty()) {
                    try {
                        val upright = ImageCapture.rotate(imageProxy.toBitmap(), rotation)
                        fresh.forEach { d ->
                            ImageCapture.cropProductThumbnailJpeg(upright, d.boundingBox)?.let { jpeg ->
                                onCapture(d.value, jpeg)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = scanner.close()
}
