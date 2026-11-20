package com.rosyidputrar.taskify.core.device

import com.rosyidputrar.taskify.core.store.device.NotifLogCrypto
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.device.UsageRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Koin: semua bagian modul Notifikasi & Kesehatan Digital yang bukan UI. */
val deviceDataModule = module {
    single<NotifLogCrypto> { notifLogCrypto() }
    single { NotificationLogRepository(get(), get()) }
    single { UsageRepository(get()) }
    single { DeviceModulePrefs(androidContext()) }
    single { AppInfo(androidContext()) }
    single { UsageCollector(androidContext(), get(), get(), get(), get()) }
    single { DeviceScheduler(androidContext()) }
    single { DeviceSummary(get(), get(), get()) }
    single<AccessProbe> { AndroidAccessProbe(androidContext()) }
}
