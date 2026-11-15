package com.rosyidputrar.taskify.di

import com.rosyidputrar.taskify.AppConfig
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.KeystorePinMac
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.auth.LoginNameStore
import com.rosyidputrar.taskify.core.auth.MfaTrustStore
import com.rosyidputrar.taskify.core.auth.NetworkMonitor
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.data.ServerConfig
import com.rosyidputrar.taskify.core.data.SupabaseConnector
import com.rosyidputrar.taskify.core.data.TaskifyDatabase
import com.rosyidputrar.taskify.core.data.createTaskifySupabase
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.feature.auth.authFeatureModule
import com.rosyidputrar.taskify.feature.tasks.tasksFeatureModule
import com.rosyidputrar.taskify.feature.calendar.calendarFeatureModule
import com.rosyidputrar.taskify.feature.journal.journalFeatureModule
import com.rosyidputrar.taskify.feature.notes.notesFeatureModule
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
import com.rosyidputrar.taskify.core.store.calendar.CalendarRepository
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import com.rosyidputrar.taskify.core.reminders.ReminderPrefs
import com.rosyidputrar.taskify.core.reminders.ReminderScheduler
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.home.HomeRepository
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.ui.capture.CaptureViewModel
import com.rosyidputrar.taskify.ui.home.HomeViewModel
import com.rosyidputrar.taskify.session.SessionCoordinator
import com.rosyidputrar.taskify.ui.gate.GateViewModel
import com.rosyidputrar.taskify.ui.settings.SettingsViewModel
import com.rosyidputrar.taskify.ui.shell.AppViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Juga dipakai modul fitur lewat `named("appScope")` (simpan otomatis editor). */
private val APP_SCOPE = named("appScope")

internal val APP_SCOPE_QUALIFIER = APP_SCOPE

val serverConfig = ServerConfig(AppConfig.supabaseUrl, AppConfig.supabasePublishableKey, AppConfig.powerSyncUrl)

/** Bagian yang selalu ada, termasuk saat konfigurasi server belum diisi. */
private val deviceModule = module {
    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { AppPreferences(androidContext()) }
    single { DevicePrefs(androidContext()) }
    single { NetworkMonitor(androidContext()) }
    single { MfaTrustStore(get()) }
    single { LoginNameStore(get()) }
    single { LockManager(get(), KeystorePinMac()) }
    single { TaskifyDatabase(androidContext(), get(APP_SCOPE), get()) }
    single { get<TaskifyDatabase>().db }
    single<UserIdProvider> { UserIdProvider { get<DevicePrefs>().dbOwner } }
    single { Messenger() }
    single { TaskRepository(get(), get()) }
    single { TagRepository(get(), get()) }
    single { HomeRepository(get()) }
    single { CalendarRepository(get()) }
    single { JournalRepository(get(), get()) }
    single { NoteRepository(get(), get()) }
    single { ReminderPrefs(get()) }
    single { ReminderNotifier(androidContext(), get()) }
    single { ReminderScheduler(androidContext(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get()) }
    viewModel { CaptureViewModel(get(), get(), get(), get(), get()) }
    viewModel { AppViewModel(get()) }
    viewModel { GateViewModel(getOrNull(), getOrNull(), get()) }
    viewModel { SettingsViewModel(getOrNull(), getOrNull(), get(), get(), get(), get(), get(), get(), get()) }
}

/** Bagian yang butuh server (Supabase + PowerSync). Hanya dimuat bila konfigurasi lengkap. */
private val serverModule = module {
    single { createTaskifySupabase(serverConfig) }
    single { SupabaseConnector(get(), serverConfig.powerSyncUrl) }
    single { AuthRepository(get(), get(), get(), get(APP_SCOPE)) }
    single(createdAtStart = true) { SessionCoordinator(get(), get(), get(), get(), get(), get(), get(APP_SCOPE)) }
}

fun appModules(): List<Module> =
    listOf(deviceModule, authFeatureModule, tasksFeatureModule, calendarFeatureModule, journalFeatureModule, notesFeatureModule) + if (serverConfig.isComplete) listOf(serverModule) else emptyList()
