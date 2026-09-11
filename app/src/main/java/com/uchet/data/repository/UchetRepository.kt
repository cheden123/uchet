package com.uchet.data.repository

import android.content.SharedPreferences
import androidx.room.withTransaction
import com.uchet.data.db.AppDatabase
import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.CrossoverTypes
import com.uchet.data.model.CumulativeLength
import com.uchet.data.model.DiameterPreset
import com.uchet.data.model.ExportData
import com.uchet.data.model.ExportDiameterRow
import com.uchet.data.model.ExportEquipment
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import com.uchet.data.model.SpoEntity
import com.uchet.data.model.SpoPipeRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UchetRepository(
    private val db: AppDatabase,
    private val prefs: SharedPreferences
) {
    private val spoDao = db.spoDao()
    private val runDao = db.runDao()
    private val pipeDao = db.pipeDao()
    private val crossoverDao = db.crossoverDao()
    private val bhaDao = db.bhaComponentDao()
    private val presetDao = db.diameterPresetDao()

    /** Защита от гонки при создании активного ряда (см. ensureActiveRun). */
    private val runMutex = Mutex()

    // ------------------------------------------------------------------ prefs

    /** Текущая СПО — это выбор пользователя, хранится в SharedPreferences. */
    fun currentSpoId(): Long? =
        prefs.getLong(KEY_CURRENT_SPO, NO_SPO).takeIf { it != NO_SPO }

    fun setCurrentSpoId(id: Long) {
        prefs.edit().putLong(KEY_CURRENT_SPO, id).apply()
    }

    fun clearCurrentSpoId() {
        prefs.edit().remove(KEY_CURRENT_SPO).apply()
    }

    fun lastDiameter(): String? = prefs.getString(KEY_LAST_DIAMETER, null)

    fun setLastDiameter(label: String) {
        prefs.edit().putString(KEY_LAST_DIAMETER, label).apply()
    }

    // ------------------------------------------------------------------ СПО

    fun observeSpos(): Flow<List<SpoEntity>> = spoDao.observeAll()

    fun observeSpo(id: Long): Flow<SpoEntity?> = spoDao.observeById(id)

    suspend fun getSpo(id: Long): SpoEntity? = spoDao.getById(id)

    suspend fun getSpos(): List<SpoEntity> = spoDao.getAll()

    /** Создаёт СПО; если ни одна не выбрана — сразу делает новую текущей. */
    suspend fun createSpo(
        startDate: Long,
        wellCluster: String,
        wellNumber: String,
        fieldName: String,
        title: String
    ): Long {
        val id = spoDao.insert(
            SpoEntity(
                startDate = startDate,
                wellCluster = wellCluster,
                wellNumber = wellNumber,
                fieldName = fieldName,
                title = title
            )
        )
        if (currentSpoId() == null) setCurrentSpoId(id)
        return id
    }

    suspend fun setSpoCompleted(id: Long, completed: Boolean) {
        spoDao.getById(id)?.let { spoDao.update(it.copy(isCompleted = completed)) }
    }

    suspend fun deleteSpo(spo: SpoEntity) {
        if (currentSpoId() == spo.id) clearCurrentSpoId()
        spoDao.delete(spo)
    }

    // ------------------------------------------------------------------ ряды

    /**
     * Создать активный ряд, только если его ещё нет. Проверка к БД выполняется
     * ВНУТРИ одной защищённой Mutex-операции — защита от параллельного вызова
     * из нескольких LaunchedEffect (иначе создалось бы два активных ряда).
     */
    suspend fun ensureActiveRun(spoId: Long): Long = runMutex.withLock {
        val existing = runDao.getActiveRunInSpoSuspend(spoId)
        if (existing != null) return@withLock existing.id
        runDao.insert(RunEntity(spoId = spoId))
    }

    fun observeActiveRun(spoId: Long): Flow<RunEntity?> = runDao.observeActiveRunInSpo(spoId)

    suspend fun getActiveRun(spoId: Long): RunEntity? = runDao.getActiveRunInSpo(spoId)

    fun observeRun(runId: Long): Flow<RunEntity?> = runDao.observeRunById(runId)

    suspend fun getRuns(spoId: Long): List<RunEntity> = runDao.getRunsForSpo(spoId)

    fun observeRuns(spoId: Long): Flow<List<RunEntity>> = runDao.observeRunsForSpo(spoId)

    fun observePipesForSpo(spoId: Long): Flow<List<PipeEntity>> = pipeDao.observePipesForSpo(spoId)

    fun observeCrossoversForSpo(spoId: Long): Flow<List<CrossoverEntity>> =
        crossoverDao.observeCrossoversForSpo(spoId)

    /**
     * Завершает ряд и возвращает id нового активного ряда (создавая его),
     * либо null, если активный ряд не найден (нечего завершать).
     */
    suspend fun completeRunAndStartNext(runId: Long): Long? {
        val run = runDao.getRunById(runId) ?: return null
        if (run.isCompleted) return runDao.getActiveRunInSpo(run.spoId)?.id
        return runMutex.withLock {
            runDao.completeRun(runId)
            runDao.getActiveRunInSpo(run.spoId)?.id
                ?: runDao.insert(RunEntity(spoId = run.spoId))
        }
    }

    // ------------------------------------------------------------------ трубы

    fun observePipesForRun(runId: Long): Flow<List<PipeEntity>> =
        pipeDao.observePipesForRun(runId)

    suspend fun getPipesForRun(runId: Long): List<PipeEntity> =
        pipeDao.getPipesForRun(runId)

    /** Добавить трубу в конец ряда. */
    suspend fun addPipeToEnd(runId: Long, lengthM: Double, diameterLabel: String): Long =
        db.withTransaction {
            val afterIndex = pipeDao.countInRun(runId)
            pipeDao.insert(
                PipeEntity(
                    runId = runId,
                    indexInRun = afterIndex + 1,
                    lengthM = lengthM,
                    diameterLabel = diameterLabel
                )
            )
        }

    /**
     * Вставка трубы ПОСЛЕ трубы с [afterIndex] (0 = в самое начало ряда).
     * Атомарно: сдвиг труб +1, сдвиг переводников +1, вставка новой трубы —
     * чтобы переводники остались привязаны к той же физической трубе.
     */
    suspend fun insertPipeAt(
        runId: Long,
        afterIndex: Int,
        lengthM: Double,
        diameterLabel: String
    ): Long = db.withTransaction {
        pipeDao.shiftIndicesAfter(runId, afterIndex, +1)
        crossoverDao.shiftIndicesAfter(runId, afterIndex, +1)
        pipeDao.insert(
            PipeEntity(
                runId = runId,
                indexInRun = afterIndex + 1,
                lengthM = lengthM,
                diameterLabel = diameterLabel
            )
        )
    }

    suspend fun updatePipe(pipe: PipeEntity) = pipeDao.update(pipe)

    /**
     * Удаление трубы с пересчётом индексов оставшихся. Переводник, стоявший
     * после удаляемой трубы, переезжает на предыдущую трубу (он физически
     * остаётся в том же месте колонны).
     */
    suspend fun deletePipe(pipe: PipeEntity) = db.withTransaction {
        pipeDao.deleteById(pipe.id)
        pipeDao.shiftIndicesAfter(pipe.runId, pipe.indexInRun, -1)
        val anchored = crossoverDao.getAnchoredAt(pipe.runId, pipe.indexInRun)
        for (c in anchored) {
            crossoverDao.update(c.copy(afterPipeIndex = (pipe.indexInRun - 1).coerceAtLeast(0)))
        }
        crossoverDao.shiftIndicesAfter(pipe.runId, pipe.indexInRun, -1)
    }

    // ------------------------------------------------------------------ переводники/оборудование

    fun observeCrossoversForRun(runId: Long): Flow<List<CrossoverEntity>> =
        crossoverDao.observeCrossoversForRun(runId)

    suspend fun getCrossoversForRun(runId: Long): List<CrossoverEntity> =
        crossoverDao.getCrossoversForRun(runId)

    suspend fun addCrossover(
        runId: Long,
        afterPipeIndex: Int,
        type: Int,
        customTypeName: String,
        diameterLabel: String,
        lengthM: Double,
        note: String
    ): Long = crossoverDao.insert(
        CrossoverEntity(
            runId = runId,
            afterPipeIndex = afterPipeIndex,
            type = type,
            customTypeName = customTypeName,
            diameterLabel = diameterLabel,
            lengthM = lengthM,
            note = note
        )
    )

    suspend fun updateCrossover(crossover: CrossoverEntity) = crossoverDao.update(crossover)

    suspend fun deleteCrossover(id: Long) = crossoverDao.deleteById(id)

    // ------------------------------------------------------------------ компоновка (BHA)

    fun observeBha(spoId: Long): Flow<List<BhaComponentEntity>> = bhaDao.observeForSpo(spoId)

    suspend fun getBha(spoId: Long): List<BhaComponentEntity> = bhaDao.getForSpo(spoId)

    suspend fun addBhaComponent(spoId: Long, name: String, lengthM: Double, sizeLabel: String): Long =
        bhaDao.insert(
            BhaComponentEntity(
                spoId = spoId,
                indexInBha = bhaDao.getMaxIndex(spoId) + 1,
                name = name,
                lengthM = lengthM,
                sizeLabel = sizeLabel
            )
        )

    suspend fun deleteBhaComponent(id: Long) = bhaDao.deleteById(id)

    suspend fun getBhaTotal(spoId: Long): Double = bhaDao.getBhaTotal(spoId)

    // ------------------------------------------------------------------ пресеты

    fun observePresets(): Flow<List<DiameterPreset>> = presetDao.observeAll()

    suspend fun getPresets(): List<DiameterPreset> = presetDao.getAll()

    /** Добавление пресета без дублей — с проверкой существующего имени. */
    suspend fun addPreset(name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Пустое название"))
        if (presetDao.countByName(trimmed) > 0) {
            return Result.failure(IllegalArgumentException("Типоразмер «$trimmed» уже существует"))
        }
        presetDao.insert(DiameterPreset(name = trimmed))
        return Result.success(Unit)
    }

    suspend fun deletePreset(preset: DiameterPreset) = presetDao.delete(preset)

    // ------------------------------------------------------------------ нарастающая длина

    /**
     * Формула из ТЗ:
     * withBha = bha_total
     *        + pipes_and_equipment_in_current_run_up_to(pipeIndex)
     *        + pipes_and_equipment_in_all_earlier_runs_of_this_spo.
     */
    suspend fun getCumulativeLengthUpTo(spoId: Long, runId: Long, pipeIndex: Int): CumulativeLength {
        val bhaTotal = bhaDao.getBhaTotal(spoId)
        val runs = runDao.getRunsForSpo(spoId)
        var pipesAndEquipment = 0.0

        for (run in runs) {
            val pipes = pipeDao.getPipesForRun(run.id)
            val crossovers = crossoverDao.getCrossoversForRun(run.id)

            if (run.id == runId) {
                // оборудование, стоящее перед первой трубой этого ряда
                pipesAndEquipment += crossovers.filter { it.afterPipeIndex == 0 }.sumOf { it.lengthM }
                for (p in pipes) {
                    if (p.indexInRun > pipeIndex) break
                    pipesAndEquipment += p.lengthM
                    pipesAndEquipment += crossovers
                        .filter { it.afterPipeIndex == p.indexInRun }
                        .sumOf { it.lengthM }
                }
                break
            } else {
                pipesAndEquipment += pipes.sumOf { it.lengthM }
                pipesAndEquipment += crossovers.sumOf { it.lengthM }
            }
        }
        return CumulativeLength(
            pipesOnly = pipesAndEquipment,
            withBha = pipesAndEquipment + bhaTotal
        )
    }

    // ------------------------------------------------------------------ сквозной список труб СПО

    /**
     * Все трубы СПО в физическом порядке с накопленной длиной у каждой,
     * посчитанной за один проход (не N+1 запросов).
     */
    suspend fun getAllPipesInSpo(spoId: Long): List<SpoPipeRow> =
        buildSpoPipeRows(
            runs = runDao.getRunsForSpo(spoId),
            pipes = pipeDao.getPipesForSpo(spoId),
            crossovers = crossoverDao.getCrossoversForSpo(spoId),
            bhaComponents = bhaDao.getForSpo(spoId)
        )

    // ------------------------------------------------------------------ экспорт

    /** Данные для экспорта одной СПО (все ряды — одна сквозная таблица). */
    suspend fun getExportData(spoId: Long): ExportData {
        val spo = spoDao.getById(spoId) ?: error("СПО не найдена")
        val rows = getAllPipesInSpo(spoId)
        val runs = runDao.getRunsForSpo(spoId)

        // (runId, indexInRun) -> глобальный номер трубы
        val globalByLocal = HashMap<Pair<Long, Int>, Int>(rows.size)
        for (r in rows) globalByLocal[r.runId to r.indexInRun] = r.globalNumber

        val equipment = ArrayList<ExportEquipment>()
        var eqNumber = 0
        for ((order, run) in runs.withIndex()) {
            val runNumber = order + 1
            val cos = crossoverDao.getCrossoversForRun(run.id)
            // оборудование перед первой трубой ряда (afterPipeIndex == 0)
            for (c in cos.filter { it.afterPipeIndex == 0 }.sortedBy { it.id }) {
                eqNumber++
                equipment += toEquipment(eqNumber, runNumber, 0, c)
            }
            val pipes = pipeDao.getPipesForRun(run.id)
            for (p in pipes) {
                for (c in cos.filter { it.afterPipeIndex == p.indexInRun }.sortedBy { it.id }) {
                    eqNumber++
                    equipment += toEquipment(
                        eqNumber,
                        runNumber,
                        globalByLocal[run.id to p.indexInRun] ?: 0,
                        c
                    )
                }
            }
        }

        // статистика по типоразмерам (порядок первого появления, не алфавит)
        val firstAppearance = LinkedHashMap<String, MutableList<SpoPipeRow>>()
        for (r in rows) firstAppearance.getOrPut(r.diameterLabel) { mutableListOf() }.add(r)
        val stats = firstAppearance.map { (label, list) ->
            ExportDiameterRow(name = label, lengthM = list.sumOf { it.lengthM }, count = list.size)
        }

        return ExportData(
            spo = spo,
            pipes = rows,
            equipment = equipment,
            diameterStats = stats,
            bhaComponents = bhaDao.getForSpo(spoId),
            totalPipes = rows.size,
            totalLengthM = rows.sumOf { it.lengthM }
        )
    }

    private fun toEquipment(
        number: Int,
        runNumber: Int,
        afterPipeGlobal: Int,
        c: CrossoverEntity
    ) = ExportEquipment(
        number = number,
        typeName = CrossoverTypes.typeName(c.type, c.customTypeName),
        diameterLabel = c.diameterLabel,
        afterPipe = afterPipeGlobal,
        lengthM = c.lengthM,
        note = c.note.ifBlank { "Ряд $runNumber" }
    )

    companion object {
        private const val KEY_CURRENT_SPO = "current_spo_id"
        private const val KEY_LAST_DIAMETER = "last_diameter"
        private const val NO_SPO = -1L
    }
}
