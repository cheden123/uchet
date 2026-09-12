package com.uchet.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.RunEntity
import com.uchet.data.model.SpoPipeRow
import com.uchet.data.repository.UchetRepository
import com.uchet.data.repository.buildSpoPipeRows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(private val repo: UchetRepository) : ViewModel() {

    private val _spoId = MutableStateFlow<Long?>(repo.currentSpoId())
    val spoId: StateFlow<Long?> = _spoId.asStateFlow()

    fun attach(id: Long?) {
        if (_spoId.value != id) _spoId.value = id
    }

    private val runsFlow = _spoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeRuns(id)
    }
    private val pipesFlow = _spoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observePipesForSpo(id)
    }
    private val crossoversFlow = _spoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeCrossoversForSpo(id)
    }
    private val bhaFlow = _spoId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repo.observeBha(id)
    }

    val rows: StateFlow<List<SpoPipeRow>> =
        combine(runsFlow, pipesFlow, crossoversFlow, bhaFlow) { runs, pipes, cos, bha ->
            buildSpoPipeRows(runs, pipes, cos, bha)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** История ТОЛЬКО завершённых рядов (активный ряд сюда не попадает). */
    val completedRuns: StateFlow<List<RunEntity>> = runsFlow
        .map { it.filter { r -> r.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Суммарная длина оборудования (переводников/шаблонов) всех рядов СПО. */
    val crossoversTotal: StateFlow<Double> = crossoversFlow
        .map { it.sumOf { c -> c.lengthM } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val allRuns: StateFlow<List<RunEntity>> = runsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
