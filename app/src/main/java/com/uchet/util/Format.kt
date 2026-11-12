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

/**
 * Трубы длиннее 15 м не бывают: большая длина — почти наверняка лишняя цифра,
 * нужно подтверждение. На этом же пределе построено автодобавление ([isCompleteCmInput]).
 */
const val MAX_REASONABLE_PIPE_M = 15.0

/** Допустимые значения «Все N» (целые метры): N.xx не длиннее 15 м. */
val ALL_SAME_BASE_RANGE = 1..14

/**
 * Ввод в обычном режиме (сантиметры целиком) уже однозначен — трубу можно добавить
 * сразу, без галочки. Поскольку труба не длиннее 15 м:
 * - 3 цифры, первая 2–9: "638" → 6.38 м, четвёртой цифры быть не может (63.8x м);
 * - 4 цифры: "1063" → 10.63 м (больше 15 м — сработает подтверждение).
 * 3 цифры с первой «1» ("105") — неоднозначно: 1.05 м или начало 10.5x м, ждём
 * четвёртую цифру или галочку. Ведущий 0 тоже ждёт галочку.
 */
fun isCompleteCmInput(digits: String): Boolean = when (digits.length) {
    3 -> digits[0] in '2'..'9'
    4 -> digits[0] != '0'
    else -> false
}

/** Результат разбора ввода длины трубы. */
data class PipeLengthInput(val meters: Double) {
    /** true, если длина больше [MAX_REASONABLE_PIPE_M] — показать предупреждение. */
    val isSuspicious: Boolean get() = meters > MAX_REASONABLE_PIPE_M
}

/**
 * Разбор ввода длины трубы: базовые метры + введённые цифры (сантиметры).
 * Обычный режим: baseMeters = 0, ввод в сантиметрах целиком ("963" → 9.63 м).
 * Режим "Все N": baseMeters = N из [ALL_SAME_BASE_RANGE], вводятся только сантиметры ("63" → 9.63 м).
 *
 * Возвращает null, если ввод не число (пусто, нецифровые символы, переполнение Int)
 * или длина получается ≤ 0 (например, "0" в обычном режиме — раньше добавлялась
 * труба длиной 0). Длина > 15 м не отвергается, а помечается [PipeLengthInput.isSuspicious].
 * Сантиметры считаются целыми: (base*100 + cm) / 100.0 — без накопления ошибки 9 + 0.63.
 */
fun parseCmInput(digits: String, baseMeters: Int = 0): PipeLengthInput? {
    val value = digits.trim().toIntOrNull() ?: return null
    val totalCm = baseMeters.toLong() * 100 + value
    if (totalCm <= 0) return null
    return PipeLengthInput(totalCm / 100.0)
}
