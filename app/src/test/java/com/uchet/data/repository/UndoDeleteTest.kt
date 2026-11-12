package com.uchet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uchet.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Задача 5: отмена удаления трубы и СПО возвращает данные в исходное состояние. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UndoDeleteTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: UchetRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = UchetRepository(db, ctx.getSharedPreferences("undo-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    }

    @After
    fun tearDown() = db.close()

    /** Состояние ряда: (indexInRun, длина) труб и (afterPipeIndex, длина) оборудования. */
    private suspend fun snapshot(runId: Long) =
        repo.getPipesForRun(runId).map { it.id to (it.indexInRun to it.lengthM) } to
            repo.getCrossoversForRun(runId).map { it.id to it.afterPipeIndex }.sortedBy { it.first }

    @Test
    fun deleteMiddlePipe_thenRestore_restoresIndicesAndEquipment() = runBlocking {
        val spo = repo.createSpo(0, "1", "2", "Ванкор", "Спуск")
        val run = repo.ensureActiveRun(spo)
        repo.addPipeToEnd(run, 9.5, "НКТ 73")
        repo.addPipeToEnd(run, 9.6, "НКТ 73")
        repo.addPipeToEnd(run, 9.7, "НКТ 73")
        repo.addCrossover(run, 0, 0, "", "", 0.2, "") // перед первой трубой
        repo.addCrossover(run, 1, 0, "", "", 0.3, "") // после трубы 1
        repo.addCrossover(run, 2, 0, "", "", 0.4, "") // после удаляемой трубы 2
        repo.addCrossover(run, 3, 0, "", "", 0.5, "") // после трубы 3
        val before = snapshot(run)

        val pipe2 = repo.getPipesForRun(run).first { it.indexInRun == 2 }
        val deleted = repo.deletePipe(pipe2)
        assertEquals(2, repo.getPipesForRun(run).size)

        repo.restorePipe(deleted)
        assertEquals(before, snapshot(run))
    }

    @Test
    fun deleteFirstPipe_thenRestore_keepsEquipmentBeforeFirstPipe() = runBlocking {
        val spo = repo.createSpo(0, "1", "2", "Ванкор", "Спуск")
        val run = repo.ensureActiveRun(spo)
        repo.addPipeToEnd(run, 9.5, "НКТ 73")
        repo.addPipeToEnd(run, 9.6, "НКТ 73")
        repo.addCrossover(run, 0, 0, "", "", 0.2, "")
        repo.addCrossover(run, 1, 0, "", "", 0.3, "")
        val before = snapshot(run)

        val deleted = repo.deletePipe(repo.getPipesForRun(run).first { it.indexInRun == 1 })
        repo.restorePipe(deleted)
        assertEquals(before, snapshot(run))
    }

    @Test
    fun deleteCurrentSpo_thenRestore_restoresAllDataAndSelection() = runBlocking {
        val spo = repo.createSpo(0, "1", "2", "Ванкор", "Спуск")
        assertEquals(spo, repo.currentSpoId())
        val run = repo.ensureActiveRun(spo)
        repo.addPipeToEnd(run, 9.5, "НКТ 73")
        repo.addCrossover(run, 1, 0, "", "", 0.3, "")
        repo.addBhaComponent(spo, "Перо", 1.2, "")
        val totalBefore = repo.getAllPipesInSpo(spo).totalWithBha

        val deleted = repo.deleteSpo(repo.getSpo(spo)!!)
        assertNull(repo.getSpo(spo))
        assertNull(repo.currentSpoId())

        repo.restoreSpo(deleted)
        assertEquals(spo, repo.currentSpoId())
        assertEquals(1, repo.getRuns(spo).size)
        assertEquals(1, repo.getPipesForRun(run).size)
        assertEquals(1, repo.getCrossoversForRun(run).size)
        assertEquals(1, repo.getBha(spo).size)
        assertEquals(totalBefore, repo.getAllPipesInSpo(spo).totalWithBha, 1e-9)
    }
}
