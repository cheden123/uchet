package com.uchet.data.repository

import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.CrossoverTypes
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import com.uchet.data.model.SpoPipeRow

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
): List<SpoPipeRow> {
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
            cumulative += xo.sumOf { it.lengthM }
        }
    }
    return rows
}
