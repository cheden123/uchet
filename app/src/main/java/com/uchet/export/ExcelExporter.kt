package com.uchet.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.uchet.data.model.ExportData
import com.uchet.data.model.ExportEquipment
import com.uchet.data.model.SpoEntity
import com.uchet.data.model.SpoPipeRow
import com.uchet.util.formatDate
import com.uchet.util.formatMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.PageMargin
import org.apache.poi.ss.usermodel.PrintSetup
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экспорт одной СПО в XLSX — «ОТЧЁТ о состоянии и замере насосно-компрессорных труб».
 *
 * Вёрстка листа (A4, книжная, подгонка по ширине страницы):
 *  1. Шапка: куст / скважина / месторождение, СПО и дата, подрядчик/бригада,
 *     компоновка с длиной, сводка по типоразмерам, список оборудования
 *     (переводники и т.п. — в шапке, НЕ строкой таблицы), итоги длины.
 *  2. Таблица труб, сгруппированная по типоразмеру (порядок — первое появление
 *     в колонне). У каждой группы своя нумерация труб (№ п/п с 1) и свой
 *     счётчик блоков по 10 труб; после блока — строка суммы блока.
 *     Двухколоночная вёрстка: до 100 труб группы на печатную страницу,
 *     блоки 1–5 — в левой половине, 6–10 — в правой; дальше — разрыв страницы
 *     и повтор заголовка группы.
 *  3. «Длина колонны» у трубы — компоновка + все трубы СПО по эту трубу в
 *     физическом порядке, БЕЗ оборудования (как было в экспорте раньше).
 *     Итог с оборудованием и компоновкой — в шапке (totalWithBha).
 *  4. Оборудование дополнительно указывается в «Примечании» трубы, после
 *     которой оно стоит.
 *
 * Поля, которых нет в модели данных («Марка стали», «Подрядчик», «Бригада №»,
 * «ФИО мастера»), выводятся пустыми заглушками.
 */
class ExcelExporter(private val context: Context) {

    suspend fun export(data: ExportData): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, exportFileName(data.spo))
        writeReport(data, file)
        file
    }

    fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    companion object {
        /**
         * Имя файла одной СПО: дата_скв№_название_idN.xlsx.
         * id СПО гарантирует уникальность: две СПО с одинаковыми датой и названием
         * (раньше имя было только «дата_название») больше не перезаписывают друг друга.
         */
        fun exportFileName(spo: SpoEntity): String {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(spo.startDate))
            val title = sanitize(spo.title.ifBlank { "СПО" })
            val well = sanitize(spo.wellNumber).takeIf { spo.wellNumber.isNotBlank() }
            return buildString {
                append(date)
                if (well != null) append("_скв").append(well)
                append('_').append(title)
                append("_id").append(spo.id)
                append(".xlsx")
            }
        }

        /** Заменяет недопустимые для имени файла символы и пробелы на «_». */
        private fun sanitize(raw: String): String {
            val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_', '.', ' ')
            return cleaned.ifBlank { "SPO" }
        }

        /** Пишет отчёт в [file]. Без Context — вызывается и из юнит-тестов. */
        internal fun writeReport(data: ExportData, file: File) {
            XSSFWorkbook().use { wb ->
                ReportWriter(wb, data).write()
                FileOutputStream(file).use { wb.write(it) }
            }
        }

        /** Колонок в одной половине листа: № п/п, Диаметр, Марка, Длина, Длина колонны, Примечание. */
        internal const val HALF_COLUMNS = 6
        internal const val LEFT = 0
        internal const val RIGHT = HALF_COLUMNS
        internal const val LAST_COL = HALF_COLUMNS * 2 - 1
        internal const val BLOCK_SIZE = 10
        /** Блоков в одной половине страницы (5 × 10 = 50 труб). */
        internal const val BLOCKS_PER_HALF = 5
        internal const val PIPES_PER_PAGE = BLOCK_SIZE * BLOCKS_PER_HALF * 2

        internal val TABLE_HEADERS = listOf(
            "№ п/п",
            "Диаметр/Толщина стенки НКТ",
            "Марка стали",
            "Длина трубы, м",
            "Длина колонны, м",
            "Примечание"
        )

        internal const val NO_LABEL = "Без типоразмера"
    }
}

/** Труба в группе типоразмера: номер внутри группы, длина колонны, примечание. */
private data class GroupPipe(
    val row: SpoPipeRow,
    val numberInGroup: Int,
    val columnLength: Double,
    val note: String
)

private data class PipeGroup(val label: String, val pipes: List<GroupPipe>) {
    val totalLength: Double get() = pipes.sumOf { it.row.lengthM }
}

/** Построение одного листа-отчёта. Состояние — только текущая строка [r]. */
private class ReportWriter(private val wb: XSSFWorkbook, private val data: ExportData) {

    private val sheet: XSSFSheet = wb.createSheet("Отчёт")
    private var r = 0

    // ------------------------------------------------------------------ стили

    private fun font(bold: Boolean = false, size: Short = 10) = wb.createFont().apply {
        this.bold = bold
        fontHeightInPoints = size
    }

    private fun XSSFCellStyle.bordered(): XSSFCellStyle = apply {
        borderTop = BorderStyle.THIN
        borderBottom = BorderStyle.THIN
        borderLeft = BorderStyle.THIN
        borderRight = BorderStyle.THIN
    }

    private val numFormat = wb.creationHelper.createDataFormat().getFormat("0.00")

    private val titleStyle = wb.createCellStyle().apply {
        setFont(font(bold = true, size = 14)); alignment = HorizontalAlignment.CENTER
    }
    private val subtitleStyle = wb.createCellStyle().apply {
        setFont(font(bold = true, size = 11)); alignment = HorizontalAlignment.CENTER
    }
    private val infoStyle = wb.createCellStyle().apply { setFont(font()) }
    private val infoBoldStyle = wb.createCellStyle().apply { setFont(font(bold = true)) }
    private val headerStyle = wb.createCellStyle().apply {
        setFont(font(bold = true, size = 9))
        alignment = HorizontalAlignment.CENTER
        verticalAlignment = VerticalAlignment.CENTER
        wrapText = true
        fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
    }.bordered()
    private val groupStyle = wb.createCellStyle().apply {
        setFont(font(bold = true, size = 11))
        fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
        fillPattern = FillPatternType.SOLID_FOREGROUND
    }.bordered()
    private val textStyle = wb.createCellStyle().apply { setFont(font()) }.bordered()
    private val centerStyle = wb.createCellStyle().apply {
        setFont(font()); alignment = HorizontalAlignment.CENTER
    }.bordered()
    private val numStyle = wb.createCellStyle().apply { setFont(font()); dataFormat = numFormat }.bordered()
    private val noteStyle = wb.createCellStyle().apply { setFont(font(size = 8)) }.bordered()
    private val sumLabelStyle = wb.createCellStyle().apply {
        setFont(font(bold = true, size = 9)); alignment = HorizontalAlignment.RIGHT
    }.bordered()
    private val sumNumStyle = wb.createCellStyle().apply {
        setFont(font(bold = true)); dataFormat = numFormat
    }.bordered()

    // ------------------------------------------------------------------ данные

    private val bhaTotal = data.bhaComponents.sumOf { it.lengthM }

    /** Оборудование, привязанное к трубе (глобальный номер), — для «Примечания». */
    private val equipmentByPipe: Map<Int, List<ExportEquipment>> =
        data.equipment.filter { it.afterPipe > 0 }.groupBy { it.afterPipe }

    private val groups: List<PipeGroup> = buildGroups()

    /** Глобальный номер трубы -> «НКТ 73 №5» (как труба подписана в таблице). */
    private val pipeCaption: Map<Int, String> = groups
        .flatMap { g -> g.pipes.map { it.row.globalNumber to "${g.label} №${it.numberInGroup}" } }
        .toMap()

    private fun buildGroups(): List<PipeGroup> {
        // «Длина колонны» — в физическом порядке по всей СПО (компоновка + трубы).
        var acc = bhaTotal
        val columnLength = HashMap<Long, Double>(data.pipes.size)
        for (p in data.pipes) {
            acc += p.lengthM
            columnLength[p.pipeId] = acc
        }
        // Порядок групп — первое появление типоразмера в колонне.
        val byLabel = LinkedHashMap<String, MutableList<SpoPipeRow>>()
        for (p in data.pipes) byLabel.getOrPut(p.diameterLabel.ifBlank { ExcelExporter.NO_LABEL }) { mutableListOf() } += p
        return byLabel.map { (label, rows) ->
            PipeGroup(
                label = label,
                pipes = rows.mapIndexed { i, row ->
                    GroupPipe(
                        row = row,
                        numberInGroup = i + 1,
                        columnLength = columnLength.getValue(row.pipeId),
                        note = equipmentByPipe[row.globalNumber].orEmpty().joinToString("; ") { equipmentShort(it) }
                    )
                }
            )
        }
    }

    // ------------------------------------------------------------------ лист

    fun write() {
        setupSheet()
        writeHeader()
        for (g in groups) writeGroup(g)
        writeFooter()
    }

    private fun setupSheet() {
        val widths = intArrayOf(5, 14, 7, 9, 10, 16)
        for (i in widths.indices) {
            sheet.setColumnWidth(ExcelExporter.LEFT + i, widths[i] * 256)
            sheet.setColumnWidth(ExcelExporter.RIGHT + i, widths[i] * 256)
        }
        sheet.printSetup.apply {
            paperSize = PrintSetup.A4_PAPERSIZE
            landscape = false
            fitWidth = 1
            fitHeight = 0 // по высоте — сколько потребуется страниц
        }
        sheet.fitToPage = true
        sheet.horizontallyCenter = true
        sheet.setMargin(PageMargin.LEFT, 0.4)
        sheet.setMargin(PageMargin.RIGHT, 0.4)
        sheet.setMargin(PageMargin.TOP, 0.5)
        sheet.setMargin(PageMargin.BOTTOM, 0.5)
    }

    private fun writeHeader() {
        val spo = data.spo
        mergedLine("ОТЧЁТ", titleStyle)
        mergedLine("о состоянии и замере насосно-компрессорных труб", subtitleStyle)
        r++
        mergedLine(
            "Куст № ${spo.wellCluster.ifBlank { "—" }}    " +
                "Скважина № ${spo.wellNumber.ifBlank { "—" }}    " +
                "Месторождение: ${spo.fieldName.ifBlank { "—" }}",
            infoBoldStyle
        )
        mergedLine("СПО: ${spo.title.ifBlank { "СПО" }}    Дата начала: ${formatDate(spo.startDate)}", infoStyle)
        mergedLine("Подрядчик: ____________________    Бригада № ______", infoStyle)

        // Компоновка
        val bha = data.bhaComponents.sortedBy { it.indexInBha }
        mergedLine(
            if (bha.isEmpty()) "Компоновка: нет"
            else "Компоновка L = ${formatMeters(bhaTotal)} м: " +
                bha.joinToString("; ") { c ->
                    listOf(c.name, c.sizeLabel).filter { it.isNotBlank() }.joinToString(" ") +
                        " ${formatMeters(c.lengthM)} м"
                },
            infoStyle
        )

        // Сводка по типоразмерам
        mergedLine(
            "Трубы: " + data.diameterStats.joinToString("; ") {
                "${it.name.ifBlank { ExcelExporter.NO_LABEL }} — ${it.count} шт., ${formatMeters(it.lengthM)} м"
            }.ifBlank { "нет" },
            infoStyle
        )

        // Оборудование — в шапке, по строке на элемент (не строкой таблицы труб).
        if (data.equipment.isEmpty()) {
            mergedLine("Переводники и оборудование: нет", infoStyle)
        } else {
            mergedLine("Переводники и оборудование (${formatMeters(data.equipment.sumOf { it.lengthM })} м):", infoBoldStyle)
            for (e in data.equipment) {
                val where = if (e.afterPipe > 0) {
                    "после трубы ${pipeCaption[e.afterPipe] ?: "№${e.afterPipe}"} (сквозной №${e.afterPipe})"
                } else {
                    "перед первой трубой ряда"
                }
                val note = e.note.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                mergedLine("  ${e.number}. ${equipmentShort(e)}, $where$note", infoStyle)
            }
        }

        // Итоги — из SpoPipeRowsResult (включая оборудование после последней трубы).
        mergedLine(
            "Итого: труб ${data.totalPipes} шт., длина труб ${formatMeters(data.totalLengthM)} м; " +
                "с оборудованием ${formatMeters(data.totalPipesOnly)} м; " +
                "с компоновкой ${formatMeters(data.totalWithBha)} м",
            infoBoldStyle
        )
        r++
    }

    /** Группа одного типоразмера: заголовок, страницы по 100 труб, итог группы. */
    private fun writeGroup(g: PipeGroup) {
        val pages = g.pipes.chunked(ExcelExporter.PIPES_PER_PAGE)
        for ((pageIndex, page) in pages.withIndex()) {
            if (pageIndex > 0) sheet.setRowBreak(r - 1) // следующая страница той же группы
            val cont = if (pageIndex > 0) " (продолжение)" else ""
            mergedLine(
                "${g.label}$cont — всего ${g.pipes.size} шт., ${formatMeters(g.totalLength)} м",
                groupStyle,
                bordered = true
            )
            writeTableHeader()

            val blocks = page.chunked(ExcelExporter.BLOCK_SIZE)
            val leftBlocks = (blocks.size + 1) / 2 // левая половина заполняется первой
            val firstBlockNo = pageIndex * ExcelExporter.BLOCKS_PER_HALF * 2 + 1
            for (bi in 0 until leftBlocks) {
                val left = blocks[bi]
                val right = blocks.getOrNull(leftBlocks + bi)
                val height = maxOf(left.size, right?.size ?: 0)
                for (k in 0 until height) {
                    val row = sheet.createRow(r++)
                    val lp = left.getOrNull(k)
                    if (lp != null) writePipe(row, ExcelExporter.LEFT, lp) else emptyCells(row, ExcelExporter.LEFT)
                    if (right != null) {
                        val rp = right.getOrNull(k)
                        if (rp != null) writePipe(row, ExcelExporter.RIGHT, rp) else emptyCells(row, ExcelExporter.RIGHT)
                    }
                }
                val sumRow = sheet.createRow(r++)
                writeBlockSum(sumRow, ExcelExporter.LEFT, firstBlockNo + bi, left)
                if (right != null) writeBlockSum(sumRow, ExcelExporter.RIGHT, firstBlockNo + leftBlocks + bi, right)
            }
        }
        mergedLine("Итого ${g.label}: ${g.pipes.size} шт., ${formatMeters(g.totalLength)} м", infoBoldStyle)
        r++
    }

    private fun writeTableHeader() {
        val row = sheet.createRow(r++)
        row.heightInPoints = 26f
        ExcelExporter.TABLE_HEADERS.forEachIndexed { i, h ->
            text(row, ExcelExporter.LEFT + i, h, headerStyle)
            text(row, ExcelExporter.RIGHT + i, h, headerStyle)
        }
    }

    private fun writePipe(row: XSSFRow, base: Int, p: GroupPipe) {
        num(row, base + 0, p.numberInGroup.toDouble(), centerStyle)
        text(row, base + 1, p.row.diameterLabel, textStyle)
        text(row, base + 2, "", textStyle) // Марка стали — заглушка
        num(row, base + 3, p.row.lengthM, numStyle)
        num(row, base + 4, p.columnLength, numStyle)
        text(row, base + 5, p.note, noteStyle)
    }

    /** Строка суммы блока из 10 (или меньше в последнем блоке) труб. */
    private fun writeBlockSum(row: XSSFRow, base: Int, blockNo: Int, block: List<GroupPipe>) {
        val from = block.first().numberInGroup
        val to = block.last().numberInGroup
        text(row, base + 0, "Блок $blockNo (№$from–$to), ${block.size} шт.:", sumLabelStyle)
        for (c in 1..2) text(row, base + c, "", sumLabelStyle)
        sheet.addMergedRegion(CellRangeAddress(row.rowNum, row.rowNum, base, base + 2))
        num(row, base + 3, block.sumOf { it.row.lengthM }, sumNumStyle)
        text(row, base + 4, "", textStyle)
        text(row, base + 5, "", textStyle)
    }

    private fun emptyCells(row: XSSFRow, base: Int) {
        for (c in 0 until ExcelExporter.HALF_COLUMNS) text(row, base + c, "", textStyle)
    }

    private fun writeFooter() {
        r++
        val sign = sheet.createRow(r++)
        text(sign, 0, "Мастер ТКРС ______________________", infoBoldStyle)
        text(sign, ExcelExporter.RIGHT, "Дата: ${formatDate(data.spo.startDate)} г.", infoBoldStyle)
        val sub = sheet.createRow(r++)
        text(sub, 1, "Фамилия, имя, отчество", infoStyle)
        text(sub, ExcelExporter.RIGHT, "Подпись ____________", infoStyle)
    }

    // ------------------------------------------------------------------ ячейки

    /** Строка текста, растянутая на всю ширину листа (обе половины). */
    private fun mergedLine(value: String, style: XSSFCellStyle, bordered: Boolean = false) {
        val row = sheet.createRow(r++)
        text(row, 0, value, style)
        if (bordered) for (c in 1..ExcelExporter.LAST_COL) text(row, c, "", style)
        sheet.addMergedRegion(CellRangeAddress(row.rowNum, row.rowNum, 0, ExcelExporter.LAST_COL))
    }

    private fun text(row: XSSFRow, col: Int, value: String, style: XSSFCellStyle) {
        row.createCell(col).apply { setCellValue(value); cellStyle = style }
    }

    private fun num(row: XSSFRow, col: Int, value: Double, style: XSSFCellStyle) {
        row.createCell(col).apply { setCellValue(value); cellStyle = style }
    }

    private fun equipmentShort(e: ExportEquipment): String =
        listOf(e.typeName, e.diameterLabel).filter { it.isNotBlank() }.joinToString(" ") +
            " ${formatMeters(e.lengthM)} м"
}
