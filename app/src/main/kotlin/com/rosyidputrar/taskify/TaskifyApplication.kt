package com.rosyidputrar.taskify

import android.app.Application
import com.rosyidputrar.taskify.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TaskifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TaskifyApplication)
            modules(appModule)
        }
    }
}
