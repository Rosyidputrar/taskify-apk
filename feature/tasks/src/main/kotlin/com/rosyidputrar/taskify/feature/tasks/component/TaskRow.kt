package com.rosyidputrar.taskify.feature.tasks.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.dueLabel
import com.rosyidputrar.taskify.core.store.tasks.Progress
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.store.tasks.TaskList
import kotlinx.coroutines.launch

/** Lingkaran centang; tepinya berwarna sesuai prioritas. */
@Composable
fun TaskCheck(task: Task, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val ring = priorityColor(task.priority)
    val done = task.status == TaskStatus.Done
    val fill by animateColorAsState(if (done) ring else Color.Transparent, label = "check")
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, ring, CircleShape)
            .semantics {
                role = Role.Checkbox
                stateDescription = if (done) "Selesai" else "Belum selesai"
                contentDescription = if (done) "Tandai belum selesai" else "Tandai selesai"
            }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
    }
}

/**
 * Satu baris tugas. Geser ke kanan = selesai, geser ke kiri = pindah ke Sampah (pola SwipeToAction).
 * TalkBack mendapat aksi yang sama lewat menu aksi kustom.
 */
@Composable
fun TaskRow(
    task: Task,
    today: String,
    progress: Progress?,
    tags: List<TagRef>,
    list: TaskList?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    swipeEnabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    val tasksAccent = TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        gesturesEnabled = swipeEnabled,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggle()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val direction = state.dismissDirection
            val (color, icon, align) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(TaskifyTheme.modules.accent(TaskifyModule.Finance), Icons.Rounded.Check, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart -> Triple(MaterialTheme.colorScheme.error, Icons.Outlined.DeleteOutline, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.18f)).padding(horizontal = 24.dp), contentAlignment = align) {
                if (icon != null) Icon(icon, contentDescription = null, tint = color)
            }
        },
    ) {
        val done = task.status != TaskStatus.Todo && task.status != TaskStatus.InProgress
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClickLabel = "Buka detail", onClick = onOpen)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(if (done) "Tandai belum selesai" else "Tandai selesai") { onToggle(); true },
                        CustomAccessibilityAction("Pindahkan ke Sampah") { onDelete(); true },
                    )
                }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TaskCheck(task, onToggle, Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.status == TaskStatus.Done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val due = dueLabel(task, today)
                val overdue = task.dueDate != null && task.dueDate!! < today && !done
                val hasMeta = due != null || task.isRecurring || task.remindAt != null || list != null || tags.isNotEmpty() || (progress?.total ?: 0) > 0
                if (hasMeta) {
                    FlowRow(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (due != null) {
                            Text(due, style = MaterialTheme.typography.labelMedium, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (task.isRecurring) Icon(Icons.Outlined.Repeat, contentDescription = "Berulang", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (task.remindAt != null && !done) Icon(Icons.Outlined.NotificationsNone, contentDescription = "Ada pengingat", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (progress != null && progress.total > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProgressRing(progress.done, progress.total, tasksAccent, size = 14.dp, stroke = 2.dp)
                                Text(" ${progress.done}/${progress.total}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (list != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(namedColor(list.color), CircleShape))
                                Text(" ${list.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        tags.forEach { tag ->
                            Text("#${tag.name}", style = MaterialTheme.typography.labelMedium, color = namedColor(tag.color))
                        }
                    }
                }
            }
        }
    }
}
