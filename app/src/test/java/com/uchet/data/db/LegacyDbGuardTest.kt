package com.uchet.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Перед стиранием базы версии 1–6 сохраняется её полная копия. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LegacyDbGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    /** Имитация базы старой версии: схема произвольная (настоящая v6 неизвестна). */
    private fun createLegacyDb(file: File, version: Int) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.enableWriteAheadLogging()
            db.execSQL("CREATE TABLE pipes (id INTEGER PRIMARY KEY, lengthM REAL NOT NULL)")
            db.execSQL("INSERT INTO pipes (lengthM) VALUES (9.63), (9.71)")
            db.version = version
        }
    }

    @Test
    fun legacyVersion_isCopiedBeforeRoomWipesIt() {
        val name = "legacy-test.db"
        ctx.deleteDatabase(name)
        val dbFile = ctx.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()
        createLegacyDb(dbFile, version = 6)

        val copy = LegacyDbGuard.preserveIfLegacy(dbFile, tmp.root)
        assertNotNull(copy)
        assertEquals("uchet_v6_", copy!!.name.take(9))

        // Room стирает старую базу (как в приложении) — копия от этого не зависит.
        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .fallbackToDestructiveMigrationFrom(*LegacyDbGuard.LEGACY_VERSIONS)
            .build()
        try {
            room.openHelper.writableDatabase.query("SELECT COUNT(*) FROM pipes").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        } finally {
            room.close()
            ctx.deleteDatabase(name)
        }

        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(6, db.version)
            db.rawQuery("SELECT COUNT(*), SUM(lengthM) FROM pipes", null).use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
                assertEquals(19.34, c.getDouble(1), 1e-9)
            }
        }
    }

    @Test
    fun currentVersion_orMissingFile_isNotCopied() {
        val missing = File(tmp.root, "nope.db")
        assertNull(LegacyDbGuard.preserveIfLegacy(missing, tmp.newFolder("b1")))

        val current = File(tmp.root, "current.db")
        SQLiteDatabase.openOrCreateDatabase(current, null).use { it.version = 8 }
        val out = tmp.newFolder("b2")
        assertNull(LegacyDbGuard.preserveIfLegacy(current, out))
        assertEquals(0, out.listFiles()!!.size)
    }
}
