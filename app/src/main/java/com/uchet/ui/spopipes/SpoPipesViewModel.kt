package com.uchet.ui.spopipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn

class SpoPipesViewModel(private val repo: UchetRepository) : ViewModel() {

    private val _spoId = MutableStateFlow<Long?>(repo.currentSpoId())
    val spoId: StateFlow<Long?> = _spoId.asStateFlow()

    fun attach(id: Long?) {
        if (_spoId.value != id) _spoId.value = id
    }

    val rows: StateFlow<List<SpoPipeRow>> = combine(
        _spoId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeRuns(id) },
        _spoId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observePipesForSpo(id) },
        _spoId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeCrossoversForSpo(id) },
        _spoId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeBha(id) }
    ) { runs, pipes, cos, bha ->
        buildSpoPipeRows(runs, pipes, cos, bha)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
