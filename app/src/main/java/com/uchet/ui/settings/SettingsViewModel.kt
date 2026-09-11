package com.uchet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.DiameterPreset
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: UchetRepository) : ViewModel() {

    val presets: StateFlow<List<DiameterPreset>> = repo.observePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun add(name: String) {
        viewModelScope.launch {
            val result = repo.addPreset(name)
            _error.value = result.exceptionOrNull()?.message
        }
    }

    fun delete(preset: DiameterPreset) {
        viewModelScope.launch { repo.deletePreset(preset) }
    }
}
