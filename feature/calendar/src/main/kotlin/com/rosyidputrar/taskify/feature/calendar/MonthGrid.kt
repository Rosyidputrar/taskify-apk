package com.rosyidputrar.taskify.feature.calendar

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import com.rosyidputrar.taskify.core.domain.calendar.MonthCell
import com.rosyidputrar.taskify.core.domain.calendar.WEEKDAY_LABELS
import com.rosyidputrar.taskify.core.domain.calendar.describeActivity
import com.rosyidputrar.taskify.core.domain.time.Dates

internal fun CalendarKind.module(): TaskifyModule = when (this) {
    CalendarKind.Tasks -> TaskifyModule.Tasks
    CalendarKind.Journal -> TaskifyModule.Journal
    CalendarKind.Notes -> TaskifyModule.Notes
    CalendarKind.Finance -> TaskifyModule.Finance
    CalendarKind.Vault -> TaskifyModule.Vault
}

internal fun DayActivity.has(kind: CalendarKind): Boolean = when (kind) {
    CalendarKind.Tasks -> tasks > 0
    CalendarKind.Journal -> journal > 0
    CalendarKind.Notes -> notes > 0
    CalendarKind.Finance -> transactions > 0
    CalendarKind.Vault -> vault > 0
}

@Composable
fun WeekdayHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * Grid bulan. Geser horizontal = bulan sebelumnya/berikutnya (pola CompactCalendarView).
 * Tiap sel: angka hari, tint mood jurnal, dan batang warna bertumpuk per modul — bukan titik.
 */
@Composable
fun MonthGrid(
    month: String,
    grid: List<MonthCell>,
    today: String,
    selected: String,
    activity: Map<String, DayActivity>,
    visible: Set<CalendarKind>,
    onSelect: (String) -> Unit,
    onShift: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    AnimatedContent(
        targetState = month to grid,
        // Hanya ganti BULAN yang menggeser; data bulan yang sama diperbarui tanpa animasi.
        contentKey = { it.first },
        transitionSpec = {
            val forward = targetState.first > initialState.first
            (slideInHorizontally(TaskifyMotion.screenOffset) { if (forward) it else -it }) togetherWith
                (slideOutHorizontally(TaskifyMotion.screenOffset) { if (forward) -it else it })
        },
        label = "month",
        modifier = modifier.pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (total < -threshold) onShift(1) else if (total > threshold) onShift(-1) },
                onHorizontalDrag = { _, delta -> total += delta },
            )
        },
    ) { (_, cells) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            isToday = cell.date == today,
                            isSelected = cell.date == selected,
                            activity = activity[cell.date],
                            visible = visible,
                            onClick = { onSelect(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    cell: MonthCell,
    isToday: Boolean,
    isSelected: Boolean,
    activity: DayActivity?,
    visible: Set<CalendarKind>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val modules = TaskifyTheme.modules
    // Tint mood: makin baik mood-nya makin pekat tint jurnal.
    val moodTint = activity?.mood?.takeIf { CalendarKind.Journal in visible }?.let {
        modules.accent(TaskifyModule.Journal).copy(alpha = 0.04f + 0.035f * it)
    }
    val background = when {
        isSelected -> colors.primary
        moodTint != null -> moodTint
        else -> colors.surfaceContainerLow
    }
    val shape = RoundedCornerShape(10.dp)
    val description = buildString {
        append(Dates.format(cell.date, "EEEE, d MMMM"))
        if (isToday) append(", hari ini")
        activity?.let { describeActivity(it) }?.takeIf { it.isNotEmpty() }?.let { append(". ").append(it) }
    }
    Column(
        modifier
            .let { if (compact) it.height(56.dp) else it.aspectRatio(0.78f) }
            .clip(shape)
            .background(background)
            .border(if (isToday && !isSelected) 2.dp else 0.dp, if (isToday) colors.primary else Color.Transparent, shape)
            .semantics {
                role = Role.Button
                selected = isSelected
                contentDescription = description
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            cell.day.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isSelected -> colors.onPrimary
                !cell.inMonth -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                else -> colors.onSurface
            },
        )
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        // Batang aktivitas bertumpuk, satu per modul yang punya aktivitas.
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CalendarKind.entries.filter { it in visible && activity?.has(it) == true }.take(if (compact) 3 else 5).forEach { kind ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) colors.onPrimary.copy(alpha = 0.85f) else modules.accent(kind.module())),
                )
            }
        }
    }
}

/** Strip satu minggu — tampil di atas saat grid bulan tergulir keluar layar. */
@Composable
fun WeekStrip(
    week: List<String>,
    today: String,
    selected: String,
    activity: Map<String, DayActivity>,
    visible: Set<CalendarKind>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        week.forEach { date ->
            DayCell(
                cell = MonthCell(date, Dates.parse(date).dayOfMonth, inMonth = true),
                isToday = date == today,
                isSelected = date == selected,
                activity = activity[date],
                visible = visible,
                onClick = { onSelect(date) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }
}

