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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Задача 5: копия .db содержит только что записанные данные (WAL перенесён). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DbBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun backup_containsFreshData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "backup-test.db"
        ctx.deleteDatabase(name)
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        try {
            val repo = UchetRepository(db, ctx.getSharedPreferences("backup-test", Context.MODE_PRIVATE))
            val spo = repo.createSpo(0, "1", "2", "Ванкор", "Спуск")
            val run = repo.ensureActiveRun(spo)
            repo.addPipeToEnd(run, 9.63, "НКТ 73")

            val out = DbBackup(repo, ctx.getDatabasePath(name), tmp.root).create()
            assertTrue(out.name.startsWith("uchet_backup_") && out.name.endsWith(".db"))

            // Открываем копию отдельно от Room — без файла -wal рядом.
            SQLiteDatabase.openDatabase(out.path, null, SQLiteDatabase.OPEN_READONLY).use { copy ->
                copy.rawQuery("SELECT COUNT(*), SUM(lengthM) FROM pipes", null).use { c ->
                    c.moveToFirst()
                    assertEquals(1, c.getInt(0))
                    assertEquals(9.63, c.getDouble(1), 1e-9)
                }
            }
        } finally {
            db.close()
            ctx.deleteDatabase(name)
        }
    }
}
