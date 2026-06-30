package com.example.multibarcode.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Centralised, easy-to-change formatting. Currency is the Israeli shekel (₪). */
object Format {

    const val CURRENCY = "₪"

    /** e.g. 12.5 -> "₪ 12.50", 12.0 -> "₪ 12". */
    fun money(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        val text = if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
        return "$CURRENCY $text"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US)

    fun dateTime(epochMillis: Long): String = dateFormat.format(Date(epochMillis))
}
