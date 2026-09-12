package com.uchet.ui.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.CumulativeLength
import com.uchet.data.model.DiameterPreset
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeasureViewModel(private val repo: UchetRepository) : ViewModel() {

    private val _spoId = MutableStateFlow<Long?>(repo.currentSpoId())
    val spoId: StateFlow<Long?> = _spoId.asStateFlow()

    val activeRun: StateFlow<RunEntity?> = _spoId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeActiveRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeRunId: StateFlow<Long?> = activeRun
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pipes: StateFlow<List<PipeEntity>> = activeRunId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observePipesForRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val crossovers: StateFlow<List<CrossoverEntity>> = activeRunId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeCrossoversForRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val runs: StateFlow<List<RunEntity>> = _spoId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeRuns(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Порядковый номер активного ряда внутри СПО (1-based). */
    val activeRunNumber: StateFlow<Int> = combine(runs, activeRunId) { runs, activeId ->
        runs.indexOfFirst { it.id == activeId }.let { if (it >= 0) it + 1 else 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Количество труб во всех рядах СПО, созданных раньше активного ряда (глобальный сдвиг нумерации). */
    val globalOffset: StateFlow<Int> =
        combine(runs, activeRunId) { runs, activeId ->
            val idx = runs.indexOfFirst { it.id == activeId }
            if (idx <= 0) emptyList() else runs.subList(0, idx).map { it.id }
        }
            .flatMapLatest { earlierRunIds -> flow { emit(repo.countPipesInRuns(earlierRunIds)) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val presets: StateFlow<List<DiameterPreset>> = repo.observePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Нарастающая длина для последней трубы активного ряда (текущая длина колонны). */
    val lastCumulative: StateFlow<CumulativeLength?> =
        combine(_spoId, activeRun, pipes, crossovers) { spoId, run, p, c ->
            Quad(spoId, run, p, c)
        }
            .flatMapLatest { (spoId, run, p, c) ->
                flow {
                    if (spoId != null && run != null && p.isNotEmpty()) {
                        emit(repo.getCumulativeLengthUpTo(spoId, run.id, p.last().indexInRun))
                    } else {
                        emit(null)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Вызывается экраном при смене текущей СПО. */
    fun attach(id: Long?) {
        if (_spoId.value != id) _spoId.value = id
    }

    /**
     * Создать активный ряд, если его нет. Безопасен при параллельных вызовах —
     * внутри репозитория под Mutex с suspend-проверкой к БД.
     */
    fun ensureActiveRun() {
        val id = _spoId.value ?: return
        viewModelScope.launch { repo.ensureActiveRun(id) }
    }

    /** Добавить трубу, длина уже в метрах (результат parseCmInput). */
    fun addPipeMeters(lengthM: Double, diameterLabel: String) {
        val runId = activeRunId.value ?: return
        repo.setLastDiameter(diameterLabel)
        viewModelScope.launch { repo.addPipeToEnd(runId, lengthM, diameterLabel) }
    }

    fun insertPipeAfter(afterIndex: Int, lengthCm: Int, diameterLabel: String) {
        val runId = activeRunId.value ?: return
        repo.setLastDiameter(diameterLabel)
        viewModelScope.launch { repo.insertPipeAt(runId, afterIndex, lengthCm / 100.0, diameterLabel) }
    }

    fun updatePipe(pipe: PipeEntity, lengthCm: Int, diameterLabel: String) {
        repo.setLastDiameter(diameterLabel)
        viewModelScope.launch { repo.updatePipe(pipe.copy(lengthM = lengthCm / 100.0, diameterLabel = diameterLabel)) }
    }

    fun deletePipe(pipe: PipeEntity) {
        viewModelScope.launch { repo.deletePipe(pipe) }
    }

    fun completeActiveRun() {
        val runId = activeRunId.value ?: return
        viewModelScope.launch { repo.completeRunAndStartNext(runId) }
    }

    fun addCrossover(
        afterPipeIndex: Int,
        type: Int,
        customTypeName: String,
        diameterLabel: String,
        lengthCm: Int,
        note: String
    ) {
        val runId = activeRunId.value ?: return
        viewModelScope.launch {
            repo.addCrossover(runId, afterPipeIndex, type, customTypeName, diameterLabel, lengthCm / 100.0, note)
        }
    }

    fun updateCrossover(crossover: CrossoverEntity) {
        viewModelScope.launch { repo.updateCrossover(crossover) }
    }

    fun deleteCrossover(id: Long) {
        viewModelScope.launch { repo.deleteCrossover(id) }
    }

    fun rememberDiameter(label: String) = repo.setLastDiameter(label)

    fun lastDiameterOrBlank(): String = repo.lastDiameter() ?: ""

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
