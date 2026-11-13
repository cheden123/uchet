package com.uchet.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.RoomDatabase
import com.uchet.data.db.AppDatabase
import com.uchet.data.db.LegacyDbGuard
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Перенос базы: загрузка файла .db (копии из «Сохранить копию базы» с другого
 * телефона или из старой установки) вместо текущей базы.
 *
 * Порядок в приложении: файл копируется во временный, [inspect] проверяет его,
 * пользователь подтверждает, текущая база сохраняется копией, файл кладётся рядом
 * с базой ([stage]), Room закрывается, [commit] атомарно подменяет базу, приложение
 * перезапускается.
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
     *
     * Кроме версии и таблиц, файл пробно открывается через Room с теми же миграциями,
     * что и в приложении: Room сверяет схему (хэш для версии 8, состав колонок после
     * миграции 7 → 8). Файл с той же версией, но другой схемой иначе прошёл бы проверку
     * и ронял бы приложение при каждом запуске. Пробное открытие доводит временную
     * копию до текущей версии — это нормально, в базу кладётся именно она.
     * @throws InvalidDbException если это не база приложения или версия/схема не подходит.
     */
    fun inspect(context: Context, file: File): Info {
        val info = inspectFile(file)
        verifyWithRoom(context, file)
        return info
    }

    private fun verifyWithRoom(context: Context, file: File) {
        val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, file.absolutePath)
            .addMigrations(*AppDatabase.MIGRATIONS)
            // Без WAL: после закрытия всё лежит в одном файле, -wal рядом не остаётся.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        try {
            // Первое обращение открывает базу: миграции и проверка схемы Room.
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM spo").use { it.moveToFirst() }
        } catch (e: SQLiteException) {
            throw InvalidDbException("Файл базы повреждён: ${e.message}")
        } catch (e: RuntimeException) {
            // Room сообщает о несовпадении схемы через IllegalStateException.
            throw InvalidDbException("Схема базы не подходит к этой версии приложения (файл из другой сборки?)")
        } finally {
            db.close()
        }
    }

    /** Заголовок, версия, таблицы — быстрая проверка без Room. */
    private fun inspectFile(file: File): Info {
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

    /** Файл рядом с базой, куда сначала кладётся загружаемая база. */
    fun stagedFile(dbFile: File) = File(dbFile.path + ".new")

    /**
     * Шаг 1 замены (Room ещё открыт): скопировать [incoming] в ту же папку, что и база,
     * и сверить размер. Если кончится место или процесс убьют — текущая база не тронута.
     */
    fun stage(incoming: File, dbFile: File): File {
        dbFile.parentFile?.mkdirs()
        val staged = stagedFile(dbFile)
        incoming.copyTo(staged, overwrite = true)
        if (staged.length() != incoming.length()) {
            staged.delete()
            throw IOException("База скопирована не полностью")
        }
        return staged
    }

    /**
     * Шаг 2 (Room уже закрыт): удалить -wal/-shm прежней базы (при закрытии Room их
     * содержимое уже записано в базу; чужой журнал нельзя применять к новой базе)
     * и одним атомарным переименованием поставить [staged] на место [dbFile].
     * Сбой до переименования оставляет прежнюю базу целой.
     */
    fun commit(staged: File, dbFile: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) File(dbFile.path + suffix).delete()
        Files.move(
            staged.toPath(), dbFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
        )
    }

    /** Замена целиком: [stage] + [commit]. Room к этому моменту должен быть закрыт. */
    fun replace(incoming: File, dbFile: File) = commit(stage(incoming, dbFile), dbFile)
}
