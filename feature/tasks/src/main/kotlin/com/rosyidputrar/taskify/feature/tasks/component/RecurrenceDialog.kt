package com.rosyidputrar.taskify.feature.tasks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.WEEKDAY_SHORT
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import com.rosyidputrar.taskify.core.domain.tasks.describeRule
import com.rosyidputrar.taskify.core.domain.tasks.occurrencesBetween
import com.rosyidputrar.taskify.core.domain.tasks.parseRule
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.ui.DatePickerSheet

private val FREQ_LABELS = listOf(
    RecurrenceFreq.None to "Tidak",
    RecurrenceFreq.Daily to "Harian",
    RecurrenceFreq.Weekdays to "Hari kerja",
    RecurrenceFreq.Weekly to "Mingguan",
    RecurrenceFreq.Monthly to "Bulanan",
    RecurrenceFreq.Yearly to "Tahunan",
)

private fun unitOf(freq: RecurrenceFreq) = when (freq) {
    RecurrenceFreq.Daily -> "hari"
    RecurrenceFreq.Weekly -> "minggu"
    RecurrenceFreq.Monthly -> "bulan"
    RecurrenceFreq.Yearly -> "tahun"
    else -> ""
}

/** Pembuat pengulangan (RRULE) dengan pratinjau 5 kejadian berikutnya. */
@Composable
fun RecurrenceDialog(rule: String?, anchor: String, today: String, onSave: (RecurrenceState) -> Unit, onDismiss: () -> Unit) {
    var state by remember(rule) { mutableStateOf(parseRule(rule).let { if (it.freq == RecurrenceFreq.None) it.copy(freq = RecurrenceFreq.Weekly) else it }) }
    var pickUntil by remember { mutableStateOf(false) }
    val built = buildRule(state, anchor)
    val preview = built?.let { occurrencesBetween(it, anchor, maxOf(anchor, today), Dates.addDays(maxOf(anchor, today), 800), 5) }.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ulangi tugas") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FREQ_LABELS.forEach { (freq, label) ->
                        FilterChip(selected = state.freq == freq, onClick = { state = state.copy(freq = freq) }, label = { Text(label) })
                    }
                }
                if (state.freq in setOf(RecurrenceFreq.Daily, RecurrenceFreq.Weekly, RecurrenceFreq.Monthly, RecurrenceFreq.Yearly)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Setiap", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { state = state.copy(interval = (state.interval - 1).coerceAtLeast(1)) }) { Icon(Icons.Rounded.Remove, contentDescription = "Kurangi") }
                        Text("${state.interval}", style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { state = state.copy(interval = (state.interval + 1).coerceAtMost(99)) }) { Icon(Icons.Rounded.Add, contentDescription = "Tambah") }
                        Text(unitOf(state.freq), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (state.freq == RecurrenceFreq.Weekly) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEKDAY_SHORT.forEachIndexed { index, label ->
                            val selected = index in state.byDay.ifEmpty { listOf(Dates.weekdayOf(anchor)) }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val current = state.byDay.ifEmpty { listOf(Dates.weekdayOf(anchor)) }
                                    val next = if (selected) current - index else current + index
                                    state = state.copy(byDay = next.ifEmpty { current }.sorted())
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                if (state.freq != RecurrenceFreq.None) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.until?.let { "Sampai ${Dates.format(it, "d MMM yyyy")}" } ?: "Tanpa batas akhir",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { pickUntil = true }) { Text("Ubah") }
                        if (state.until != null) TextButton(onClick = { state = state.copy(until = null) }) { Text("Hapus") }
                    }
                    Text(describeRule(built, anchor), style = MaterialTheme.typography.titleSmall)
                    if (preview.isNotEmpty()) {
                        Text(
                            "Berikutnya: " + preview.joinToString(" · ") { Dates.format(it, "EEE d MMM") },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(state) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )

    if (pickUntil) {
        DatePickerSheet(
            initial = state.until ?: Dates.addDays(anchor, 30),
            today = today,
            allowClear = false,
            onPick = { picked -> state = state.copy(until = picked); pickUntil = false },
            onDismiss = { pickUntil = false },
        )
    }
}

