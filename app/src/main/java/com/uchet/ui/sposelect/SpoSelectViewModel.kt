package com.uchet.ui.sposelect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.SpoEntity
import com.uchet.data.repository.UchetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SpoSelectViewModel(private val repo: UchetRepository) : ViewModel() {

    val spos: StateFlow<List<SpoEntity>> = repo.observeSpos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun create(
        startDate: Long,
        wellCluster: String,
        wellNumber: String,
        fieldName: String,
        title: String
    ): Long = repo.createSpo(startDate, wellCluster, wellNumber, fieldName, title)

    suspend fun delete(spo: SpoEntity) = repo.deleteSpo(spo)

    suspend fun setCompleted(id: Long, completed: Boolean) = repo.setSpoCompleted(id, completed)

    suspend fun exportData(spoId: Long) = repo.getExportData(spoId)
}
