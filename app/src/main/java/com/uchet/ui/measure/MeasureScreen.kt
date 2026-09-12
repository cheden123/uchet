package com.uchet.ui.measure

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uchet.data.model.CrossoverEntity
import com.uchet.data.model.CrossoverTypes
import com.uchet.data.model.PipeEntity
import com.uchet.ui.AppViewModelProvider
import com.uchet.ui.MainViewModel
import com.uchet.ui.components.ConfirmDialog
import com.uchet.ui.components.CrossoverEditorDialog
import com.uchet.ui.components.DiameterPickerDialog
import com.uchet.ui.components.EmptyState
import com.uchet.ui.components.SectionHeader
import com.uchet.ui.components.digitsOnly
import com.uchet.util.formatMetersFull
import kotlinx.coroutines.launch

/**
 * Разбор ввода длины трубы: базовые метры + дробная часть (введённые цифры).
 * Обычный режим: baseMeters = 0, ввод в сантиметрах целиком ("963" → 9.63 м).
 * Режим "Все N": baseMeters = 8/9/10, вводятся только сантиметры ("63" → 9.63 м).
 */
fun parseCmInput(digits: String, baseMeters: Int = 0): Double? {
    val value = digits.trim().toIntOrNull() ?: return null
    return baseMeters + value / 100.0
}

@Composable
fun MeasureScreen(
    mainViewModel: MainViewModel,
    onNavigateToSelectSpo: () -> Unit,
    viewModel: MeasureViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val currentSpoId by mainViewModel.currentSpoId.collectAsStateWithLifecycle()
    val currentSpo by mainViewModel.currentSpo.collectAsStateWithLifecycle()

    // Эффект первичной инициализации: привязать СПО и создать активный ряд.
    LaunchedEffect(currentSpoId) {
        viewModel.attach(currentSpoId)
        viewModel.ensureActiveRun()
    }

    val activeRunId by viewModel.activeRunId.collectAsStateWithLifecycle()
    // Эффект восстановления после завершения ряда: активный ряд стал null -> создать следующий.
    LaunchedEffect(currentSpoId, activeRunId) {
        if (currentSpoId != null && activeRunId == null) {
            viewModel.ensureActiveRun()
        }
    }

    val pipes by viewModel.pipes.collectAsStateWithLifecycle()
    val crossovers by viewModel.crossovers.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val activeRunNumber by viewModel.activeRunNumber.collectAsStateWithLifecycle()
    val lastCumulative by viewModel.lastCumulative.collectAsStateWithLifecycle()
    val globalOffset by viewModel.globalOffset.collectAsStateWithLifecycle()

    if (currentSpoId == null) {
        EmptyState(
            message = "Нет выбранной СПО",
            actionLabel = "Выбрать СПО",
            onAction = onNavigateToSelectSpo
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var lengthText by rememberSaveable { mutableStateOf("") }
    var diameter by rememberSaveable { mutableStateOf(viewModel.lastDiameterOrBlank()) }
    // 0 = выключен, иначе базовая целая часть длины в метрах (8/9/10) для быстрого ввода.
    var allSameBaseMeters by rememberSaveable { mutableStateOf(0) }
    val lengthFocusRequester = remember { FocusRequester() }

    LaunchedEffect(presets) {
        if (diameter.isBlank() && presets.isNotEmpty()) diameter = presets.first().name
    }

    var showDiameterPicker by remember { mutableStateOf(false) }
    var crossoverAfterPipe by remember { mutableStateOf<Int?>(null) } // null=закрыто, -1 = перед 1-й трубой
    var editingCrossover by remember { mutableStateOf<CrossoverEntity?>(null) }
    var deletePipeTarget by remember { mutableStateOf<PipeEntity?>(null) }
    var deleteCrossoverTarget by remember { mutableStateOf<CrossoverEntity?>(null) }

    val parsedMeters = parseCmInput(lengthText, if (allSameBaseMeters > 0) allSameBaseMeters else 0)

    fun addPipe() {
        val meters = parseCmInput(lengthText, if (allSameBaseMeters > 0) allSameBaseMeters else 0) ?: return
        if (diameter.isBlank()) {
            showDiameterPicker = true
            scope.launch { snackbarHostState.showSnackbar("Выберите типоразмер") }
            return
        }
        viewModel.addPipeMeters(meters, diameter)
        lengthText = ""
    }

    // Автодобавление в режиме "Все N": ровно 2 цифры -> труба добавляется сама.
    LaunchedEffect(lengthText, allSameBaseMeters, diameter) {
        if (allSameBaseMeters > 0 && lengthText.length == 2 && diameter.isNotBlank()) {
            addPipe()
            lengthFocusRequester.requestFocus()
        }
    }

    // Визуальная разбивка списка труб ряда на "столбцы" по 10 (последняя — сверху).
    val groupedPipes = remember(pipes) { pipes.reversed().chunked(10) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (pipes.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            viewModel.completeActiveRun()
                            scope.launch { snackbarHostState.showSnackbar("Ряд завершён, начат следующий") }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) { Text("Завершить ряд") }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "${currentSpo?.title?.ifBlank { "СПО" } ?: "СПО"} · Ряд ${activeRunNumber} · Труб: ${pipes.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Длина: ${formatMetersFull(lastCumulative?.pipesOnly ?: 0.0)}  ·  с компоновкой: ${formatMetersFull(lastCumulative?.withBha ?: 0.0)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Все:", style = MaterialTheme.typography.labelMedium)
                listOf(8, 9, 10).forEach { base ->
                    FilterChip(
                        selected = allSameBaseMeters == base,
                        onClick = {
                            allSameBaseMeters = if (allSameBaseMeters == base) 0 else base
                            lengthText = ""
                            lengthFocusRequester.requestFocus()
                        },
                        label = { Text("$base..") }
                    )
                }
                if (allSameBaseMeters != 0) {
                    TextButton(
                        onClick = {
                            allSameBaseMeters = 0
                            lengthText = ""
                        }
                    ) { Text("Сброс") }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.digitsOnly().take(if (allSameBaseMeters > 0) 2 else 6) },
                    label = { Text(if (allSameBaseMeters > 0) "См (${allSameBaseMeters}.xx)" else "Длина, см") },
                    prefix = if (allSameBaseMeters > 0) {
                        @Composable { Text("${allSameBaseMeters}.") }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(lengthFocusRequester)
                )
                OutlinedTextField(
                    value = diameter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Типоразмер") },
                    trailingIcon = { Text("▾") },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDiameterPicker = true }
                )
            }

            Button(
                onClick = { addPipe() },
                enabled = (parsedMeters ?: 0.0) > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) { Text("Добавить трубу") }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                groupedPipes.forEachIndexed { colIndex, chunk ->
                    item(key = "column_header_$colIndex") {
                        Text(
                            "Столбец ${colIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    items(chunk, key = { "pipe_${it.id}" }) { pipe ->
                        val crossover = crossovers.firstOrNull { it.afterPipeIndex == pipe.indexInRun }
                        PipeRow(
                            pipe = pipe,
                            globalNumber = globalOffset + pipe.indexInRun,
                            crossover = crossover,
                            onAddCrossover = { crossoverAfterPipe = pipe.indexInRun },
                            onDelete = { deletePipeTarget = pipe }
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    }
                }

                item(key = "crossover_section_header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("Оборудование ряда", Modifier.weight(1f))
                        OutlinedButton(onClick = { crossoverAfterPipe = -1 }) {
                            Icon(Icons.Filled.Add, null, Modifier.width(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Перед 1-й трубой")
                        }
                    }
                }

                items(crossovers, key = { "crossover_${it.id}" }) { crossover ->
                    CrossoverRow(
                        crossover = crossover,
                        onEdit = { editingCrossover = crossover },
                        onDelete = { deleteCrossoverTarget = crossover }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                }

                if (pipes.isEmpty() && crossovers.isEmpty()) {
                    item(key = "empty_hint") {
                        Text(
                            "Введите длину трубы в сантиметрах и нажмите «Добавить трубу».",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // --- диалоги ---

    if (showDiameterPicker) {
        DiameterPickerDialog(
            presets = presets.map { it.name },
            initialValue = diameter,
            onDismiss = { showDiameterPicker = false },
            onConfirm = {
                diameter = it
                viewModel.rememberDiameter(it)
                showDiameterPicker = false
            }
        )
    }

    crossoverAfterPipe?.let { afterIndex ->
        CrossoverEditorDialog(
            presets = presets.map { it.name },
            afterPipeLabel = if (afterIndex < 0) "Перед первой трубой ряда" else "После трубы № ${afterIndex + 1}",
            onDismiss = { crossoverAfterPipe = null },
            onSave = { type, customName, dia, cm, note ->
                viewModel.addCrossover(
                    afterPipeIndex = if (afterIndex < 0) 0 else afterIndex,
                    type = type,
                    customTypeName = customName,
                    diameterLabel = dia,
                    lengthCm = cm,
                    note = note
                )
                crossoverAfterPipe = null
            }
        )
    }

    editingCrossover?.let { c ->
        CrossoverEditorDialog(
            presets = presets.map { it.name },
            afterPipeLabel = if (c.afterPipeIndex == 0) "Перед первой трубой ряда" else "После трубы № ${c.afterPipeIndex + 1}",
            existingType = c.type,
            existingCustomName = c.customTypeName,
            existingDiameter = c.diameterLabel,
            existingLengthCm = formatCm(c.lengthM),
            existingNote = c.note,
            onDismiss = { editingCrossover = null },
            onSave = { type, customName, dia, cm, note ->
                viewModel.updateCrossover(
                    c.copy(
                        type = type,
                        customTypeName = customName,
                        diameterLabel = dia,
                        lengthM = cm / 100.0,
                        note = note
                    )
                )
                editingCrossover = null
            }
        )
    }

    deletePipeTarget?.let { pipe ->
        ConfirmDialog(
            title = "Удалить трубу",
            text = "Удалить трубу #${globalOffset + pipe.indexInRun} (№${pipe.indexInRun}, ${formatMetersFull(pipe.lengthM)})?",
            onConfirm = {
                viewModel.deletePipe(pipe)
                deletePipeTarget = null
            },
            onDismiss = { deletePipeTarget = null }
        )
    }

    deleteCrossoverTarget?.let { c ->
        ConfirmDialog(
            title = "Удалить оборудование",
            text = "Удалить «${CrossoverTypes.typeName(c.type, c.customTypeName)}»?",
            onConfirm = {
                viewModel.deleteCrossover(c.id)
                deleteCrossoverTarget = null
            },
            onDismiss = { deleteCrossoverTarget = null }
        )
    }
}

@Composable
private fun PipeRow(
    pipe: PipeEntity,
    globalNumber: Int,
    crossover: CrossoverEntity?,
    onAddCrossover: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(112.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "#$globalNumber  (№${pipe.indexInRun})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pipe.diameterLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (crossover != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "＋ ${CrossoverTypes.typeName(crossover.type, crossover.customTypeName)} ${formatMetersFull(crossover.lengthM)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Text(
                formatMetersFull(pipe.lengthM),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onAddCrossover) {
            Icon(
                if (crossover == null) Icons.Filled.Add else Icons.Filled.Edit,
                contentDescription = "Оборудование",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun CrossoverRow(
    crossover: CrossoverEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${CrossoverTypes.typeName(crossover.type, crossover.customTypeName)} · ${crossover.diameterLabel}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                buildString {
                    append(if (crossover.afterPipeIndex == 0) "перед 1-й трубой" else "после трубы № ${crossover.afterPipeIndex + 1}")
                    append(" · ")
                    append(formatMetersFull(crossover.lengthM))
                    if (crossover.note.isNotBlank()) append(" · ${crossover.note}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Изменить", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun formatCm(m: Double): String = (m * 100).toInt().toString()
