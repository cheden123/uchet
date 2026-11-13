package com.uchet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uchet.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Активный ряд: несуществующая СПО не роняет приложение (после загрузки другой базы),
 * труба не теряется и не уходит в завершённый ряд, двойное «Завершить ряд» безопасно.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ActiveRunSafetyTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: UchetRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = UchetRepository(db, ctx.getSharedPreferences("active-run-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun missingSpo_noCrashNothingCreated() = runBlocking {
        // Раньше здесь было падение на внешнем ключе при каждом запуске.
        assertNull(repo.ensureActiveRun(777))
        assertFalse(repo.addPipeToActiveRun(777, 9.5, "НКТ 73"))
        assertNull(repo.addBhaComponent(777, "Перо", 1.2, ""))
        assertTrue(repo.getRuns(777).isEmpty())
    }

    @Test
    fun pipeAfterCompleteRun_goesToNewRun() = runBlocking {
        val spo = repo.createSpo(0, "1", "2", "Поле", "СПО")
        assertTrue(repo.addPipeToActiveRun(spo, 9.5, "НКТ 73"))
        val first = repo.ensureActiveRun(spo)!!
        val second = repo.completeRunAndStartNext(first)!!
        // Экран ещё мог помнить первый ряд — репозиторий всё равно кладёт трубу в активный.
        assertTrue(repo.addPipeToActiveRun(spo, 9.6, "НКТ 73"))
        assertEquals(listOf(9.5), repo.getPipesForRun(first).map { it.lengthM })
        assertEquals(listOf(9.6), repo.getPipesForRun(second).map { it.lengthM })
    }

    @Test
    fun doubleCompleteRun_doesNotCompleteEmptyNewRun() = runBlocking {
        val spo = repo.createSpo(0, "1", "2", "Поле", "СПО")
        repo.addPipeToActiveRun(spo, 9.5, "НКТ 73")
        val first = repo.ensureActiveRun(spo)!!
        val second = repo.completeRunAndStartNext(first)!!
        // Второе нажатие пришло уже с новым (пустым) рядом.
        assertEquals(second, repo.completeRunAndStartNext(second))
        assertEquals(2, repo.getRuns(spo).size)
        assertEquals(second, repo.ensureActiveRun(spo))
    }
}
