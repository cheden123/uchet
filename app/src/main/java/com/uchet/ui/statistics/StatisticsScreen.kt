package com.uchet.ui.statistics

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.MainViewModel
import com.uchet.ui.components.EmptyState
import com.uchet.ui.components.KeyValueRow
import com.uchet.ui.components.SectionHeader
import com.uchet.util.formatMetersFull

@Composable
fun StatisticsScreen(
    mainViewModel: MainViewModel,
    viewModel: StatisticsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val currentSpoId by mainViewModel.currentSpoId.collectAsStateWithLifecycle()
    LaunchedEffect(currentSpoId) { viewModel.attach(currentSpoId) }

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val completedRuns by viewModel.completedRuns.collectAsStateWithLifecycle()
    val allRuns by viewModel.allRuns.collectAsStateWithLifecycle()
    val crossoversTotal by viewModel.crossoversTotal.collectAsStateWithLifecycle()

    if (currentSpoId == null) {
        EmptyState(message = "Нет выбранной СПО")
        return
    }

    val byDiameter = remember(rows) {
        val order = rows.map { it.diameterLabel }.distinct()
        order.map { label ->
            val list = rows.filter { it.diameterLabel == label }
            DiameterGroup(label, list.size, list.sumOf { it.lengthM })
        }
    }

    // Длины всех труб СПО в физическом порядке (как в «Трубы СПО» / глобальной нумерации).
    val pipeLengths = remember(rows) { rows.map { it.lengthM } }
    val decileSums = remember(pipeLengths) {
        pipeLengths.chunked(10).mapIndexed { i, chunk ->
            val from = i * 10 + 1
            val to = i * 10 + chunk.size
            "$from—$to" to chunk.sum()
        }
    }
    val totalPipeLength = remember(pipeLengths) { pipeLengths.sum() }
    val runNumberById = remember(allRuns) { allRuns.mapIndexed { i, r -> r.id to (i + 1) }.toMap() }
    val history = remember(completedRuns, rows, runNumberById) {
        completedRuns.map { run ->
            val runRows = rows.filter { it.runId == run.id }
            RunHistory(
                runNumber = runNumberById[run.id] ?: 0,
                pipeCount = runRows.size,
                totalLength = runRows.sumOf { it.lengthM }
            )
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Всего труб: ${rows.size}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Суммарная длина труб: ${formatMetersFull(totalPipeLength)}")
                    Text(
                        "Суммарная длина оборудования: ${formatMetersFull(crossoversTotal)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "С учётом компоновки: ${formatMetersFull(rows.lastOrNull()?.withBha ?: 0.0)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { SectionHeader("Сумма каждых 10 труб") }
        if (decileSums.isEmpty()) {
            item { Text("Труб пока нет", modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        decileSums.forEach { (range, sum) ->
                            KeyValueRow(range, formatMetersFull(sum))
                        }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        KeyValueRow("Общая длина труб", formatMetersFull(totalPipeLength))
                    }
                }
            }
        }

        item { SectionHeader("Группировка по диаметрам") }
        if (byDiameter.isEmpty()) {
            item { Text("Труб пока нет", modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            items(byDiameter, key = { "dia_${it.label}" }) { g ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.label, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        KeyValueRow("Количество", "${g.count} шт.")
                        KeyValueRow("Длина", formatMetersFull(g.length))
                    }
                }
            }
        }

        item { SectionHeader("История завершённых рядов") }
        if (history.isEmpty()) {
            item {
                Text(
                    "Завершённых рядов пока нет. Активный (незавершённый) ряд в историю не попадает.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history, key = { "run_hist_${it.runNumber}" }) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Ряд № ${h.runNumber}", style = MaterialTheme.typography.titleSmall)
                        Text("${h.pipeCount} труб · ${formatMetersFull(h.totalLength)}")
                    }
                }
            }
        }
    }
}

private data class DiameterGroup(val label: String, val count: Int, val length: Double)
private data class RunHistory(val runNumber: Int, val pipeCount: Int, val totalLength: Double)
