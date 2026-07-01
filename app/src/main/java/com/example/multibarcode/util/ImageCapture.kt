package com.example.multibarcode.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Helpers to turn a camera frame + barcode box into a product thumbnail JPEG. */
object ImageCapture {

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Crop a large region **centered on the barcode** so the thumbnail shows the actual product
     * (the biscuit, the cola bottle, …), not just the code. The region is the larger of a big
     * multiple of the code's size or ~65% of the frame's shorter side, then scaled to [maxDim].
     */
    fun cropProductThumbnailJpeg(
        upright: Bitmap,
        box: Rect,
        maxDim: Int = 640,
        quality: Int = 82,
    ): ByteArray? {
        val w = upright.width
        val h = upright.height
        val shorterSide = min(w, h)
        val boxMax = max(box.width(), box.height())

        // Region side: big enough to include the product around the code.
        val side = max((boxMax * 4f).roundToInt(), (shorterSide * 0.65f).roundToInt())
            .coerceAtMost(min(w, h))

        val cx = box.centerX()
        val cy = box.centerY()
        var left = cx - side / 2
        var top = cy - side / 2
        // Keep the region inside the bitmap.
        left = left.coerceIn(0, w - side)
        top = top.coerceIn(0, h - side)
        val right = (left + side).coerceAtMost(w)
        val bottom = (top + side).coerceAtMost(h)
        val cw = right - left
        val ch = bottom - top
        if (cw <= 0 || ch <= 0) return null

        var cropped = Bitmap.createBitmap(upright, left, top, cw, ch)

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
