package com.uchet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.data.model.DiameterPreset
import com.uchet.data.repository.UchetRepository
import com.uchet.data.db.AppDatabase
import com.uchet.export.DbBackup
import com.uchet.export.DbImport
import android.content.Context
import android.database.SQLException
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

    // ------------------------------------------------------------------ загрузка базы
    // Состояние — здесь, а не на экране: поворот экрана не закрывает диалог «Заменить базу?»
    // и не обрывает загрузку.

    /** Файл проверен и ждёт подтверждения (null — диалога нет). */
    private val _pendingImport = MutableStateFlow<DbImport.Info?>(null)
    val pendingImport: StateFlow<DbImport.Info?> = _pendingImport.asStateFlow()

    /** Идёт чтение/проверка или замена базы — кнопка загрузки недоступна. */
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /**
     * Шаг 1: скопировать выбранный файл во временный [tmp] и проверить (в т.ч. пробным
     * открытием через Room). Ошибка — в [error] (сообщение уже понятное пользователю).
     */
    fun prepareImport(context: Context, open: () -> InputStream, tmp: File) {
        if (_importing.value) return
        _importing.value = true
        viewModelScope.launch {
            try {
                _pendingImport.value = withContext(Dispatchers.IO) {
                    tmp.parentFile?.mkdirs()
                    open().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    DbImport.inspect(context, tmp)
                }
            } catch (e: IOException) {
                // В т.ч. DbImport.InvalidDbException.
                tmp.delete()
                _error.value = e.message ?: "Не удалось прочитать файл"
            } finally {
                _importing.value = false
            }
        }
    }

    fun cancelImport(tmp: File) {
        _pendingImport.value = null
        tmp.delete()
    }

    /**
     * Шаг 2 (после подтверждения): копия текущей базы в [safetyDir], загружаемый файл —
     * рядом с базой, сброс выбора СПО (её номер из прежней базы в новой может не
     * существовать), закрытие Room, атомарная подмена, [restart].
     *
     * Выполняется в [NonCancellable]: уход с экрана или поворот не прервут работу
     * между закрытием базы и перезапуском. После закрытия Room приложение
     * перезапускается в любом случае — со старой ссылкой на закрытую базу оно бы упало.
     */
    fun confirmImport(tmp: File, dbFile: File, safetyDir: File, restart: () -> Unit) {
        if (_pendingImport.value == null || _importing.value) return
        _pendingImport.value = null
        _importing.value = true
        viewModelScope.launch {
            withContext(NonCancellable) {
                var closed = false
                try {
                    if (dbFile.exists()) DbBackup(repo, dbFile, safetyDir).create()
                    withContext(Dispatchers.IO) {
                        val staged = DbImport.stage(tmp, dbFile)
                        repo.clearCurrentSpoIdNow()
                        closed = true
                        AppDatabase.closeInstance()
                        try {
                            DbImport.commit(staged, dbFile)
                        } finally {
                            staged.delete()
                            tmp.delete()
                        }
                    }
                } catch (e: IOException) {
                    _error.value = "Не удалось загрузить базу: ${e.message}"
                } catch (e: SQLException) {
                    _error.value = "Ошибка базы: ${e.message}"
                } finally {
                    if (closed) withContext(Dispatchers.Main) { restart() } else _importing.value = false
                }
            }
        }
    }
}
