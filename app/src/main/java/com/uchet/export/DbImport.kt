package com.uchet.export

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.uchet.data.db.AppDatabase
import com.uchet.data.db.LegacyDbGuard
import java.io.File
import java.io.IOException

/**
 * Перенос базы: загрузка файла .db (копии из «Сохранить копию базы» с другого
 * телефона или из старой установки) вместо текущей базы.
 *
 * Порядок в приложении: файл копируется во временный, [inspect] проверяет его,
 * пользователь подтверждает, текущая база сохраняется копией, Room закрывается,
 * [replace] подменяет файлы, приложение перезапускается — при открытии Room сам
 * выполнит миграции (7 → 8).
 */
object DbImport {

    /** Неподходящий файл — сообщение показывается пользователю как есть. */
    class InvalidDbException(message: String) : IOException(message)

    /** Что лежит в файле — для подтверждения перед заменой. */
    data class Info(val version: Int, val spoCount: Int, val pipeCount: Int)

    private val REQUIRED_TABLES = setOf("spo", "runs", "pipes", "crossovers", "bha_components")
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Проверить файл. Открывается на запись (это наша временная копия): так SQLite
     * спокойно читает базу в режиме WAL без файлов -wal/-shm рядом.
     * @throws InvalidDbException если это не база приложения или версия не подходит.
     */
    fun inspect(file: File): Info {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        if (read != header.size || !header.contentEquals(SQLITE_HEADER)) {
            throw InvalidDbException("Это не файл базы данных (.db)")
        }
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val version = db.version
                when {
                    version in LegacyDbGuard.LEGACY_VERSIONS -> throw InvalidDbException(
                        "База старой версии $version: перенос из неё пока не поддерживается"
                    )
                    version > AppDatabase.VERSION -> throw InvalidDbException(
                        "База из более новой версии приложения ($version) — сначала обновите приложение"
                    )
                    version < 1 -> throw InvalidDbException("Пустая база без данных приложения")
                }
                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                val missing = REQUIRED_TABLES - tables
                if (missing.isNotEmpty()) {
                    throw InvalidDbException("Это база другого приложения (нет таблиц: ${missing.joinToString()})")
                }
                return Info(version, count(db, "spo"), count(db, "pipes"))
            }
        } catch (e: SQLiteException) {
            throw InvalidDbException("Файл базы повреждён: ${e.message}")
        }
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getInt(0) }

    /**
     * Заменить [dbFile] файлом [incoming]. Room к этому моменту должен быть закрыт.
     * Старые -wal/-shm удаляются: иначе SQLite применил бы чужой журнал к новой базе.
     */
    fun replace(incoming: File, dbFile: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) File(dbFile.path + suffix).delete()
        dbFile.parentFile?.mkdirs()
        incoming.copyTo(dbFile, overwrite = true)
        if (dbFile.length() != incoming.length()) throw IOException("База скопирована не полностью")
    }
}
