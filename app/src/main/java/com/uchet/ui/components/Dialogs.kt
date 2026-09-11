package com.uchet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.uchet.data.model.CrossoverTypes

private const val CUSTOM_KEY = "__custom__"

/** Оставляет в строке только цифры. */
fun String.digitsOnly(): String = filter { it.isDigit() }

/**
 * Диалог выбора типоразмера: пресеты + «Свой». При выборе «Свой» ОБЯЗАТЕЛЬНО
 * показывается текстовое поле — выбор не сбрасывается молча в пустоту.
 */
@Composable
fun DiameterPickerDialog(
    presets: List<String>,
    initialValue: String,
    title: String = "Типоразмер",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val customInitial = initialValue.isNotBlank() && initialValue !in presets
    var selected by remember { mutableStateOf(if (customInitial) CUSTOM_KEY else initialValue) }
    var customText by remember { mutableStateOf(if (customInitial) initialValue else "") }
    val result = if (selected == CUSTOM_KEY) customText.trim() else selected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                presets.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == p, onClick = { selected = p })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == p, onClick = { selected = p })
                        Spacer(Modifier.width(8.dp))
                        Text(p)
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected == CUSTOM_KEY, onClick = { selected = CUSTOM_KEY })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected == CUSTOM_KEY, onClick = { selected = CUSTOM_KEY })
                    Spacer(Modifier.width(8.dp))
                    Text("Свой")
                }
                if (selected == CUSTOM_KEY) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text("Свой типоразмер") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(result) }, enabled = result.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/**
 * Диалог добавления/редактирования переводника/оборудования.
 * Тип: Переводник / Шаблон / Свое (для «Свое» — обязательное поле названия).
 */
@Composable
fun CrossoverEditorDialog(
    presets: List<String>,
    afterPipeLabel: String,
    existingType: Int? = null,
    existingCustomName: String = "",
    existingDiameter: String = "",
    existingLengthCm: String = "",
    existingNote: String = "",
    onDismiss: () -> Unit,
    onSave: (type: Int, customTypeName: String, diameterLabel: String, lengthCm: Int, note: String) -> Unit
) {
    var type by remember { mutableStateOf(existingType ?: CrossoverTypes.TRANSLATOR) }
    var customName by remember { mutableStateOf(existingCustomName) }
    var diameter by remember { mutableStateOf(existingDiameter) }
    var lengthText by remember { mutableStateOf(existingLengthCm) }
    var note by remember { mutableStateOf(existingNote) }
    var showDiameterPicker by remember { mutableStateOf(false) }

    val lengthCm = lengthText.toIntOrNull() ?: 0
    val canSave = lengthCm > 0 && diameter.isNotBlank() &&
        (type != CrossoverTypes.CUSTOM || customName.trim().isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingType == null) "Добавить оборудование" else "Изменить оборудование") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    afterPipeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == CrossoverTypes.TRANSLATOR,
                        onClick = { type = CrossoverTypes.TRANSLATOR },
                        label = { Text("Переводник") }
                    )
                    FilterChip(
                        selected = type == CrossoverTypes.TEMPLATE,
                        onClick = { type = CrossoverTypes.TEMPLATE },
                        label = { Text("Шаблон") }
                    )
                    FilterChip(
                        selected = type == CrossoverTypes.CUSTOM,
                        onClick = { type = CrossoverTypes.CUSTOM },
                        label = { Text("Свое") }
                    )
                }
                if (type == CrossoverTypes.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Название (обязательно)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = diameter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Типоразмер") },
                    trailingIcon = { Text("▾") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiameterPicker = true }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.digitsOnly().take(6) },
                    label = { Text("Длина, см") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Примечание") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(type, customName.trim(), diameter, lengthCm, note.trim()) },
                enabled = canSave
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showDiameterPicker) {
        DiameterPickerDialog(
            presets = presets,
            initialValue = diameter,
            onDismiss = { showDiameterPicker = false },
            onConfirm = {
                diameter = it
                showDiameterPicker = false
            }
        )
    }
}

/** Диалог добавления/редактирования трубы (длина в см + типоразмер). */
@Composable
fun PipeEditorDialog(
    presets: List<String>,
    title: String,
    initialLengthCm: String = "",
    initialDiameter: String = "",
    onDismiss: () -> Unit,
    onSave: (lengthCm: Int, diameterLabel: String) -> Unit
) {
    var lengthText by remember { mutableStateOf(initialLengthCm) }
    var diameter by remember { mutableStateOf(initialDiameter) }
    var showDiameterPicker by remember { mutableStateOf(false) }

    val lengthCm = lengthText.toIntOrNull() ?: 0
    val canSave = lengthCm > 0 && diameter.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = lengthText,
                    onValueChange = { lengthText = it.digitsOnly().take(6) },
                    label = { Text("Длина, см") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = diameter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Типоразмер") },
                    trailingIcon = { Text("▾") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDiameterPicker = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(lengthCm, diameter) }, enabled = canSave) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (showDiameterPicker) {
        DiameterPickerDialog(
            presets = presets,
            initialValue = diameter,
            onDismiss = { showDiameterPicker = false },
            onConfirm = {
                diameter = it
                showDiameterPicker = false
            }
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Удалить",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Диалог создания новой СПО. */
@Composable
fun SpoFormDialog(
    defaultDate: String,
    onDismiss: () -> Unit,
    onSave: (startDate: Long, wellCluster: String, wellNumber: String, fieldName: String, title: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultDate) }
    var cluster by remember { mutableStateOf("") }
    var well by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая СПО") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название подвески *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Дата (дд.мм.гггг)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cluster,
                    onValueChange = { cluster = it },
                    label = { Text("Куст №") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = well,
                    onValueChange = { well = it },
                    label = { Text("Скважина №") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text("Месторождение") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ts = parseDateOrToday(date)
                    onSave(ts, cluster.trim(), well.trim(), field.trim(), title.trim())
                },
                enabled = title.isNotBlank()
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun parseDateOrToday(value: String): Long {
    return try {
        val df = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        df.isLenient = false
        df.parse(value.trim())?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}
