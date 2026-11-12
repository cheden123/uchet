package com.uchet.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uchet.data.db.AppDatabase
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Перенос базы: копия с «телефона А» загружается на «телефон Б» и открывается Room. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DbImportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun room(name: String) = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @Test
    fun backupFromOnePhone_importedIntoAnother_keepsData() = runBlocking {
        // «Телефон А»: две СПО, три трубы, резервная копия.
        val nameA = "import-a.db"
        ctx.deleteDatabase(nameA)
        val dbA = room(nameA)
        val backup = try {
            val repo = UchetRepository(dbA, ctx.getSharedPreferences("import-a", Context.MODE_PRIVATE))
            val s1 = repo.createSpo(0, "1", "2", "Ванкор", "Спуск")
            repo.createSpo(0, "3", "4", "Ванкор", "Подъём")
            val run = repo.ensureActiveRun(s1)
            repo.addPipeToEnd(run, 9.63, "НКТ 73")
            repo.addPipeToEnd(run, 9.71, "НКТ 73")
            repo.addPipeToEnd(run, 10.02, "НКТ 73")
            DbBackup(repo, ctx.getDatabasePath(nameA), tmp.newFolder("out")).create()
        } finally {
            dbA.close()
            ctx.deleteDatabase(nameA)
        }

        val info = DbImport.inspect(backup)
        assertEquals(AppDatabase.VERSION, info.version)
        assertEquals(2, info.spoCount)
        assertEquals(3, info.pipeCount)

        // «Телефон Б»: своя база (одна СПО) заменяется загруженной.
        val nameB = "import-b.db"
        ctx.deleteDatabase(nameB)
        val fileB = ctx.getDatabasePath(nameB)
        room(nameB).apply {
            UchetRepository(this, ctx.getSharedPreferences("import-b", Context.MODE_PRIVATE))
                .createSpo(0, "9", "9", "Другое", "Чужая СПО")
            close()
        }
        DbImport.replace(backup, fileB)

        val dbB = room(nameB)
        try {
            dbB.openHelper.readableDatabase.query("SELECT COUNT(*), SUM(lengthM) FROM pipes").use { c ->
                c.moveToFirst()
                assertEquals(3, c.getInt(0))
                assertEquals(29.36, c.getDouble(1), 1e-9)
            }
            dbB.openHelper.readableDatabase.query("SELECT COUNT(*) FROM spo WHERE title = 'Чужая СПО'").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        } finally {
            dbB.close()
            ctx.deleteDatabase(nameB)
        }
    }

    private fun assertRejected(file: File, messagePart: String) {
        try {
            DbImport.inspect(file)
            fail("Файл ${file.name} должен быть отвергнут")
        } catch (e: DbImport.InvalidDbException) {
            assertTrue("«${e.message}» не содержит «$messagePart»", e.message!!.contains(messagePart))
        }
    }

    @Test
    fun inspect_rejectsForeignFiles() {
        val text = tmp.newFile("notes.db").apply { writeText("просто текст, не база") }
        assertRejected(text, "не файл базы")

        val foreign = File(tmp.root, "foreign.db")
        SQLiteDatabase.openOrCreateDatabase(foreign, null).use {
            it.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY)")
            it.version = 8
        }
        assertRejected(foreign, "другого приложения")

        val legacy = File(tmp.root, "legacy.db")
        SQLiteDatabase.openOrCreateDatabase(legacy, null).use { it.version = 6 }
        assertRejected(legacy, "старой версии 6")

        val newer = File(tmp.root, "newer.db")
        SQLiteDatabase.openOrCreateDatabase(newer, null).use { it.version = AppDatabase.VERSION + 1 }
        assertRejected(newer, "более новой версии")
    }
}
