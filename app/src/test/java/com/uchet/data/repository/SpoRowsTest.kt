package com.uchet.data.repository

import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Регрессия задачи 1: итог «Статистики» (buildSpoPipeRows) должен совпадать с
 * «Замером» (UchetRepository.getCumulativeLengthUpTo для последней трубы
 * активного ряда), в т.ч. когда после последней трубы стоит оборудование.
 *
 * Ожидаемые значения посчитаны вручную по формуле getCumulativeLengthUpTo:
 * компоновка + все трубы и оборудование предыдущих рядов + трубы и оборудование
 * текущего ряда по последнюю трубу включительно (с оборудованием после неё).
 */
class SpoRowsTest {

    private val eps = 1e-9
    private val spoId = 1L
    private val bha = listOf(BhaComponentEntity(id = 1, spoId = spoId, indexInBha = 1, name = "Перо", lengthM = 10.0))
    private val run1 = RunEntity(id = 1, spoId = spoId, createdAt = 1)
    private val run2 = RunEntity(id = 2, spoId = spoId, createdAt = 2)

    private fun pipe(id: Long, runId: Long, idx: Int, len: Double) =
        PipeEntity(id = id, runId = runId, indexInRun = idx, lengthM = len, diameterLabel = "НКТ 73")

    private fun xo(id: Long, runId: Long, after: Int, len: Double) =
        CrossoverEntity(id = id, runId = runId, afterPipeIndex = after, lengthM = len)

    @Test
    fun equipmentAfterLastPipe_isIncludedInTotalImmediately() {
        val pipes = listOf(pipe(1, 1, 1, 9.5), pipe(2, 1, 2, 9.6))
        val cos = listOf(xo(1, 1, 2, 0.5)) // переводник после последней трубы

        val r = buildSpoPipeRows(listOf(run1), pipes, cos, bha)

        // «Замер»: 9.5 + 9.6 + 0.5 = 19.6, с компоновкой 29.6.
        assertEquals(19.6, r.totalPipesOnly, eps)
        assertEquals(29.6, r.totalWithBha, eps)
        // Построчные значения не изменились: в строку трубы №2 переводник не входит.
        assertEquals(19.1, r.rows.last().pipesOnly, eps)
        assertEquals(29.1, r.rows.last().withBha, eps)
    }

    @Test
    fun addingNextPipe_growsTotalByPipeLength_withoutDoubleCountingEquipment() {
        val cos = listOf(xo(1, 1, 2, 0.5))
        val before = buildSpoPipeRows(
            listOf(run1), listOf(pipe(1, 1, 1, 9.5), pipe(2, 1, 2, 9.6)), cos, bha
        )
        val after = buildSpoPipeRows(
            listOf(run1), listOf(pipe(1, 1, 1, 9.5), pipe(2, 1, 2, 9.6), pipe(3, 1, 3, 9.7)), cos, bha
        )

        assertEquals(before.totalWithBha + 9.7, after.totalWithBha, eps)
        // Без оборудования после новой последней трубы итог = строке последней трубы.
        assertEquals(after.totalWithBha, after.rows.last().withBha, eps)
        assertEquals(39.3, after.totalWithBha, eps)
    }

    @Test
    fun equipmentInRunWithoutPipes_andAcrossRuns_isCounted() {
        val pipes = listOf(pipe(1, 1, 1, 9.0))
        val cos = listOf(
            xo(1, 1, 1, 0.4), // после последней трубы ряда 1
            xo(2, 2, 0, 0.3)  // в начале пустого ряда 2
        )

        val r = buildSpoPipeRows(listOf(run1, run2), pipes, cos, bha)

        assertEquals(1, r.rows.size)
        assertEquals(9.7, r.totalPipesOnly, eps)
        assertEquals(19.7, r.totalWithBha, eps)
    }

    @Test
    fun emptySpo_totalIsBhaOnly() {
        val r = buildSpoPipeRows(emptyList(), emptyList(), emptyList(), bha)
        assertEquals(0, r.rows.size)
        assertEquals(0.0, r.totalPipesOnly, eps)
        assertEquals(10.0, r.totalWithBha, eps)
    }
}
