package com.uchet.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uchet.BuildConfig
import java.io.File
import java.util.Locale

/** Раздел «Обновление» в настройках. */
@Composable
fun UpdateSection(viewModel: UpdateViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Подсказка после отправки в настройки «Установка неизвестных приложений».
    var permissionHint by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Hint("Установлена сборка ${BuildConfig.VERSION_CODE}")

        when (val s = state) {
            UpdateViewModel.State.Idle, UpdateViewModel.State.UpToDate, is UpdateViewModel.State.Error -> {
                when (s) {
                    UpdateViewModel.State.UpToDate -> Text("Установлена последняя версия")
                    is UpdateViewModel.State.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                OutlinedButton(onClick = { viewModel.check() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить обновления")
                }
            }
            UpdateViewModel.State.Checking -> {
                Text("Проверка…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is UpdateViewModel.State.Available -> {
                Text("Доступна сборка ${s.release.versionCode}: ${s.release.title}")
                if (s.release.notes.isNotBlank()) Hint(s.release.notes)
                Button(onClick = { viewModel.download(context.cacheDir) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Скачать и установить" + sizeLabel(s.release.apkSize))
                }
            }
            is UpdateViewModel.State.Downloading -> {
                Text("Скачивание сборки ${s.release.versionCode}…")
                if (s.progress >= 0f) {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            is UpdateViewModel.State.Ready -> {
                Text("Сборка ${s.release.versionCode} скачана")
                Button(
                    onClick = { permissionHint = !installApk(context, s.apk) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Установить") }
                if (permissionHint) {
                    Hint("Разрешите «Установку неизвестных приложений» для этого приложения и нажмите «Установить» ещё раз.")
                }
                Hint(
                    "Данные сохраняются: обновление ставится поверх. Если телефон пишет «Приложение не установлено», " +
                        "сборка подписана другим ключом — не удаляйте приложение, сначала сохраните копию базы."
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun sizeLabel(bytes: Long): String =
    if (bytes > 0) String.format(Locale.US, " (%.1f МБ)", bytes / 1_048_576.0) else ""

/**
 * Запустить системную установку APK.
 * @return false — нет разрешения на установку, открыты настройки, чтобы его дать.
 */
fun installApk(context: Context, apk: File): Boolean {
    if (!context.packageManager.canRequestPackageInstalls()) {
        val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        try {
            context.startActivity(settings)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        return false
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    val install = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(install)
    return true
}
