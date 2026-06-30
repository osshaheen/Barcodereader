package com.example.multibarcode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Draws a coloured box and a value label over every detected barcode.
 *
 * The detector reports bounding boxes in the coordinate space of the analysed image, which is
 * usually a different size and aspect ratio than this view. [PreviewView] is configured with the
 * default `FILL_CENTER` scale type, so we reproduce the same centre-crop transform here: scale by
 * the larger axis ratio and centre the result. This keeps the boxes glued to the codes on screen.
 */
class BarcodeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val lock = Any()
    private var barcodes: List<DetectedBarcode> = emptyList()
    private var imageWidth = 0
    private var imageHeight = 0

    private val boxStroke = Paint().apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val boxFill = Paint().apply {
        color = Color.parseColor("#3300E676")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelBg = Paint().apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelText = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val indexText = Paint().apply {
        color = Color.BLACK
        textSize = 34f
        isAntiAlias = true
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val indexBg = Paint().apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /** Replace the currently drawn results. Safe to call from the camera analysis thread. */
    fun setResults(frame: BarcodeFrame) {
        synchronized(lock) {
            barcodes = frame.barcodes
            imageWidth = frame.imageWidth
            imageHeight = frame.imageHeight
        }
        postInvalidate()
    }

    /** Clear everything (e.g. when the camera stops). */
    fun clear() {
        synchronized(lock) {
            barcodes = emptyList()
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val codes: List<DetectedBarcode>
        val imgW: Int
        val imgH: Int
        synchronized(lock) {
            codes = barcodes
            imgW = imageWidth
            imgH = imageHeight
        }
        if (codes.isEmpty() || imgW <= 0 || imgH <= 0) return

        // Reproduce PreviewView's FILL_CENTER mapping from image space to view space.
        val scale = max(width.toFloat() / imgW, height.toFloat() / imgH)
        val dx = (width - imgW * scale) / 2f
        val dy = (height - imgH * scale) / 2f

        codes.forEachIndexed { i, code ->
            val b = code.boundingBox
            val rect = RectF(
                b.left * scale + dx,
                b.top * scale + dy,
                b.right * scale + dx,
                b.bottom * scale + dy,
            )

            canvas.drawRect(rect, boxFill)
            canvas.drawRect(rect, boxStroke)

            drawIndexBadge(canvas, rect, i + 1)
            drawLabel(canvas, rect, code.value)
        }
    }

    private fun drawIndexBadge(canvas: Canvas, rect: RectF, number: Int) {
        val radius = 26f
        val cx = rect.left + radius
        val cy = rect.top + radius
        canvas.drawCircle(cx, cy, radius, indexBg)
        // Vertically centre the digits inside the circle.
        val fm = indexText.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(number.toString(), cx, textY, indexText)
    }

    private fun drawLabel(canvas: Canvas, rect: RectF, rawValue: String) {
        val value = rawValue.ifBlank { "—" }
        val maxWidth = (width * 0.7f)
        val text = ellipsize(value, maxWidth)

        val pad = 12f
        val textWidth = labelText.measureText(text)
        val fm = labelText.fontMetrics
        val textHeight = fm.descent - fm.ascent

        // Prefer placing the label above the box; drop below if there is no room.
        val bgBottom: Float
        val bgTop: Float
        if (rect.top - textHeight - pad * 2 > 0) {
            bgBottom = rect.top
            bgTop = rect.top - textHeight - pad * 2
        } else {
            bgTop = rect.bottom
            bgBottom = rect.bottom + textHeight + pad * 2
        }

        var bgLeft = rect.left
        var bgRight = bgLeft + textWidth + pad * 2
        if (bgRight > width) {
            bgRight = width.toFloat()
            bgLeft = bgRight - (textWidth + pad * 2)
        }
        if (bgLeft < 0f) bgLeft = 0f

        canvas.drawRect(bgLeft, bgTop, bgRight, bgBottom, labelBg)
        val baseline = bgTop + pad - fm.ascent
        canvas.drawText(text, bgLeft + pad, baseline, labelText)
    }

    private fun ellipsize(text: String, maxWidth: Float): String {
        if (labelText.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && labelText.measureText(text.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }
}
