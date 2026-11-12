package com.uchet.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Формат длины в метрах: округление до 3 знаков (HALF_UP), без незначащих нулей
 * (9.63, 9.6, 10).
 *
 * BigDecimal.valueOf (через Double.toString — кратчайшее десятичное представление),
 * а не BigDecimal(double): конструктор берёт точное двоичное значение, и, например,
 * 1.0005 (на самом деле 1.000499999…) округлялось до 1 вместо 1.001.
 */
fun formatMeters(m: Double): String {
    if (m.isNaN() || m.isInfinite()) return "0"
    return BigDecimal.valueOf(m)
        .setScale(3, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

fun formatMetersFull(m: Double): String = formatMeters(m) + " м"

fun formatDate(ts: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(ts))

/** Длина трубы больше этой считается подозрительной (лишняя цифра?) — нужно подтверждение. */
const val MAX_REASONABLE_PIPE_M = 20.0

/** Результат разбора ввода длины трубы. */
data class PipeLengthInput(val meters: Double) {
    /** true, если длина больше [MAX_REASONABLE_PIPE_M] — показать предупреждение. */
    val isSuspicious: Boolean get() = meters > MAX_REASONABLE_PIPE_M
}

/**
 * Разбор ввода длины трубы: базовые метры + введённые цифры (сантиметры).
 * Обычный режим: baseMeters = 0, ввод в сантиметрах целиком ("963" → 9.63 м).
 * Режим "Все N": baseMeters = 8/9/10, вводятся только сантиметры ("63" → 9.63 м).
 *
 * Возвращает null, если ввод не число (пусто, нецифровые символы, переполнение Int)
 * или длина получается ≤ 0 (например, "0" в обычном режиме — раньше добавлялась
 * труба длиной 0). Длина > 20 м не отвергается, а помечается [PipeLengthInput.isSuspicious].
 * Сантиметры считаются целыми: (base*100 + cm) / 100.0 — без накопления ошибки 9 + 0.63.
 */
fun parseCmInput(digits: String, baseMeters: Int = 0): PipeLengthInput? {
    val value = digits.trim().toIntOrNull() ?: return null
    val totalCm = baseMeters.toLong() * 100 + value
    if (totalCm <= 0) return null
    return PipeLengthInput(totalCm / 100.0)
}
