package com.uchet.ui.settings

import android.content.Intent
import android.database.SQLException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.uchet.data.db.AppDatabase
import com.uchet.data.db.LegacyDbGuard
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.model.DiameterPreset
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.components.ConfirmDialog
import com.uchet.ui.components.SectionHeader

@Composable
fun SettingsScreen(
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
    // Только основные .db (рядом может лежать «-wal»), новые сверху.
    val legacyBackups = remember {
        File(context.filesDir, LegacyDbGuard.BACKUP_DIR)
            .listFiles { f -> f.isFile && f.name.endsWith(".db") }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }

    var newName by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DiameterPreset?>(null) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            item {
                Text(
                    "Порядок — по добавлению (не по алфавиту). Дубли не добавляются.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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

            item { SectionHeader("Резервная копия") }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val file = viewModel.createBackup(
                                    dbFile = context.getDatabasePath(AppDatabase.DB_NAME),
                                    outDir = File(context.cacheDir, "exports") // путь из file_paths.xml
                                )
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Резервная копия базы"))
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
                Text(
                    "Вся база (все СПО) одним файлом uchet_backup_….db — отправьте себе в мессенджер или на почту.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Копии базы старой версии, сохранённые перед обновлением (LegacyDbGuard).
            if (legacyBackups.isNotEmpty()) {
                item { SectionHeader("Данные старой версии") }
                item {
                    Text(
                        "При обновлении найдена база старой версии. Её данные не перенесены, " +
                            "но сохранены в файл — отправьте его разработчику для переноса.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(legacyBackups, key = { "legacy_${it.name}" }) { file ->
                    Button(
                        onClick = {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Старая база"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Отправить ${file.name}") }
                }
            }
        }
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
