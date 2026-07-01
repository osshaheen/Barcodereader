package com.example.multibarcode

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX [ImageAnalysis.Analyzer] that feeds every frame to ML Kit and reports **all** codes found
 * in that frame at once. ML Kit naturally returns every barcode/QR it can decode in a single image,
 * which is exactly what we need for scanning ~10 products simultaneously.
 *
 * @param onResult invoked on the analysis thread with the full set of codes for the latest frame.
 */
class BarcodeAnalyzer(
    private val onResult: (BarcodeFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            // Standard retail product codes + QR only (EAN/UPC are checksum-validated),
            // so invalid or partial reads are rejected.
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_QR_CODE,
            )
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        // ML Kit reports bounding boxes relative to the rotation-corrected image, so the overlay
        // must be told those same (possibly swapped) dimensions.
        val rotated = rotation == 90 || rotation == 270
        val width = if (rotated) imageProxy.height else imageProxy.width
        val height = if (rotated) imageProxy.width else imageProxy.height

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val results = barcodes.mapNotNull { it.toDetected() }
                onResult(BarcodeFrame(results, width, height))
            }
            .addOnFailureListener {
                onResult(BarcodeFrame(emptyList(), width, height))
            }
            .addOnCompleteListener {
                // Always close so CameraX can deliver the next frame.
                imageProxy.close()
            }
    }

    /** Release the underlying detector. Call from [MainActivity.onDestroy]. */
    fun close() {
        scanner.close()
    }

    private fun Barcode.toDetected(): DetectedBarcode? {
        val box = boundingBox ?: return null
        val value = rawValue ?: displayValue ?: return null
        return DetectedBarcode(
            boundingBox = box,
            value = value,
            format = format.toFormatName(),
        )
    }
}

/** Map ML Kit's integer format constants to readable names shown in the UI. */
private fun Int.toFormatName(): String = when (this) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    else -> "UNKNOWN"
}
