package com.uchet.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Формат длины в метрах: убирает незначащие нули (9.63, 9.6, 10). */
fun formatMeters(m: Double): String {
    if (m.isNaN() || m.isInfinite()) return "0"
    return BigDecimal(m)
        .setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

fun formatMetersFull(m: Double): String = formatMeters(m) + " м"

fun formatDate(ts: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))
