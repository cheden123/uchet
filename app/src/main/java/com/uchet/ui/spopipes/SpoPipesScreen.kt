package com.uchet.ui.spopipes

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.model.SpoPipeRow
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.MainViewModel
import com.uchet.ui.components.EmptyState
import com.uchet.util.formatMetersFull

@Composable
fun SpoPipesScreen(
    mainViewModel: MainViewModel,
    onEditRun: (Long) -> Unit,
    viewModel: SpoPipesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val currentSpoId by mainViewModel.currentSpoId.collectAsStateWithLifecycle()
    LaunchedEffect(currentSpoId) { viewModel.attach(currentSpoId) }

    val rows by viewModel.rows.collectAsStateWithLifecycle()

    if (currentSpoId == null) {
        EmptyState(message = "Нет выбранной СПО")
        return
    }

    sealed interface Item {
        data class Header(val runNumber: Int, val runId: Long) : Item
        data class Pipe(val row: SpoPipeRow) : Item
    }

    val displayItems = remember(rows) {
        buildList {
            var lastRun = -1
            for (row in rows) {
                if (row.runNumber != lastRun) {
                    add(Item.Header(row.runNumber, row.runId))
                    lastRun = row.runNumber
                }
                add(Item.Pipe(row))
            }
        }
    }

    if (rows.isEmpty()) {
        EmptyState(message = "В этой СПО ещё нет труб")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(displayItems, key = {
            when (it) {
                is Item.Header -> "runheader_${it.runId}"
                is Item.Pipe -> "sporow_${it.row.pipeId}"
            }
        }) { item ->
            when (item) {
                is Item.Header -> RunHeaderRow(item.runNumber, item.runId, onEditRun)
                is Item.Pipe -> PipeDetailRow(item.row)
            }
        }
    }
}

@Composable
private fun RunHeaderRow(runNumber: Int, runId: Long, onEditRun: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Ряд $runNumber",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onEditRun(runId) }) {
            Icon(Icons.Filled.Edit, contentDescription = "Редактировать ряд", tint = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider()
}

@Composable
private fun PipeDetailRow(row: SpoPipeRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${row.globalNumber}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(36.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "${row.diameterLabel} · ${formatMetersFull(row.lengthM)}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (row.crossoverLabel.isNotBlank()) {
                    Text(
                        "Оборудование: ${row.crossoverLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Длина труб (с оборудованием):", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMetersFull(row.pipesOnly), fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("С учётом компоновки:", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMetersFull(row.withBha), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}
