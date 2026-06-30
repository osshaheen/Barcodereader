package com.example.multibarcode

import android.graphics.Rect

/**
 * Lightweight, UI-facing representation of a single detected code.
 *
 * Keeping the rest of the app decoupled from ML Kit's [com.google.mlkit.vision.barcode.common.Barcode]
 * type makes the overlay and the list adapter trivial to test and reason about.
 *
 * @param boundingBox the code's location, in the coordinate space of the analysed (rotation-corrected)
 *        image — see [BarcodeAnalyzer] for how [imageWidth]/[imageHeight] are derived.
 * @param value       the decoded text (raw value, falling back to display value).
 * @param format      a human readable format name, e.g. "QR_CODE", "EAN_13".
 */
data class DetectedBarcode(
    val boundingBox: Rect,
    val value: String,
    val format: String,
)

/** A frame's worth of results plus the size of the image they were measured against. */
data class BarcodeFrame(
    val barcodes: List<DetectedBarcode>,
    val imageWidth: Int,
    val imageHeight: Int,
)
