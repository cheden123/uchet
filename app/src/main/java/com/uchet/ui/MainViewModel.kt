package com.uchet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.SpoEntity
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Хранит выбор текущей СПО (SharedPreferences) и отдаёт его реактивно
 * всем экранам, зависящим от текущей СПО.
 */
class MainViewModel(private val repo: UchetRepository) : ViewModel() {

    private val _currentSpoId = MutableStateFlow(repo.currentSpoId())
    val currentSpoId: StateFlow<Long?> = _currentSpoId.asStateFlow()

    val currentSpo: StateFlow<SpoEntity?> = _currentSpoId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeSpo(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasCurrentSpo: StateFlow<Boolean> = _currentSpoId.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refresh() {
        _currentSpoId.value = repo.currentSpoId()
    }

    fun setCurrent(id: Long) {
        repo.setCurrentSpoId(id)
        _currentSpoId.value = id
    }

    fun clearCurrent() {
        repo.clearCurrentSpoId()
        _currentSpoId.value = null
    }
}
