package com.rosyidputrar.taskify.feature.tasks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** DatePicker Material bekerja dengan milidetik UTC tengah malam; tanggal lokal tetap teks. */
private fun dateToUtcMillis(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun utcMillisToDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initial: String?,
    today: String,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    allowClear: Boolean = true,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = dateToUtcMillis(initial ?: today))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.selectedDateMillis?.let(::utcMillisToDate)) }) { Text("Pilih") }
        },
        dismissButton = {
            if (allowClear && initial != null) {
                TextButton(onClick = { onPick(null) }) { Text("Hapus tanggal") }
            } else {
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        },
    ) { DatePicker(state = state, showModeToggle = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val (h, m) = initial?.take(5)?.split(":")?.let { (it.getOrNull(0)?.toIntOrNull() ?: 9) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (9 to 0)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih jam") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onPick("%02d:%02d".format(state.hour, state.minute)) }) { Text("Pilih") } },
        dismissButton = {
            if (initial != null) TextButton(onClick = { onPick(null) }) { Text("Tanpa jam") } else TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
