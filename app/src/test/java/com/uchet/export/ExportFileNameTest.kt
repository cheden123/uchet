package com.uchet.export

import com.uchet.data.model.SpoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Задача 3: одна СПО = один файл, имена файлов разных СПО не совпадают. */
class ExportFileNameTest {

    private val day: Long = Calendar.getInstance().apply {
        clear(); set(2026, Calendar.NOVEMBER, 12, 10, 0)
    }.timeInMillis

    @Test
    fun sameDateAndTitle_differentSpo_differentFiles() {
        val a = SpoEntity(id = 1, startDate = day, wellNumber = "345", title = "Спуск пера")
        val b = SpoEntity(id = 2, startDate = day, wellNumber = "345", title = "Спуск пера")
        assertNotEquals(ExcelExporter.exportFileName(a), ExcelExporter.exportFileName(b))
    }

    @Test
    fun name_containsDateWellTitleId_andIsSafe() {
        val spo = SpoEntity(id = 7, startDate = day, wellNumber = "12/3", title = "Спуск: пакера?")
        val name = ExcelExporter.exportFileName(spo)
        assertEquals("2026-11-12_скв12_3_Спуск_пакера_id7.xlsx", name)
        assertTrue(name.none { it in "\\/:*?\"<>| " })
    }
}
