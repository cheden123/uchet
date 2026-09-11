package com.uchet.ui.sposelect

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.model.SpoEntity
import com.uchet.export.ExcelExporter
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.MainViewModel
import com.uchet.ui.components.ConfirmDialog
import com.uchet.ui.components.EmptyState
import com.uchet.ui.components.SpoFormDialog
import com.uchet.util.formatDate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpoSelectScreen(
    mainViewModel: MainViewModel,
    viewModel: SpoSelectViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val spos by viewModel.spos.collectAsStateWithLifecycle()
    val currentSpoId by mainViewModel.currentSpoId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val exporter = remember { ExcelExporter(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SpoEntity?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Новая СПО") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (spos.isEmpty()) {
                EmptyState(message = "СПО пока нет. Создайте первую — кнопка «＋ Новая СПО» внизу.")
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(spos, key = { "spo_${it.id}" }) { spo ->
                        SpoCard(
                            spo = spo,
                            isCurrent = spo.id == currentSpoId,
                            onSelect = { mainViewModel.setCurrent(spo.id) },
                            onToggleCompleted = { scope.launch { viewModel.setCompleted(spo.id, !spo.isCompleted) } },
                            onView = { export(spo, share = false, viewModel, exporter, context, scope, snackbarHostState) },
                            onShare = { export(spo, share = true, viewModel, exporter, context, scope, snackbarHostState) },
                            onDelete = { deleteTarget = spo }
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        SpoFormDialog(
            defaultDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()),
            onDismiss = { showCreate = false },
            onSave = { date, cluster, well, field, title ->
                scope.launch {
                    val id = viewModel.create(date, cluster, well, field, title)
                    mainViewModel.setCurrent(id)
                    showCreate = false
                }
            }
        )
    }

    deleteTarget?.let { spo ->
        ConfirmDialog(
            title = "Удалить СПО",
            text = "Удалить «${spo.title.ifBlank { "СПО" }}» вместе со всеми рядами, трубами и компоновкой?",
            onConfirm = {
                scope.launch { viewModel.delete(spo) }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

private fun export(
    spo: SpoEntity,
    share: Boolean,
    viewModel: SpoSelectViewModel,
    exporter: ExcelExporter,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState
) {
    scope.launch {
        try {
            val data = viewModel.exportData(spo.id)
            val file = exporter.export(data)
            val uri = exporter.shareUri(file)
            val intent = if (share) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт СПО"))
        } catch (e: Exception) {
            snackbar.showSnackbar("Ошибка экспорта: ${e.message}")
        }
    }
}

@Composable
private fun SpoCard(
    spo: SpoEntity,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onToggleCompleted: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        spo.title.ifBlank { "СПО" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatDate(spo.startDate)} · Куст ${spo.wellCluster} · Скв. ${spo.wellNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Месторождение: ${spo.fieldName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = onToggleCompleted,
                    label = { Text(if (spo.isCompleted) "Завершена" else "В работе") }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrent) {
                    Text(
                        "Текущая",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = onView) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Просмотр", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
