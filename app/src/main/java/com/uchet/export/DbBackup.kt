package com.uchet.export

import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Резервная копия всей базы: файл uchet.db копируется в [outDir]
 * (в приложении — cacheDir/exports, откуда его отдаёт FileProvider).
 *
 * Перед копированием WAL переносится в основной файл (checkpoint), иначе
 * последние изменения остались бы в uchet.db-wal и в копию не попали.
 * Восстановление из копии в приложении не реализовано (только экспорт).
 */
class DbBackup(
    private val repo: UchetRepository,
    private val dbFile: File,
    private val outDir: File
) {
    /** @throws IOException если БД занята или файл не найден/не скопирован. */
    suspend fun create(now: Date = Date()): File {
        if (!repo.checkpointDatabase()) throw IOException("База занята, повторите через пару секунд")
        return withContext(Dispatchers.IO) {
            if (!dbFile.exists()) throw IOException("Файл базы не найден")
            outDir.mkdirs()
            val name = "uchet_backup_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now) + ".db"
            dbFile.copyTo(File(outDir, name), overwrite = true)
        }
    }
}
