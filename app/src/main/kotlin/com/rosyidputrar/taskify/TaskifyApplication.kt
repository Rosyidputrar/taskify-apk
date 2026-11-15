package com.rosyidputrar.taskify

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.rosyidputrar.taskify.core.auth.LockManager
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
    }
}
