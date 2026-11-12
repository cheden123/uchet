package com.uchet.export

import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.model.ExportData
import com.uchet.data.model.ExportDiameterRow
import com.uchet.data.model.ExportEquipment
import com.uchet.data.model.SpoEntity
import com.uchet.data.model.SpoPipeRow
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Задача 4: структура XLSX-отчёта (читаем сгенерированный файл через POI). */
class ExcelReportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val spo = SpoEntity(id = 5, startDate = 0, wellCluster = "12", wellNumber = "345", fieldName = "Ванкор", title = "Спуск пера")
    private val bha = listOf(BhaComponentEntity(id = 1, spoId = 5, indexInBha = 1, name = "Перо", lengthM = 1.5))

    /** count73 труб НКТ 73 по 9.5 м, затем переводник, затем count89 труб НКТ 89 по 9.0 м. */
    private fun data(count73: Int, count89: Int): ExportData {
        val pipes = ArrayList<SpoPipeRow>()
        var n = 0
        repeat(count73) { pipes += row(++n, 9.5, "НКТ 73") }
        repeat(count89) { pipes += row(++n, 9.0, "НКТ 89") }
        val eq = listOf(ExportEquipment(1, "Переводник", "73x89", count73, 0.35, ""))
        val stats = listOf(
            ExportDiameterRow("НКТ 73", count73 * 9.5, count73),
            ExportDiameterRow("НКТ 89", count89 * 9.0, count89)
        ).filter { it.count > 0 }
        val len = pipes.sumOf { it.lengthM }
        return ExportData(
            spo = spo, pipes = pipes, equipment = eq, diameterStats = stats, bhaComponents = bha,
            totalPipes = pipes.size, totalLengthM = len,
            totalPipesOnly = len + 0.35, totalWithBha = len + 0.35 + 1.5
        )
    }

    private fun row(n: Int, len: Double, label: String) = SpoPipeRow(
        pipeId = n.toLong(), runId = 1, runNumber = 1, indexInRun = n, globalNumber = n,
        lengthM = len, diameterLabel = label, crossoverLabel = "", pipesOnly = 0.0, withBha = 0.0
    )

    private fun render(d: ExportData): XSSFSheet {
        val f = tmp.newFile("r.xlsx")
        ExcelExporter.writeReport(d, f)
        return XSSFWorkbook(f).getSheetAt(0)
    }

    private fun XSSFSheet.str(r: Int, c: Int): String =
        getRow(r)?.getCell(c)?.let { if (it.cellType == CellType.STRING) it.stringCellValue else "" } ?: ""

    private fun XSSFSheet.numOrNull(r: Int, c: Int): Double? =
        getRow(r)?.getCell(c)?.takeIf { it.cellType == CellType.NUMERIC }?.numericCellValue

    private fun XSSFSheet.rowsWhere(pred: (Int) -> Boolean) = (0..lastRowNum).filter(pred)

    @Test
    fun header_hasClusterWellField_andEquipment_notAsTableRow() {
        val s = render(data(25, 12))
        val headerText = (0..20).joinToString("\n") { s.str(it, 0) }
        assertTrue(headerText.contains("Куст № 12"))
        assertTrue(headerText.contains("Скважина № 345"))
        assertTrue(headerText.contains("Месторождение: Ванкор"))
        assertTrue(headerText.contains("Переводник 73x89 0.35 м, после трубы НКТ 73 №25"))

        // В колонке «Диаметр» таблицы — только типоразмеры труб, переводника там нет.
        for (r in 0..s.lastRowNum) for (c in listOf(1, 7)) {
            assertFalse("строка $r", s.str(r, c).contains("Переводник"))
        }
        // Переводник отмечен в «Примечании» трубы №25 НКТ 73.
        val pipe25 = s.rowsWhere { s.numOrNull(it, 6) == 25.0 && s.str(it, 7) == "НКТ 73" }.single()
        assertTrue(s.str(pipe25, 11).contains("Переводник"))
    }

    @Test
    fun groups_haveOwnNumbering_andBlocksOf10_inTwoColumns() {
        val s = render(data(25, 12))
        // Заголовки групп
        assertEquals(1, s.rowsWhere { s.str(it, 0).startsWith("НКТ 73 — всего 25 шт.") }.size)
        assertEquals(1, s.rowsWhere { s.str(it, 0).startsWith("НКТ 89 — всего 12 шт.") }.size)

        // НКТ 73: 3 блока -> левая половина 1–20 (блоки 1, 2), правая 21–25 (блок 3).
        val first = s.rowsWhere { s.numOrNull(it, 0) == 1.0 && s.str(it, 1) == "НКТ 73" }.single()
        assertEquals(21.0, s.numOrNull(first, 6)!!, 0.0)
        val block3 = s.rowsWhere { s.str(it, 6).startsWith("Блок 3 (№21–25), 5 шт.") }.single()
        assertEquals(5 * 9.5, s.numOrNull(block3, 9)!!, 1e-9)
        assertTrue(s.str(block3, 0).startsWith("Блок 1 (№1–10), 10 шт."))
        assertEquals(95.0, s.numOrNull(block3, 3)!!, 1e-9)

        // НКТ 89 нумеруется с 1 и имеет свой «Блок 1».
        assertEquals(1, s.rowsWhere { s.numOrNull(it, 0) == 1.0 && s.str(it, 1) == "НКТ 89" }.size)
        assertEquals(2, s.rowsWhere { s.str(it, 0).startsWith("Блок 1 (") }.size)

        // «Длина колонны» у трубы №1 НКТ 89 = компоновка + 25 × 9.5 + 9.0 (без оборудования).
        val first89 = s.rowsWhere { s.numOrNull(it, 0) == 1.0 && s.str(it, 1) == "НКТ 89" }.single()
        assertEquals(1.5 + 25 * 9.5 + 9.0, s.numOrNull(first89, 4)!!, 1e-9)
    }

    @Test
    fun moreThan100PipesInGroup_breaksPage_andRepeatsGroupTitle() {
        val s = render(data(150, 0))
        assertEquals(1, s.rowBreaks.size)
        assertEquals(1, s.rowsWhere { s.str(it, 0).startsWith("НКТ 73 (продолжение)") }.size)
        // Блок 11 — первый на второй странице.
        assertEquals(1, s.rowsWhere { s.str(it, 0).startsWith("Блок 11 (№101–110)") }.size)
    }

    @Test
    fun totals_comeFromTotalWithBha() {
        val s = render(data(3, 0))
        val total = s.rowsWhere { s.str(it, 0).startsWith("Итого: труб 3 шт.") }.single()
        assertTrue(s.str(total, 0).contains("с компоновкой 30.35 м")) // 28.5 + 0.35 + 1.5
    }
}
