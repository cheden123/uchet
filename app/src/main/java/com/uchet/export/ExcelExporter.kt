package com.uchet.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.uchet.data.model.ExportData
import com.uchet.data.model.SpoPipeRow
import com.uchet.util.formatDate
import com.uchet.util.formatMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.VerticalAlignment
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
 * Экспорт одной СПО в XLSX по образцу «ОТЧЁТ о состоянии и замере
 * насосно-компрессорных труб» (Лист3 загруженного примера).
 *
 * Один лист-отчёт: шапка (куст/скважина/месторождение, подрядчик, бригада,
 * трубы, СПО с длиной компоновки), двухколоночная таблица труб
 * (по 50 труб в колонке, 100 труб на страницу), нарастающая «Длина колонны»
 * от длины компоновки, строка переводника/оборудования после каждого десятка
 * труб с суммой этих 10 труб в «Примечание», подписи внизу.
 *
 * Поля, отсутствующие в модели данных («Марка стали», «Подрядчик»,
 * «Бригада №», «ФИО мастера»), выводятся пустыми заглушками.
 */
class ExcelExporter(private val context: Context) {

    suspend fun export(data: ExportData): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, buildFileName(data))
        write(data, file)
        file
    }

    fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun buildFileName(data: ExportData): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(data.spo.startDate))
        val title = sanitize(data.spo.title.ifBlank { "СПО" })
        return "${date}_${title}.xlsx"
    }

    private fun sanitize(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").trim('_', '.', ' ')
        return cleaned.ifBlank { "SPO" }
    }

    private fun write(data: ExportData, file: File) {
        XSSFWorkbook().use { wb ->
            val titleStyle = wb.createCellStyle().apply {
                setFont(wb.createFont().apply { bold = true; fontHeightInPoints = 14 })
            }
            val boldStyle = wb.createCellStyle().apply {
                setFont(wb.createFont().apply { bold = true })
            }
            val headerStyle = wb.createCellStyle().apply {
                setFont(wb.createFont().apply { bold = true })
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                wrapText = true
                addBorders(this)
            }
            val textStyle = wb.createCellStyle().apply { addBorders(this) }
            val numStyle = wb.createCellStyle().apply {
                dataFormat = wb.creationHelper.createDataFormat().getFormat("0.00")
                addBorders(this)
            }

            writeReport(wb, data, titleStyle, boldStyle, headerStyle, textStyle, numStyle)

            FileOutputStream(file).use { wb.write(it) }
        }
    }

    private fun addBorders(style: XSSFCellStyle) {
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
    }

    private fun writeReport(
        wb: XSSFWorkbook,
        data: ExportData,
        titleStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle,
        headerStyle: XSSFCellStyle,
        textStyle: XSSFCellStyle,
        numStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Отчёт")
        configureColumns(sheet)

        val bhaTotal = data.bhaComponents.sumOf { it.lengthM }
        val pipes = data.pipes

        // Нарастающая длина «от забоя»: компоновка + трубы.
        // Оборудование/переводники в «Длину колонны» не суммируются (как в примере).
        val cumulative = DoubleArray(pipes.size)
        var acc = bhaTotal
        for (k in pipes.indices) {
            acc += pipes[k].lengthM
            cumulative[k] = acc
        }

        var r = 0

        // --- шапка отчёта -------------------------------------------------
        putText(sheet.createRow(r++), 0, "ОТЧЁТ", titleStyle)
        putText(sheet.createRow(r++), 0, "о состоянии и замере насосно-компрессорных труб")
        r++ // пустая строка
        putText(
            sheet.createRow(r++), 1,
            "Куст №  ${data.spo.wellCluster}   Скважина № ${data.spo.wellNumber}  " +
                "месторождение    ${data.spo.fieldName}"
        )
        val contractorRow = sheet.createRow(r++)
        putText(contractorRow, 1, "Подрядчик  ")
        putText(contractorRow, 5, "Бригада №")
        val pipeInfoRow = sheet.createRow(r++)
        putText(pipeInfoRow, 0, "Труба ${diameterSummary(data)}")
        putText(pipeInfoRow, 6, "${data.spo.title.ifBlank { "СПО" }} L=${formatMeters(bhaTotal)}м.")
        r++ // пустая строка
        writeHeaderRow(sheet, r++, headerStyle)
        r++ // пустая строка

        // Начальное значение «Длины колонны» — длина компоновки.
        val offsetRow = sheet.createRow(r++)
        for (c in 0 until 6) putText(offsetRow, LEFT + c, "", textStyle)
        putNum(offsetRow, LEFT + 4, bhaTotal, numStyle)

        // --- данные: по 100 труб на страницу, две колонки по 50 -------------
        var start = 0
        var firstPage = true
        while (start < pipes.size) {
            if (!firstPage) {
                sheet.createRow(r++) // пустая строка между страницами
                writeHeaderRow(sheet, r++, headerStyle)
            }
            firstPage = false

            val end = minOf(start + PIPES_PER_PAGE, pipes.size)
            val pagePipes = pipes.subList(start, end)
            val leftCount = minOf(PIPES_PER_COLUMN, pagePipes.size)
            val rightCount = maxOf(0, pagePipes.size - leftCount)
            val rows = maxOf(leftCount, rightCount)

            fun sumRow(from: Int, endExclusive: Int) {
                val row = sheet.createRow(r++)
                val leftPipes = (from until minOf(endExclusive, leftCount)).map { pagePipes[it] }
                val rightPipes = (from until minOf(endExclusive, rightCount)).map { pagePipes[leftCount + it] }
                if (leftPipes.isNotEmpty()) {
                    for (c in 0 until 6) putText(row, LEFT + c, "", textStyle)
                    val nos = leftPipes.map { it.globalNumber }
                    val eq = data.equipment.filter { it.afterPipe in nos }
                    if (eq.isNotEmpty()) {
                        putText(row, LEFT + 1, eq.joinToString("; ") { it.diameterLabel }, textStyle)
                    }
                    putNum(row, LEFT + 5, leftPipes.sumOf { it.lengthM }, numStyle)
                }
                if (rightPipes.isNotEmpty()) {
                    for (c in 0 until 6) putText(row, RIGHT + c, "", textStyle)
                    val nos = rightPipes.map { it.globalNumber }
                    val eq = data.equipment.filter { it.afterPipe in nos }
                    if (eq.isNotEmpty()) {
                        putText(row, RIGHT + 1, eq.joinToString("; ") { it.diameterLabel }, textStyle)
                    }
                    putNum(row, RIGHT + 5, rightPipes.sumOf { it.lengthM }, numStyle)
                }
            }

            var groupStart = 0
            for (i in 0 until rows) {
                val row = sheet.createRow(r++)
                if (i < leftCount) {
                    writePipeRow(row, LEFT, pagePipes[i], cumulative[start + i], textStyle, numStyle)
                }
                if (i < rightCount) {
                    writePipeRow(
                        row, RIGHT, pagePipes[leftCount + i],
                        cumulative[start + leftCount + i], textStyle, numStyle
                    )
                }
                if ((i + 1) % GROUP_SIZE == 0) {
                    sumRow(groupStart, i + 1)
                    groupStart = i + 1
                }
            }
            if (groupStart < rows) {
                sumRow(groupStart, rows)
            }

            start = end
        }

        // --- подписи --------------------------------------------------------
        sheet.createRow(r++) // пустая строка
        val signRow = sheet.createRow(r++)
        putText(signRow, 0, "Мастер ТКРС", boldStyle)
        putText(signRow, 6, "Дата: ${formatDate(data.spo.startDate)}г.", boldStyle)
        val signSub = sheet.createRow(r++)
        putText(signSub, 1, "Фамилия,имя,отчество")
        putText(signSub, 5, "Подпись")
    }

    private fun diameterSummary(data: ExportData): String =
        data.diameterStats.joinToString("; ") { it.name }

    private fun writeHeaderRow(sheet: XSSFSheet, rowIndex: Int, style: XSSFCellStyle) {
        val row = sheet.createRow(rowIndex)
        TABLE_HEADERS.forEachIndexed { i, h -> putText(row, LEFT + i, h, style) }
        TABLE_HEADERS.forEachIndexed { i, h -> putText(row, RIGHT + i, h, style) }
    }

    private fun writePipeRow(
        row: XSSFRow,
        base: Int,
        pipe: SpoPipeRow,
        cumulative: Double,
        textStyle: XSSFCellStyle,
        numStyle: XSSFCellStyle
    ) {
        putNum(row, base + 0, pipe.globalNumber.toDouble(), textStyle) // № п/п
        putText(row, base + 1, pipe.diameterLabel, textStyle)
        putText(row, base + 2, "", textStyle) // Марка стали — заглушка
        putNum(row, base + 3, pipe.lengthM, numStyle)
        putNum(row, base + 4, cumulative, numStyle)
        putText(row, base + 5, "", textStyle) // Примечание
    }

    private fun configureColumns(sheet: XSSFSheet) {
        val widths = intArrayOf(6, 18, 8, 12, 12, 11)
        for (i in widths.indices) {
            sheet.setColumnWidth(LEFT + i, widths[i] * 256)
            sheet.setColumnWidth(RIGHT + i, widths[i] * 256)
        }
    }

    private fun putText(row: XSSFRow, col: Int, value: String, style: XSSFCellStyle? = null) {
        val cell = row.createCell(col)
        cell.setCellValue(value)
        if (style != null) cell.cellStyle = style
    }

    private fun putNum(row: XSSFRow, col: Int, value: Double, style: XSSFCellStyle) {
        val cell = row.createCell(col)
        cell.setCellValue(value)
        cell.cellStyle = style
    }

    private companion object {
        const val LEFT = 0
        const val RIGHT = 6
        const val PIPES_PER_COLUMN = 50
        const val PIPES_PER_PAGE = 100
        const val GROUP_SIZE = 10

        val TABLE_HEADERS = listOf(
            "№ п/п",
            "Диаметр/Толщина стенки НКТ",
            "Марка стали",
            "Длина трубы,м",
            "Длина колонны",
            "Примечание"
        )
    }
}
