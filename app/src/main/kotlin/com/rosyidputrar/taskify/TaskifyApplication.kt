package com.rosyidputrar.taskify

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.app.Application
import android.util.Log
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.UiErrors
import com.rosyidputrar.taskify.ui.shell.userMessageFor
import androidx.lifecycle.ProcessLifecycleOwner
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.sandi.SandiSession
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import com.rosyidputrar.taskify.core.reminders.ReminderScheduler
import com.rosyidputrar.taskify.di.APP_SCOPE_QUALIFIER
import com.rosyidputrar.taskify.di.appModules
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TaskifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TaskifyApplication)
            modules(appModules())
        }
        // Error yang lolos dari coroutine UI/latar → pesan singkat + log, bukan force close.
        val messenger = get<Messenger>()
        UiErrors.install { error ->
            Log.e("Taskify", "Error tak tertangani", error)
            messenger.show(userMessageFor(error))
        }
        // Kunci PIN mengikuti siklus seluruh aplikasi (latar ↔ depan), bukan per layar.
        ProcessLifecycleOwner.get().lifecycle.addObserver(get<LockManager>())
        // Brankas Sandi terkunci otomatis saat aplikasi ke latar (sesuai pengaturan kunci otomatis).
        ProcessLifecycleOwner.get().lifecycle.addObserver(get<SandiSession>())
        // Alarm pengingat mengikuti data lokal (termasuk yang tiba lewat sync dari laptop).
        get<ReminderNotifier>().ensureChannel()
        get<ReminderScheduler>().start(get(APP_SCOPE_QUALIFIER))
        // Tagihan jatuh tempo, anggaran, dan streak jurnal — tetap dicek walau Taskify tidak dibuka.
        get<com.rosyidputrar.taskify.core.reminders.ContextNotifier>().ensureChannels()
        get<com.rosyidputrar.taskify.core.reminders.ContextReminderScheduler>().schedule()
        // Mode Fokus: alarm hilang bila proses mati paksa / "Paksa berhenti" — pasang ulang dari
        // sesi tersimpan, dan pulihkan "Jangan Ganggu" bila macet menyala tanpa sesi aktif.
        get<kotlinx.coroutines.CoroutineScope>(APP_SCOPE_QUALIFIER).launch {
            runCatching { get<com.rosyidputrar.taskify.focus.FocusController>().recoverAfterRestart() }
                .onFailure { Log.w("Taskify", "Mode Fokus: pemulihan saat mulai gagal", it) }
        }
        // Perubahan yang ditolak server diumumkan (bukan hanya tercatat di Pengaturan).
        com.rosyidputrar.taskify.session.RejectedChangesNotifier(get<com.rosyidputrar.taskify.core.data.TaskifyDatabase>().watchUploadErrorCount(), messenger, get(APP_SCOPE_QUALIFIER)).start()
        // Item yang baru dibuka → shortcut launcher dinamis (tidak saat PIN aktif; dihapus saat PIN dinyalakan).
        val lock = get<LockManager>()
        com.rosyidputrar.taskify.core.designsystem.component.FocusLauncher.install { taskId ->
            com.rosyidputrar.taskify.ui.shell.AppEvents.send(com.rosyidputrar.taskify.ui.shell.AppEvent.OpenFocus(taskId))
        }
        com.rosyidputrar.taskify.core.designsystem.component.RecentEntities.install { type, id, title ->
            com.rosyidputrar.taskify.widget.EntityShortcuts.report(this, type, id, title, pinActive = lock.state.value.config != null)
        }
        get<kotlinx.coroutines.CoroutineScope>(APP_SCOPE_QUALIFIER).launchPinShortcutGuard(lock, this)
        // Widget "Hari ini" mengikuti perubahan tugas (lokal maupun dari sinkron).
        com.rosyidputrar.taskify.widget.WidgetUpdater(this, get(), get(), get(APP_SCOPE_QUALIFIER)).start()
    }
}

/** PIN dinyalakan → judul item terakhir dibuka tidak boleh terlihat di launcher. */
private fun kotlinx.coroutines.CoroutineScope.launchPinShortcutGuard(lock: LockManager, context: android.content.Context) = launch {
    lock.state.map { it.config != null }.distinctUntilChanged().collect { pin ->
        if (pin) com.rosyidputrar.taskify.widget.EntityShortcuts.clear(context)
    }
}
