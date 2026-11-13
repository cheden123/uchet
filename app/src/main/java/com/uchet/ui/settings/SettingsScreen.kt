package com.uchet.ui.settings

import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.db.AppDatabase
import com.uchet.data.db.LegacyDbGuard
import com.uchet.data.model.DiameterPreset
import com.uchet.export.DbImport
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.components.ConfirmDialog
import com.uchet.ui.components.SectionHeader
import com.uchet.ui.update.UpdateSection
import com.uchet.ui.update.UpdateViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Папка копий текущей базы, сделанных перед загрузкой другой (filesDir). */
private const val BEFORE_IMPORT_DIR = "before_import"

@Composable
fun SettingsScreen(
    updateViewModel: UpdateViewModel,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var newName by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DiameterPreset?>(null) }

    // Сохранённые копии базы: старой версии (LegacyDbGuard) и перед загрузкой другой базы.
    // Только основные .db (рядом может лежать «-wal»), новые сверху.
    val savedCopies = remember {
        listOf(LegacyDbGuard.BACKUP_DIR, BEFORE_IMPORT_DIR)
            .flatMap { dir ->
                File(context.filesDir, dir).listFiles { f -> f.isFile && f.name.endsWith(".db") }.orEmpty().toList()
            }
            .sortedByDescending { it.lastModified() }
    }

    // Загрузка базы: состояние и работа — в ViewModel (переживают поворот экрана).
    val importTmp = remember { File(context.cacheDir, "import/incoming.db") }
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()

    val pickDbFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        viewModel.prepareImport(
            context = context.applicationContext,
            open = { resolver.openInputStream(uri) ?: throw FileNotFoundException("Файл недоступен") },
            tmp = importTmp
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { SectionHeader("Обновление") }
            item { UpdateSection(updateViewModel) }

            item { SectionHeader("Пресеты типоразмеров") }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Новый типоразмер") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            viewModel.add(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank()
                    ) { Text("Добавить") }
                }
            }
            item { Hint("Порядок — по добавлению (не по алфавиту). Дубли не добавляются.") }
            items(presets, key = { "preset_${it.id}" }) { preset ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(preset.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { deleteTarget = preset }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить")
                        }
                    }
                }
            }

            item { SectionHeader("Перенос базы") }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val file = viewModel.createBackup(
                                    dbFile = context.getDatabasePath(AppDatabase.DB_NAME),
                                    outDir = File(context.cacheDir, "exports") // путь из file_paths.xml
                                )
                                shareFile(context, file, "Резервная копия базы")
                            } catch (e: IOException) {
                                snackbar.showSnackbar("Не удалось создать копию: ${e.message}")
                            } catch (e: SQLException) {
                                snackbar.showSnackbar("Ошибка базы при копировании: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить копию базы (.db)") }
            }
            item {
                OutlinedButton(
                    onClick = { pickDbFile.launch(arrayOf("*/*")) },
                    enabled = !importing,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Загрузить базу из файла (.db)") }
            }
            item {
                Hint(
                    "Перенос на другой телефон или после переустановки: «Сохранить копию» → " +
                        "отправить файл себе → на новом телефоне «Загрузить базу из файла». " +
                        "Загрузка заменяет все данные; прежняя база сохраняется в «Сохранённые копии»."
                )
            }

            if (savedCopies.isNotEmpty()) {
                item { SectionHeader("Сохранённые копии") }
                item {
                    Hint(
                        "uchet_v… — база старой версии, найденная при обновлении (её данные не перенесены). " +
                            "uchet_backup_… — база, которая была до загрузки другой."
                    )
                }
                items(savedCopies, key = { "saved_${it.parentFile?.name}_${it.name}" }) { file ->
                    OutlinedButton(
                        onClick = { shareFile(context, file, "Копия базы") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Отправить ${file.name}") }
                }
            }
        }
    }

    pendingImport?.let { info ->
        ConfirmDialog(
            title = "Заменить базу?",
            text = "В файле: СПО — ${info.spoCount}, труб — ${info.pipeCount} (версия базы ${info.version}).\n\n" +
                "Все текущие данные будут заменены. Их копия сохранится в «Сохранённые копии». " +
                "Приложение перезапустится.",
            confirmLabel = "Заменить",
            onConfirm = {
                val app = context.applicationContext
                viewModel.confirmImport(
                    tmp = importTmp,
                    dbFile = app.getDatabasePath(AppDatabase.DB_NAME),
                    safetyDir = File(app.filesDir, BEFORE_IMPORT_DIR),
                    // Контекст приложения, а не экрана: перезапуск может случиться уже после ухода с него.
                    restart = { restartApp(app) }
                )
            },
            onDismiss = { viewModel.cancelImport(importTmp) }
        )
    }

    deleteTarget?.let { preset ->
        ConfirmDialog(
            title = "Удалить типоразмер",
            text = "Удалить «${preset.name}» из пресетов? (на уже введённых трубах это не отразится)",
            onConfirm = {
                viewModel.delete(preset)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Отправить файл через FileProvider (пути — в res/xml/file_paths.xml). */
private fun shareFile(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/**
 * Перезапуск после замены базы: старый процесс держит закрытую базу и ViewModel'и
 * со старыми данными, проще начать с чистого листа.
 */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
