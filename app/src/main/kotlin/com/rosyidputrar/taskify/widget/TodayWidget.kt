package com.rosyidputrar.taskify.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rosyidputrar.taskify.MainActivity
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext

/** Isi widget yang sudah diputuskan (tanpa membaca database di dalam komposisi). */
sealed interface TodayWidgetState {
    data object SignedOut : TodayWidgetState
    /** PIN aktif: judul tugas tidak ditampilkan di layar utama. */
    data class Locked(val count: Int) : TodayWidgetState
    data class Ready(val tasks: List<Task>, val today: String) : TodayWidgetState
}

/**
 * Widget "Hari Ini": tugas fokus (jatuh tempo hari ini & terlambat) dengan centang langsung, plus
 * tombol Tangkap. Bila kunci PIN aktif, hanya jumlahnya yang tampil — isi tetap di balik PIN.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load()
        provideContent { GlanceTheme { Content(context, state) } }
    }

    private suspend fun load(): TodayWidgetState {
        val koin = GlobalContext.getOrNull() ?: return TodayWidgetState.SignedOut
        if (koin.get<DevicePrefs>().dbOwner == null) return TodayWidgetState.SignedOut
        val today = Dates.today()
        val tasks = runCatching { koin.get<TaskRepository>().watchFocus(today, 6).first() }.getOrDefault(emptyList())
        return if (koin.get<LockManager>().state.value.config != null) TodayWidgetState.Locked(tasks.size) else TodayWidgetState.Ready(tasks, today)
    }

    @Composable
    private fun Content(context: Context, state: TodayWidgetState) {
        val open = Intent(context, MainActivity::class.java)
        val capture = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CAPTURE)
        Column(GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(14.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(open))) {
                    Text("Hari ini", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GlanceTheme.colors.onSurface))
                    Text(Dates.format(Dates.today(), "EEEE, d MMM"), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                }
                Box(
                    GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(16.dp).padding(horizontal = 14.dp, vertical = 8.dp).clickable(actionStartActivity(capture)),
                ) { Text("+ Tangkap", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)) }
            }
            Spacer(GlanceModifier.height(8.dp))
            when (state) {
                TodayWidgetState.SignedOut -> Message("Masuk ke Taskify untuk melihat tugas hari ini.", open)
                is TodayWidgetState.Locked -> Message(if (state.count == 0) "Tidak ada tugas mendesak. Taskify terkunci PIN." else "${state.count} tugas menunggu · buka Taskify untuk melihat (terkunci PIN).", open)
                is TodayWidgetState.Ready -> if (state.tasks.isEmpty()) Message("Semua beres untuk hari ini. 🎉", open) else LazyColumn {
                    items(state.tasks, itemId = { it.id.hashCode().toLong() }) { t -> TaskLine(context, t, state.today) }
                }
            }
        }
    }

    @Composable
    private fun Message(text: String, open: Intent) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant), modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(open)))
    }

    @Composable
    private fun TaskLine(context: Context, t: Task, today: String) {
        val openTask = Intent(context, MainActivity::class.java).setAction(ReminderNotifier.ACTION_OPEN_TASK).putExtra(ReminderNotifier.EXTRA_TASK_ID, t.id)
            .setData(android.net.Uri.parse("taskify://task/${t.id}")) // intent unik per tugas
        val overdue = t.dueDate != null && t.dueDate!! < today
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            CheckBox(checked = false, onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TASK_ID to t.id)))
            Spacer(GlanceModifier.width(4.dp))
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openTask))) {
                Text(t.title, maxLines = 1, style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurface))
                val meta = listOfNotNull(if (overdue) "Terlambat" else null, t.dueTime?.take(5)).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = TextStyle(fontSize = 11.sp, color = if (overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant))
            }
        }
    }

    companion object {
        val TASK_ID = ActionParameters.Key<String>("task_id")

        suspend fun refresh(context: Context) = runCatching { TodayWidget().updateAll(context) }
    }
}

/** Centang dari widget = selesaikan tugas (berulang → kejadian berikutnya dibuat seperti di aplikasi). */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TodayWidget.TASK_ID] ?: return
        val repo = GlobalContext.getOrNull()?.getOrNull<TaskRepository>() ?: return
        runCatching { repo.get(id)?.let { repo.toggle(it) } }
        TodayWidget.refresh(context)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

