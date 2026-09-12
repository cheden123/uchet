package com.uchet.ui.editrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.CrossoverTypes
import com.uchet.data.model.PipeEntity
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.components.ConfirmDialog
import com.uchet.ui.components.PipeEditorDialog
import com.uchet.util.formatMetersFull

@Composable
fun EditRunScreen(
    runId: Long,
    onBack: () -> Unit,
    viewModel: EditRunViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    LaunchedEffect(runId) { viewModel.attach(runId) }

    val pipes by viewModel.pipes.collectAsStateWithLifecycle()
    val crossovers by viewModel.crossovers.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val globalOffset by viewModel.globalOffset.collectAsStateWithLifecycle()

    var insertAfter by remember { mutableStateOf<Int?>(null) } // null=закрыто, -1 = в начало
    var editPipe by remember { mutableStateOf<PipeEntity?>(null) }
    var deletePipeTarget by remember { mutableStateOf<PipeEntity?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Ряд № — " + if (run?.isCompleted == true) "завершён" else "активный",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item(key = "insert_start") {
                OutlinedButton(
                    onClick = { insertAfter = -1 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.width(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Вставить трубу в начало ряда")
                }
                Spacer(Modifier.padding(4.dp))
            }

            items(pipes, key = { "epipe_${it.id}" }) { pipe ->
                PipeEditRow(
                    pipe = pipe,
                    globalNumber = globalOffset + pipe.indexInRun,
                    crossover = crossovers.firstOrNull { it.afterPipeIndex == pipe.indexInRun },
                    onInsertAfter = { insertAfter = pipe.indexInRun },
                    onEdit = { editPipe = pipe },
                    onDelete = { deletePipeTarget = pipe }
                )
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
            }

            if (crossovers.isNotEmpty()) {
                item(key = "eq_header") {
                    Text(
                        "Оборудование ряда",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(crossovers, key = { "ecross_${it.id}" }) { c ->
                    EquipmentInfoRow(c)
                }
            }
        }
    }

    insertAfter?.let { after ->
        PipeEditorDialog(
            presets = presets.map { it.name },
            title = if (after < 0) "Вставить трубу в начало" else "Вставить трубу после № ${after + 1}",
            initialDiameter = presets.firstOrNull()?.name ?: "",
            onDismiss = { insertAfter = null },
            onSave = { cm, dia ->
                viewModel.insertAfter(after, cm, dia)
                insertAfter = null
            }
        )
    }

    editPipe?.let { pipe ->
        PipeEditorDialog(
            presets = presets.map { it.name },
            title = "Труба #${globalOffset + pipe.indexInRun} (№${pipe.indexInRun})",
            initialLengthCm = (pipe.lengthM * 100).toInt().toString(),
            initialDiameter = pipe.diameterLabel,
            onDismiss = { editPipe = null },
            onSave = { cm, dia ->
                viewModel.updatePipe(pipe, cm, dia)
                editPipe = null
            }
        )
    }

    deletePipeTarget?.let { pipe ->
        ConfirmDialog(
            title = "Удалить трубу",
            text = "Удалить трубу #${globalOffset + pipe.indexInRun} (№${pipe.indexInRun}, ${formatMetersFull(pipe.lengthM)})? Индексы оставшихся труб будут пересчитаны.",
            onConfirm = {
                viewModel.deletePipe(pipe)
                deletePipeTarget = null
            },
            onDismiss = { deletePipeTarget = null }
        )
    }
}

@Composable
private fun PipeEditRow(
    pipe: PipeEntity,
    globalNumber: Int,
    crossover: CrossoverEntity?,
    onInsertAfter: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#$globalNumber  (№${pipe.indexInRun})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(116.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${pipe.diameterLabel} · ${formatMetersFull(pipe.lengthM)}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (crossover != null) {
                    Text(
                        "после: ${CrossoverTypes.typeName(crossover.type, crossover.customTypeName)} ${formatMetersFull(crossover.lengthM)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            IconButton(onClick = onInsertAfter) {
                Icon(Icons.Filled.Add, contentDescription = "Вставить после", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EquipmentInfoRow(crossover: CrossoverEntity) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${CrossoverTypes.typeName(crossover.type, crossover.customTypeName)} · ${crossover.diameterLabel} · " +
                "${formatMetersFull(crossover.lengthM)} — " +
                (if (crossover.afterPipeIndex == 0) "перед 1-й трубой" else "после трубы № ${crossover.afterPipeIndex + 1}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
