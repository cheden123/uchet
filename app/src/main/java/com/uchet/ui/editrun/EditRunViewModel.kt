package com.uchet.ui.editrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.PipeEntity
import com.uchet.data.model.RunEntity
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditRunViewModel(private val repo: UchetRepository) : ViewModel() {

    private val _runId = MutableStateFlow<Long?>(null)
    val runId: StateFlow<Long?> = _runId.asStateFlow()

    fun attach(runId: Long) {
        if (_runId.value != runId) _runId.value = runId
    }

    val run: StateFlow<RunEntity?> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pipes: StateFlow<List<PipeEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observePipesForRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val crossovers: StateFlow<List<CrossoverEntity>> = _runId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeCrossoversForRun(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val presets: StateFlow<List<com.uchet.data.model.DiameterPreset>> = repo.observePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Количество труб во всех рядах СПО, созданных раньше текущего ряда (глобальный сдвиг нумерации). */
    val globalOffset: StateFlow<Int> = run
        .flatMapLatest { r ->
            if (r == null) flowOf(emptyList())
            else repo.observeRuns(r.spoId).map { runs ->
                val idx = runs.indexOfFirst { it.id == r.id }
                if (idx <= 0) emptyList() else runs.subList(0, idx).map { it.id }
            }
        }
        .flatMapLatest { earlierRunIds -> flow { emit(repo.countPipesInRuns(earlierRunIds)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun updatePipe(pipe: PipeEntity, lengthCm: Int, diameterLabel: String) {
        viewModelScope.launch { repo.updatePipe(pipe.copy(lengthM = lengthCm / 100.0, diameterLabel = diameterLabel)) }
    }

    fun deletePipe(pipe: PipeEntity) {
        viewModelScope.launch { repo.deletePipe(pipe) }
    }

    fun insertAfter(afterIndex: Int, lengthCm: Int, diameterLabel: String) {
        val id = _runId.value ?: return
        viewModelScope.launch { repo.insertPipeAt(id, afterIndex, lengthCm / 100.0, diameterLabel) }
    }

    fun addCrossover(
        afterPipeIndex: Int,
        type: Int,
        customTypeName: String,
        diameterLabel: String,
        lengthCm: Int,
        note: String
    ) {
        val id = _runId.value ?: return
        viewModelScope.launch {
            repo.addCrossover(id, afterPipeIndex, type, customTypeName, diameterLabel, lengthCm / 100.0, note)
        }
    }

    fun updateCrossover(crossover: CrossoverEntity) {
        viewModelScope.launch { repo.updateCrossover(crossover) }
    }

    fun deleteCrossover(crossoverId: Long) {
        viewModelScope.launch { repo.deleteCrossover(crossoverId) }
    }
}
