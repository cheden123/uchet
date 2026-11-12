package com.uchet.data.db

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Страховка от потери данных при обновлении со старой базы.
 *
 * Для версий из [LEGACY_VERSIONS] миграций нет (схемы 1–6 не сохранились), и Room
 * пересоздаёт базу пустой (`fallbackToDestructiveMigrationFrom`). Поэтому ДО того,
 * как Room откроет файл, проверяем `user_version` и, если база старая, кладём её
 * полную копию в [backupDir]. Потом по копии можно написать настоящую миграцию
 * и перенести данные.
 *
 * Если копию сделать не удалось — бросаем исключение: лучше падение при запуске
 * (данные на месте), чем молчаливое стирание.
 */
object LegacyDbGuard {

    /** Версии, которые Room сотрёт без миграции (совпадает с fallbackToDestructiveMigrationFrom). */
    val LEGACY_VERSIONS = intArrayOf(1, 2, 3, 4, 5, 6)

    const val BACKUP_DIR = "legacy_backup"

    /**
     * @return файл копии, если база старой версии была сохранена; null — если копия не нужна
     * (файла нет, база новая или пустая).
     */
    fun preserveIfLegacy(dbFile: File, backupDir: File): File? {
        if (!dbFile.exists() || dbFile.length() == 0L) return null

        // READWRITE, чтобы checkpoint перенёс всё из -wal в основной файл:
        // тогда копия одного .db содержит все данные.
        val version = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val v = db.version
            if (v in LEGACY_VERSIONS) {
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            }
            v
        }
        if (version !in LEGACY_VERSIONS) return null

        if (!backupDir.exists() && !backupDir.mkdirs()) {
            error("Не удалось создать папку для копии старой базы: $backupDir")
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(backupDir, "uchet_v${version}_$ts.db")
        dbFile.copyTo(out, overwrite = true)
        // -wal после checkpoint обычно пуст, но копируем на всякий случай рядом.
        val wal = File(dbFile.path + "-wal")
        if (wal.exists() && wal.length() > 0L) wal.copyTo(File(out.path + "-wal"), overwrite = true)

        check(out.length() == dbFile.length()) { "Копия старой базы неполная: ${out.path}" }
        return out
    }
}
