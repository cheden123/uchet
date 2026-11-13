package com.uchet.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Проверка миграции 7 -> 8 с сохранением данных (задача 2).
 *
 * Схема 7 (app/schemas/.../7.json) восстановлена по 8.json минус колонка
 * spo.testField — ровно то, что добавляет MIGRATION_7_8. Настоящий 7.json
 * в репозитории не сохранился.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate7to8_keepsAllData_andAllowsSeveralUnfinishedSpo() {
        // --- база версии 7: две незавершённые СПО с рядами, трубами, оборудованием, компоновкой
        helper.createDatabase(dbName, 7).apply {
            execSQL("INSERT INTO spo (id, startDate, wellCluster, wellNumber, fieldName, title, isCompleted) VALUES (1, 1000, '12', '345', 'Ванкор', 'Спуск пера', 0)")
            execSQL("INSERT INTO spo (id, startDate, wellCluster, wellNumber, fieldName, title, isCompleted) VALUES (2, 2000, '13', '346', 'Ванкор', 'Спуск пакера', 0)")
            execSQL("INSERT INTO runs (id, spoId, createdAt, isCompleted) VALUES (1, 1, 1, 1)")
            execSQL("INSERT INTO runs (id, spoId, createdAt, isCompleted) VALUES (2, 1, 2, 0)")
            execSQL("INSERT INTO runs (id, spoId, createdAt, isCompleted) VALUES (3, 2, 3, 0)")
            execSQL("INSERT INTO pipes (id, runId, indexInRun, lengthM, diameterLabel) VALUES (1, 1, 1, 9.55, 'НКТ 73')")
            execSQL("INSERT INTO pipes (id, runId, indexInRun, lengthM, diameterLabel) VALUES (2, 1, 2, 9.61, 'НКТ 73')")
            execSQL("INSERT INTO pipes (id, runId, indexInRun, lengthM, diameterLabel) VALUES (3, 3, 1, 9.40, 'НКТ 89')")
            execSQL("INSERT INTO crossovers (id, runId, afterPipeIndex, type, customTypeName, diameterLabel, lengthM, note) VALUES (1, 1, 2, 0, '', '73x89', 0.35, '')")
            execSQL("INSERT INTO bha_components (id, spoId, indexInBha, name, lengthM, sizeLabel) VALUES (1, 1, 1, 'Перо', 1.2, '')")
            close()
        }

        // --- миграция + валидация схемы против 8.json
        val db = helper.runMigrationsAndValidate(dbName, 8, true, AppDatabase.MIGRATION_7_8)

        fun count(table: String): Int =
            db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

        assertEquals(2, count("spo"))
        assertEquals(3, count("runs"))
        assertEquals(3, count("pipes"))
        assertEquals(1, count("crossovers"))
        assertEquals(1, count("bha_components"))
        db.query("SELECT testField FROM spo WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("", it.getString(0))
        }
        db.query("SELECT SUM(lengthM) FROM pipes WHERE runId = 1").use {
            it.moveToFirst()
            assertEquals(19.16, it.getDouble(0), 1e-9)
        }
        db.close()

        // --- открываем через Room с теми же миграциями, что в приложении,
        // и проверяем, что обе незавершённые СПО доступны через DAO
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val spos = room.spoDao().getAll()
                assertEquals(2, spos.size)
                assertEquals(2, spos.count { !it.isCompleted })
                // Активный ряд у каждой СПО свой: у СПО 1 ряд 1 завершён -> активный id 2.
                assertEquals(2L, room.runDao().getActiveRunInSpo(1)?.id)
                assertEquals(3L, room.runDao().getActiveRunInSpo(2)?.id)
            }
        } finally {
            room.close()
        }
    }
}
