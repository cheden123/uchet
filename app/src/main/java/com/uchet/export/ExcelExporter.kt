package com.uchet.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.uchet.data.model.ExportData
import com.uchet.util.formatMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экспорт одной СПО в XLSX. Файл = одна СПО; все ряды внутри объединены
 * в одну сквозную таблицу.
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
            val headerStyle = wb.createCellStyle().apply {
                setFont(wb.createFont().apply { bold = true; color = IndexedColors.WHITE.index })
                fillForegroundColor = IndexedColors.DARK_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
            }
            val titleStyle = wb.createCellStyle().apply {
                setFont(wb.createFont().apply { bold = true; fontHeightInPoints = 14 })
            }
            val boldStyle = wb.createCellStyle().apply {
                setFont(wb.createFont().apply { bold = true })
            }
            val lengthStyle = wb.createCellStyle().apply {
                dataFormat = wb.creationHelper.createDataFormat().getFormat("0.000")
            }

            writePipesSheet(wb, data, headerStyle, titleStyle, boldStyle, lengthStyle)
            writeEquipmentSheet(wb, data, headerStyle, boldStyle, lengthStyle)
            writeDiameterSheet(wb, data, headerStyle, boldStyle, lengthStyle)
            writeBhaSheet(wb, data, headerStyle, boldStyle, lengthStyle)

            FileOutputStream(file).use { wb.write(it) }
        }
    }

    private fun headerRow(row: XSSFRow, headers: List<String>, style: XSSFCellStyle) {
        headers.forEachIndexed { i, h ->
            row.createCell(i).setCellValue(h).also { row.getCell(i).cellStyle = style }
        }
    }

    private fun writePipesSheet(
        wb: XSSFWorkbook,
        data: ExportData,
        headerStyle: XSSFCellStyle,
        titleStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle,
        lengthStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Трубы")
        var r = 0
        fun cell(row: Int, col: Int, value: String, style: XSSFCellStyle? = null) {
            val rr = sheet.getRow(row) ?: sheet.createRow(row)
            rr.createCell(col).setCellValue(value).also { if (style != null) rr.getCell(col).cellStyle = style }
        }

        cell(r++, 0, data.spo.title.ifBlank { "СПО" }, titleStyle)
        cell(r++, 0, "Дата: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(data.spo.startDate))}")
        cell(r++, 0, "Куст № ${data.spo.wellCluster}  |  Скважина № ${data.spo.wellNumber}")
        cell(r++, 0, "Месторождение: ${data.spo.fieldName}")
        cell(r++, 0, "Компоновка (BHA): ${formatMeters(data.bhaComponents.sumOf { it.lengthM })} м")
        r++

        // Оборудование в эту таблицу не смешивается — оно уходит на отдельный лист.
        headerRow(
            sheet.createRow(r),
            listOf("№", "Ряд", "Типоразмер", "Длина, м", "Накоплено без компоновки, м", "Накоплено с компоновкой, м"),
            headerStyle
        )
        r++

        for (row in data.pipes) {
            val rr = sheet.createRow(r)
            rr.createCell(0).setCellValue(row.globalNumber.toDouble())
            rr.createCell(1).setCellValue(row.runNumber.toDouble())
            rr.createCell(2).setCellValue(row.diameterLabel)
            rr.createCell(3).setCellValue(row.lengthM).also { rr.getCell(3).cellStyle = lengthStyle }
            rr.createCell(4).setCellValue(row.pipesOnly).also { rr.getCell(4).cellStyle = lengthStyle }
            rr.createCell(5).setCellValue(row.withBha).also { rr.getCell(5).cellStyle = lengthStyle }
            r++
        }
        cell(r, 0, "Итого: ${data.totalPipes} труб, ${formatMeters(data.totalLengthM)} м", boldStyle)
    }

    private fun writeEquipmentSheet(
        wb: XSSFWorkbook,
        data: ExportData,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle,
        lengthStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Оборудование")
        var r = 0
        headerRow(sheet.createRow(r), listOf("№", "Тип", "Типоразмер", "После трубы", "Длина, м", "Примечание"), headerStyle)
        r++
        for (e in data.equipment) {
            val row = sheet.createRow(r)
            row.createCell(0).setCellValue(e.number.toDouble())
            row.createCell(1).setCellValue(e.typeName)
            row.createCell(2).setCellValue(e.diameterLabel)
            row.createCell(3).setCellValue(if (e.afterPipe == 0) "—" else e.afterPipe.toString())
            row.createCell(4).setCellValue(e.lengthM).also { row.getCell(4).cellStyle = lengthStyle }
            row.createCell(5).setCellValue(e.note)
            r++
        }
        if (data.equipment.isEmpty()) {
            sheet.createRow(r).createCell(0).setCellValue("Оборудование отсутствует")
        }
    }

    private fun writeDiameterSheet(
        wb: XSSFWorkbook,
        data: ExportData,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle,
        lengthStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Типоразмеры")
        var r = 0
        headerRow(sheet.createRow(r), listOf("Наименование", "Длина, м", "Типоразмер"), headerStyle)
        r++
        var total = 0.0
        for (d in data.diameterStats) {
            val row = sheet.createRow(r)
            row.createCell(0).setCellValue(d.name)
            row.createCell(1).setCellValue(d.lengthM).also { row.getCell(1).cellStyle = lengthStyle }
            row.createCell(2).setCellValue(d.name)
            total += d.lengthM
            r++
        }
        val row = sheet.createRow(r)
        row.createCell(0).setCellValue("Итого").also { row.getCell(0).cellStyle = boldStyle }
        row.createCell(1).setCellValue(total).also { row.getCell(1).cellStyle = boldStyle }
    }

    private fun writeBhaSheet(
        wb: XSSFWorkbook,
        data: ExportData,
        headerStyle: XSSFCellStyle,
        boldStyle: XSSFCellStyle,
        lengthStyle: XSSFCellStyle
    ) {
        val sheet = wb.createSheet("Компоновка")
        var r = 0
        headerRow(sheet.createRow(r), listOf("№", "Наименование", "Размер", "Длина, м"), headerStyle)
        r++
        for (c in data.bhaComponents) {
            val row = sheet.createRow(r)
            row.createCell(0).setCellValue((c.indexInBha + 1).toDouble())
            row.createCell(1).setCellValue(c.name)
            row.createCell(2).setCellValue(c.sizeLabel)
            row.createCell(3).setCellValue(c.lengthM).also { row.getCell(3).cellStyle = lengthStyle }
            r++
        }
        if (data.bhaComponents.isEmpty()) {
            sheet.createRow(r).createCell(0).setCellValue("Компоновка не задана")
        } else {
            val row = sheet.createRow(r)
            row.createCell(0).setCellValue("Итого").also { row.getCell(0).cellStyle = boldStyle }
            row.createCell(3).setCellValue(data.bhaComponents.sumOf { it.lengthM }).also { row.getCell(3).cellStyle = boldStyle }
        }
    }
}
