package com.uchet.data.repository

import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.CrossoverTypes
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import com.uchet.data.model.SpoPipeRow

/**
 * Результат построения сквозной таблицы труб СПО.
 *
 * [rows] — построчные значения (у каждой трубы накопленная длина «по эту трубу
 * включительно», БЕЗ оборудования, стоящего после неё).
 * [totalPipesOnly] / [totalWithBha] — итог по всей СПО: все трубы + всё
 * оборудование (в т.ч. стоящее после последней трубы или в ряду без труб),
 * без компоновки / с компоновкой. Итог брать отсюда, а не из rows.lastOrNull().
 */
data class SpoPipeRowsResult(
    val rows: List<SpoPipeRow>,
    val totalPipesOnly: Double,
    val totalWithBha: Double
) {
    companion object {
        val EMPTY = SpoPipeRowsResult(emptyList(), 0.0, 0.0)
    }
}

/**
 * Чистая функция: строит сквозную таблицу труб СПО за один проход.
 * Используется и репозиторием (для экспорта), и ViewModel'ами (реактивно),
 * чтобы логика накопления длины была в одном месте.
 *
 * Порядок: ряды по createdAt ASC (самый старый ряд — наименьшие номера),
 * внутри ряда — по indexInRun. Нумерация сквозная «от забоя вверх».
 *
 * Оборудование ряда учитывается так:
 *  - afterPipeIndex == 0 — перед первой трубой ряда;
 *  - afterPipeIndex == indexInRun трубы — сразу после неё (в withBha этой трубы
 *    не входит, входит в следующую трубу и в итог);
 *  - afterPipeIndex, не совпадающий ни с одной трубой ряда («висячее»), —
 *    в конце ряда. Так итог совпадает с UchetRepository.getCumulativeLengthUpTo,
 *    который для предыдущих рядов суммирует ВСЁ оборудование ряда.
 */
fun buildSpoPipeRows(
    runs: List<RunEntity>,
    pipes: List<PipeEntity>,
    crossovers: List<CrossoverEntity>,
    bhaComponents: List<BhaComponentEntity>
): SpoPipeRowsResult {
    val bhaTotal = bhaComponents.sumOf { it.lengthM }
    // Порядок труб внутри ряда не полагаем на порядок из запроса — сортируем явно.
    val pipesByRun = pipes.groupBy { it.runId }.mapValues { (_, list) -> list.sortedBy { it.indexInRun } }
    val crossoversByRun = crossovers.groupBy { it.runId }

    val rows = ArrayList<SpoPipeRow>()
    var globalNumber = 0
    var cumulative = 0.0

    for ((order, run) in runs.withIndex()) {
        val runNumber = order + 1
        val runPipes = pipesByRun[run.id].orEmpty()
        val runCrossovers = crossoversByRun[run.id].orEmpty()
        val pipeIndices = runPipes.mapTo(HashSet()) { it.indexInRun }

        cumulative += runCrossovers.filter { it.afterPipeIndex == 0 }.sumOf { it.lengthM }

        for (p in runPipes) {
            globalNumber++
            cumulative += p.lengthM
            val xo = runCrossovers.filter { it.afterPipeIndex == p.indexInRun }
            val xoLabel = xo.joinToString("; ") {
                "${CrossoverTypes.typeName(it.type, it.customTypeName)} (${com.uchet.util.formatMeters(it.lengthM)} м)"
            }
            rows += SpoPipeRow(
                pipeId = p.id,
                runId = run.id,
                runNumber = runNumber,
                indexInRun = p.indexInRun,
                globalNumber = globalNumber,
                lengthM = p.lengthM,
                diameterLabel = p.diameterLabel,
                crossoverLabel = xoLabel,
                pipesOnly = cumulative,
                withBha = cumulative + bhaTotal
            )
            // Оборудование после трубы входит в следующую строку и в итог.
            cumulative += xo.sumOf { it.lengthM }
        }

        // «Висячее» оборудование (не привязано ни к началу, ни к существующей трубе).
        cumulative += runCrossovers
            .filter { it.afterPipeIndex != 0 && it.afterPipeIndex !in pipeIndices }
            .sumOf { it.lengthM }
    }
    return SpoPipeRowsResult(
        rows = rows,
        totalPipesOnly = cumulative,
        totalWithBha = cumulative + bhaTotal
    )
}
