package com.example.multibarcode.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Helpers to turn a camera frame + barcode box into a small JPEG product thumbnail. */
object ImageCapture {

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Crop around [box] (expanded by [expand] on each side to capture the product, not just the
     * code), scale down to [maxDim], and JPEG-encode. Returns null if the crop is empty.
     */
    fun cropThumbnailJpeg(
        upright: Bitmap,
        box: Rect,
        expand: Float = 0.7f,
        maxDim: Int = 512,
        quality: Int = 80,
    ): ByteArray? {
        val padX = (box.width() * expand).roundToInt()
        val padY = (box.height() * expand).roundToInt()
        val left = (box.left - padX).coerceIn(0, upright.width - 1)
        val top = (box.top - padY).coerceIn(0, upright.height - 1)
        val right = (box.right + padX).coerceIn(left + 1, upright.width)
        val bottom = (box.bottom + padY).coerceIn(top + 1, upright.height)

        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return null

        var cropped = Bitmap.createBitmap(upright, left, top, w, h)

        val longest = max(cropped.width, cropped.height)
        if (longest > maxDim) {
            val scale = maxDim.toFloat() / longest
            cropped = Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * scale).roundToInt(),
                (cropped.height * scale).roundToInt(),
                true,
            )
        }

        return ByteArrayOutputStream().use { out ->
            cropped.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }
}
