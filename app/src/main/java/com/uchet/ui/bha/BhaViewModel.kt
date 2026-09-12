package com.uchet.ui.bha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.BhaComponentEntity
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BhaViewModel(private val repo: UchetRepository) : ViewModel() {

    private val _spoId = MutableStateFlow<Long?>(repo.currentSpoId())
    val spoId: StateFlow<Long?> = _spoId.asStateFlow()

    fun attach(id: Long?) {
        if (_spoId.value != id) _spoId.value = id
    }

    val components: StateFlow<List<BhaComponentEntity>> = _spoId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeBha(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalLength: StateFlow<Double> = components
        .map { it.sumOf { c -> c.lengthM } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun add(name: String, lengthCm: Int, sizeLabel: String) {
        val id = _spoId.value ?: return
        viewModelScope.launch { repo.addBhaComponent(id, name, lengthCm / 100.0, sizeLabel) }
    }

    fun delete(componentId: Long) {
        viewModelScope.launch { repo.deleteBhaComponent(componentId) }
    }
}
