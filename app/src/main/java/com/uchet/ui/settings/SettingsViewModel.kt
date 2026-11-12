package com.uchet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.DiameterPreset
import com.uchet.data.repository.UchetRepository
import com.uchet.data.db.AppDatabase
import com.uchet.export.DbBackup
import com.uchet.export.DbImport
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** Копия всей базы в [outDir]; пути даёт экран (у ViewModel нет Context). */
    suspend fun createBackup(dbFile: File, outDir: File): File = DbBackup(repo, dbFile, outDir).create()

    /**
     * Загрузка базы, шаг 1: скопировать выбранный файл во временный [tmp] и проверить.
     * @throws DbImport.InvalidDbException если файл не подходит (сообщение — для пользователя).
     */
    suspend fun prepareImport(open: () -> InputStream, tmp: File): DbImport.Info = withContext(Dispatchers.IO) {
        tmp.parentFile?.mkdirs()
        open().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        DbImport.inspect(tmp)
    }

    /**
     * Шаг 2 (после подтверждения): сохранить копию текущей базы в [safetyDir],
     * закрыть Room и подменить файл. После этого приложение нужно перезапустить.
     * @return копия прежней базы (null, если базы ещё не было).
     */
    suspend fun importDatabase(tmp: File, dbFile: File, safetyDir: File): File? {
        val safety = if (dbFile.exists()) DbBackup(repo, dbFile, safetyDir).create() else null
        withContext(Dispatchers.IO) {
            AppDatabase.closeInstance()
            DbImport.replace(tmp, dbFile)
            tmp.delete()
        }
        return safety
    }
}
