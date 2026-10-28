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
 * [rows] — построчные значения «по состоянию на эту трубу» (оборудование,
 * стоящее ПОСЛЕ трубы, в её строку не входит). Итоги же считаются по всей
 * колонне, включая оборудование после самой последней трубы — поэтому брать
 * общую длину из `rows.lastOrNull()` нельзя.
 */
data class SpoPipeRowsResult(
    val rows: List<SpoPipeRow>,
    /** Трубы + всё оборудование СПО, без компоновки. */
    val totalPipesOnly: Double,
    /** То же + суммарная длина компоновки (BHA). */
    val totalWithBha: Double
)

/**
 * Чистая функция: строит сквозную таблицу труб СПО за один проход.
 * Используется и репозиторием (для экспорта), и ViewModel'ами (реактивно),
 * чтобы логика накопления длины была в одном месте.
 *
 * Порядок: ряды по createdAt ASC (самый старый ряд — наименьшие номера),
 * внутри ряда — по indexInRun. Нумерация сквозная «от забоя вверх».
 */
fun buildSpoPipeRows(
    runs: List<RunEntity>,
    pipes: List<PipeEntity>,
    crossovers: List<CrossoverEntity>,
    bhaComponents: List<BhaComponentEntity>
): SpoPipeRowsResult {
    val bhaTotal = bhaComponents.sumOf { it.lengthM }
    val pipesByRun = pipes.groupBy { it.runId }
    val crossoversByRun = crossovers.groupBy { it.runId }

    val rows = ArrayList<SpoPipeRow>()
    var globalNumber = 0
    var cumulative = 0.0

    for ((order, run) in runs.withIndex()) {
        val runNumber = order + 1
        val runPipes = pipesByRun[run.id].orEmpty()
        val runCrossovers = crossoversByRun[run.id].orEmpty()

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
            // Оборудование после этой трубы учитывается в накоплении, но в её
            // строку не попадает — оно относится к точке сразу за трубой.
            cumulative += xo.sumOf { it.lengthM }
        }
    }

    // cumulative уже включает хвостовое оборудование после последней трубы,
    // даже если следующей трубы нет — именно это значение и есть итог колонны.
    return SpoPipeRowsResult(
        rows = rows,
        totalPipesOnly = cumulative,
        totalWithBha = cumulative + bhaTotal
    )
}
