package com.rosyidputrar.taskify

import android.app.Application
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
        // Kunci PIN mengikuti siklus seluruh aplikasi (latar ↔ depan), bukan per layar.
        ProcessLifecycleOwner.get().lifecycle.addObserver(get<LockManager>())
        // Brankas Sandi terkunci otomatis saat aplikasi ke latar (sesuai pengaturan kunci otomatis).
        ProcessLifecycleOwner.get().lifecycle.addObserver(get<SandiSession>())
        // Alarm pengingat mengikuti data lokal (termasuk yang tiba lewat sync dari laptop).
        get<ReminderNotifier>().ensureChannel()
        get<ReminderScheduler>().start(get(APP_SCOPE_QUALIFIER))
        // Widget "Hari ini" mengikuti perubahan tugas (lokal maupun dari sinkron).
        com.rosyidputrar.taskify.widget.WidgetUpdater(this, get(), get(), get(APP_SCOPE_QUALIFIER)).start()
    }
}
