package com.uchet.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uchet.BuildConfig
import com.uchet.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Состояние обновления из программы. Один экземпляр на Activity: им пользуются
 * и вопрос «Доступно обновление» при запуске, и раздел «Обновление» в настройках.
 */
class UpdateViewModel : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: UpdateChecker.Release) : State
        /** [progress] 0..1, или -1 — размер неизвестен. */
        data class Downloading(val release: UpdateChecker.Release, val progress: Float) : State
        data class Ready(val release: UpdateChecker.Release, val apk: File) : State
        data class Error(val message: String) : State
    }

    private val checker = UpdateChecker(BuildConfig.UPDATE_REPO)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Вопрос при запуске показываем один раз за сеанс (Compose-состояние — чтобы диалог закрывался). */
    var startupPromptHandled by mutableStateOf(false)

    fun check() {
        val s = _state.value
        if (s is State.Checking || s is State.Downloading || s is State.Ready) return
        _state.value = State.Checking
        viewModelScope.launch {
            _state.value = try {
                checker.findUpdate(BuildConfig.VERSION_CODE)?.let { State.Available(it) } ?: State.UpToDate
            } catch (e: IOException) {
                State.Error("Не удалось проверить обновления: ${e.message ?: "нет сети"}")
            }
        }
    }

    /** Скачать APK в [cacheDir]/updates (путь из file_paths.xml). */
    fun download(cacheDir: File) {
        val release = (_state.value as? State.Available)?.release ?: return
        _state.value = State.Downloading(release, 0f)
        viewModelScope.launch {
            _state.value = try {
                val dir = File(cacheDir, "updates")
                // Старые скачанные APK не нужны.
                dir.listFiles()?.forEach { it.delete() }
                val apk = checker.download(release, File(dir, "uchet-${release.versionCode}.apk")) { p ->
                    _state.value = State.Downloading(release, p)
                }
                State.Ready(release, apk)
            } catch (e: IOException) {
                State.Error("Не удалось скачать обновление: ${e.message ?: "нет сети"}")
            }
        }
    }
}
