package com.uchet.ui.bha

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.model.BhaComponentEntity
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.MainViewModel
import com.uchet.ui.components.ConfirmDialog
import com.uchet.ui.components.EmptyState
import com.uchet.ui.components.digitsOnly
import com.uchet.util.formatMetersFull

@Composable
fun BhaScreen(
    mainViewModel: MainViewModel,
    viewModel: BhaViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val currentSpoId by mainViewModel.currentSpoId.collectAsStateWithLifecycle()
    LaunchedEffect(currentSpoId) { viewModel.attach(currentSpoId) }

    val components by viewModel.components.collectAsStateWithLifecycle()
    val total by viewModel.totalLength.collectAsStateWithLifecycle()

    if (currentSpoId == null) {
        EmptyState(message = "Нет выбранной СПО")
        return
    }

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BhaComponentEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Элемент") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Компоновка (BHA)", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Суммарная длина: ${formatMetersFull(total)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item {
                Text(
                    "Компоновка физически идёт в скважину первой, до всех рядов труб.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            if (components.isEmpty()) {
                item { Text("Компоновка пуста", modifier = Modifier.padding(4.dp)) }
            } else {
                items(components, key = { "bha_${it.id}" }) { c ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${c.indexInBha + 1}. ${c.name}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${c.sizeLabel} · ${formatMetersFull(c.lengthM)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { deleteTarget = c }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddBhaDialog(
            onDismiss = { showAdd = false },
            onSave = { name, cm, size ->
                viewModel.add(name, cm, size)
                showAdd = false
            }
        )
    }

    deleteTarget?.let { c ->
        ConfirmDialog(
            title = "Удалить элемент",
            text = "Удалить «${c.name}» из компоновки?",
            onConfirm = {
                viewModel.delete(c.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun AddBhaDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, lengthCm: Int, sizeLabel: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }

    val cm = length.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Элемент компоновки") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Размер") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it.digitsOnly().take(6) },
                    label = { Text("Длина, см") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), cm, size.trim()) }, enabled = name.isNotBlank() && cm > 0) {
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
