package com.uchet.ui.sposelect

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.DeletedSpo
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

    /** Удаляет СПО со всеми данными; снимок — для отмены через [restore]. */
    suspend fun delete(spo: SpoEntity): DeletedSpo = repo.deleteSpo(spo)

    /** false — восстановить не удалось (конфликт id / внешних ключей). */
    suspend fun restore(d: DeletedSpo): Boolean = try {
        repo.restoreSpo(d)
        true
    } catch (e: SQLException) {
        false
    }

    suspend fun setCompleted(id: Long, completed: Boolean) = repo.setSpoCompleted(id, completed)

    suspend fun exportData(spoId: Long) = repo.getExportData(spoId)
}
