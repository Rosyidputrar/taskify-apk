package com.rosyidputrar.taskify.di

import com.rosyidputrar.taskify.feature.wellbeing.wellbeingFeatureModule
import com.rosyidputrar.taskify.feature.notifications.notificationsFeatureModule
import com.rosyidputrar.taskify.core.device.deviceDataModule
import com.rosyidputrar.taskify.ui.focus.FocusViewModel
import com.rosyidputrar.taskify.focus.FocusController
import com.rosyidputrar.taskify.core.store.focus.FocusRepository
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
import com.rosyidputrar.taskify.feature.finance.financeFeatureModule
import com.rosyidputrar.taskify.feature.vault.vaultFeatureModule
import com.rosyidputrar.taskify.feature.attachments.attachmentsFeatureModule
import com.rosyidputrar.taskify.feature.sandi.AccountEmail
import com.rosyidputrar.taskify.feature.files.FilesConfig
import com.rosyidputrar.taskify.feature.files.OnlineStatus
import com.rosyidputrar.taskify.feature.files.filesFeatureModule
import com.rosyidputrar.taskify.feature.review.reviewFeatureModule
import com.rosyidputrar.taskify.core.store.review.ReviewRepository
import com.rosyidputrar.taskify.core.store.search.SearchRepository
import com.rosyidputrar.taskify.core.store.cross.LinkRepository
import com.rosyidputrar.taskify.core.store.cross.TrashRepository
import com.rosyidputrar.taskify.feature.links.EntityOpener
import com.rosyidputrar.taskify.feature.links.linksFeatureModule
import com.rosyidputrar.taskify.feature.organize.organizeFeatureModule
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import com.rosyidputrar.taskify.ui.share.ShareInbox
import com.rosyidputrar.taskify.core.media.MediaSaver
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.YouTubeMusicClient
import com.rosyidputrar.taskify.core.media.lyrics.LyricsRepository
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import com.rosyidputrar.taskify.core.media.youtube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.youtube.FailedStreamClients
import com.rosyidputrar.taskify.core.media.youtube.PoTokenEnsurer
import com.rosyidputrar.taskify.core.media.youtube.YoutubeiStreamResolver
import com.rosyidputrar.taskify.core.media.youtube.potoken.BotGuardTokenGenerator
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginRepository
import com.rosyidputrar.taskify.core.media.youtube.SignatureTimestampProvider
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.core.media.youtube.VisitorDataBootstrap
import com.rosyidputrar.taskify.core.media.youtube.YouTubeAuthStore
import com.rosyidputrar.taskify.core.media.TikTokClient
import com.rosyidputrar.taskify.feature.media.OnlineFlow
import com.rosyidputrar.taskify.feature.media.TikTokConfig
import com.rosyidputrar.taskify.feature.media.mediaFeatureModule
import com.rosyidputrar.taskify.ui.share.ShareViewModel
import com.rosyidputrar.taskify.core.files.TempFileUploader
import com.rosyidputrar.taskify.core.files.TempFilesApi
import com.rosyidputrar.taskify.core.store.files.TempFileRepository
import io.github.jan.supabase.SupabaseClient
import com.rosyidputrar.taskify.feature.sandi.sandiFeatureModule
import com.rosyidputrar.taskify.core.sandi.KeystoreTrustStore
import com.rosyidputrar.taskify.core.sandi.ActivityDeviceAuthenticator
import com.rosyidputrar.taskify.core.sandi.DeviceAuthenticator
import org.koin.android.ext.koin.androidApplication
import com.rosyidputrar.taskify.core.sandi.SandiPrefs
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiSession
import com.rosyidputrar.taskify.core.sandi.SecureClipboard
import com.rosyidputrar.taskify.core.sandi.SupabaseVaultServer
import com.rosyidputrar.taskify.core.sandi.TrustStore
import com.rosyidputrar.taskify.core.sandi.VaultServer
import com.rosyidputrar.taskify.core.store.sandi.ConfigRepository
import com.rosyidputrar.taskify.core.store.sandi.SandiRepository
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import com.rosyidputrar.taskify.core.files.AttachmentManager
import com.rosyidputrar.taskify.core.files.FileStore
import com.rosyidputrar.taskify.core.files.FilesApi
import com.rosyidputrar.taskify.core.files.Transfers
import com.rosyidputrar.taskify.session.AttachmentOutbox
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.session.RecurringProcessor
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import com.rosyidputrar.taskify.core.designsystem.component.UiErrors
import com.rosyidputrar.taskify.core.data.RejectedChangeRetrier
import com.rosyidputrar.taskify.session.DeviceLeftovers
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
    // Error yang lolos dilaporkan (snackbar + log), bukan force close.
    single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default + UiErrors.handler) }
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
    single { FinanceRepository(get(), get()) }
    single { AttachmentRepository(get(), get()) }
    single { VaultRepository(get(), get()) }
    single { SandiRepository(get(), get()) }
    single { ConfigRepository(get(), get()) }
    single { SandiPrefs(androidContext()) }
    single { SecureClipboard(androidContext(), get(APP_SCOPE)) }
    single<TrustStore> { KeystoreTrustStore(androidApplication()) }
    single<DeviceAuthenticator>(createdAtStart = true) { ActivityDeviceAuthenticator(androidApplication()) }
    single { SandiSession(get(), get(), get(APP_SCOPE)) }
    single { SandiService(get(), get(), get(), get(), get(), getOrNull<VaultServer>(), get(), get(), get(), get(APP_SCOPE)) }
    single { AccountEmail { getOrNull<AuthRepository>()?.currentEmail() } }
    single { ReviewRepository(get()) }
    single { SearchRepository(get()) }
    single { TrashRepository(get()) }
    single { com.rosyidputrar.taskify.core.store.cross.ExportRepository(get()) }
    single { ShareInbox(androidContext()) }
    single { com.rosyidputrar.taskify.ui.capture.CaptureDraft(androidContext()) }
    single { com.rosyidputrar.taskify.autofill.AutofillVault(get()) }
    single { FocusRepository(get()) }
    single {
        val history = get<FocusRepository>()
        val reminderPrefs = get<com.rosyidputrar.taskify.core.reminders.ReminderPrefs>()
        FocusController(
            androidContext(),
            record = { r -> history.record(r.taskId, r.taskTitle, r.day, r.startedAt, r.endedAt, r.minutes) },
            hideContent = { reminderPrefs.hideContent },
        )
    }
    single {
        val reminderPrefs = get<ReminderPrefs>()
        com.rosyidputrar.taskify.core.device.DraftAlerts(androidContext(), hideContent = { reminderPrefs.hideContent })
    }
    single {
        DeviceLeftovers(
            androidContext(), get(), get(), get(), get(), get(),
            devicePrefs = get(), deviceScheduler = get(),
        )
    }
    // Musik direct (Tahap 7): tidak ada lagi klien Worker — katalog/lirik/
    // SponsorBlock lewat fasad YouTubeMusicClient. MUSIC_API_URL tidak lagi
    // dipakai musik (secret GitHub-nya dipertahankan untuk rollback, lihat
    // AppConfig); Supabase tetap dipakai auth/database, bukan musik.
    // Porting ArchiveTune Tahap 2 (C1-8, C2-13): resolver memakai penyedia
    // signatureTimestamp (base.js) + memastikan visitorData siap sebelum
    // resolve anonim. Tanpa keduanya perilaku sama seperti sebelumnya.
    single { SignatureTimestampProvider() }
    // Porting ArchiveTune Tahap 7 (C2-19): blacklist klien stream yang gagal.
    single { FailedStreamClients() }
    // Porting ArchiveTune Tahap 5 (C1-1..5): mesin mint PoToken BotGuard.
    // initialize() sekali di sini (butuh context aplikasi untuk WebView).
    single {
        PoTokenEnsurer().also {
            BotGuardTokenGenerator.initialize(androidContext())
        }
    }
    single {
        DirectStreamResolver(
            signatureTimestampProvider = { client, videoId ->
                get<SignatureTimestampProvider>().signatureTimestamp(client, videoId)
            },
            ensureVisitorData = { get<VisitorDataBootstrap>().ensureVisitorData() },
            poTokenEnsurer = get(),
            failedStreamClients = get(),
        )
    }
    single { SponsorBlockRepository() }
    single { LyricsRepository(youTube = get()) }
    single {
        YouTubeMusicClient(get(), get(), get()) { get<YouTubeAuthStore>().loadAuthState() }
    }
    // Porting ArchiveTune Tahap 3 (C1-6): resolver youtubei.js (QuickJS).
    // Malas dibuat — QuickJS baru dibuat saat pertama resolve, bukan saat
    // aplikasi start. PoToken dipastikan lewat ensurer Tahap 5.
    single { YoutubeiStreamResolver(androidContext(), poTokenEnsurer = get()) }
    single {
        MusicPlayer(
            androidContext(), get(), get(), loginEvents = get<MusicLoginRepository>(),
            youtubeiResolver = get(),
            failedStreamClients = get(),
            poTokenEnsurer = get(),
            refreshVisitorData = { get<VisitorDataBootstrap>().refreshVisitorData() },
            // Tahap 9 (C2-21): telemetri putar best-effort saat lagu mulai berbunyi.
            registerPlayback = { _, trackingUrl, auth -> get<YouTube>().registerPlayback(trackingUrl, authState = auth).isSuccess },
            // Agen B (tunggu-jaringan): observer koneksi yang sama dengan
            // OnlineFlow di bawah (NetworkMonitor).
            onlineFlow = get<NetworkMonitor>().online,
        ).also { player ->
            // Pesan pemutar (lewati sponsor, pewaktu tidur) tampil sebagai snackbar aplikasi, sekali saja.
            get<CoroutineScope>(APP_SCOPE).launch { player.notice.filterNotNull().collect { get<Messenger>().show(it) } }
        }
    }
    // Musik login (PLAN C.5/C.6): repo login + hook kedaluwarsa pemutar.
    single { MusicLoginRepository(get(), get()) }
    // Musik direct (Tahap 5): auth anonim — visitorData tersimpan di
    // SharedPreferences "youtube_auth", di-bootstrap sekali saat start dari
    // TaskifyApplication. Login/PoToken belum ada (follow-up, PLAN G.10).
    single { YouTube() }
    single { YouTubeAuthStore(androidContext()) }
    single { VisitorDataBootstrap(get(), get()) }
    // TikTok: Worker hanya melayani Origin Taskify → dikirim eksplisit dari PUBLIC_WEB_ORIGIN.
    single { TikTokClient(AppConfig.tiktokApiUrl, AppConfig.publicWebOrigin) }
    single { TikTokConfig(AppConfig.publicWebOrigin) }
    single { MediaSaver(androidContext()) }
    single { OnlineFlow { get<NetworkMonitor>().online } }
    single { LinkRepository(get(), get()) }
    single { EntityOpener { type, id -> AppEvents.send(AppEvent.OpenEntity(type, id)) } }
    single { TempFileRepository(get()) }
    // Tanpa SupabaseClient (konfigurasi belum lengkap) halaman unduh publik tetap bisa memakai URL-nya.
    single { TempFilesApi(getOrNull<SupabaseClient>(), AppConfig.supabaseUrl) }
    single { TempFileUploader(androidContext(), get(), get(), get(APP_SCOPE)) }
    single { FilesConfig(AppConfig.publicWebOrigin) }
    single { OnlineStatus { get<NetworkMonitor>().online } }
    single { FileStore(androidContext()) }
    single { Transfers() }
    single { ReminderPrefs(get()) }
    single { ReminderNotifier(androidContext(), get()) }
    single { ReminderScheduler(androidContext(), get(), get()) }
    single { com.rosyidputrar.taskify.core.reminders.ContextReminderPrefs(get()) }
    single { com.rosyidputrar.taskify.core.reminders.ContextNotifier(androidContext(), get()) }
    single { com.rosyidputrar.taskify.core.reminders.ContextReminderScheduler(androidContext(), get()) }
    viewModel { params -> FocusViewModel(params.getOrNull(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get<TaskifyDatabase>().watchUploadErrorCount(), get(), get(), getOrNull()) }
    viewModel { CaptureViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AppViewModel(get()) }
    viewModel { ShareViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { GateViewModel(getOrNull(), getOrNull(), get()) }
    viewModel { SettingsViewModel(getOrNull(), getOrNull(), get(), get(), get(), get(), get(), get(), get(), get(), get(), androidContext(), getOrNull(), get(), get()) }
}

/** Bagian yang butuh server (Supabase + PowerSync). Hanya dimuat bila konfigurasi lengkap. */
private val serverModule = module {
    single { createTaskifySupabase(serverConfig) }
    single { SupabaseConnector(get(), serverConfig.powerSyncUrl) }
    single { RejectedChangeRetrier(get<TaskifyDatabase>(), get<SupabaseConnector>()) }
    single { AuthRepository(get(), get(), get(), get(APP_SCOPE)) }
    single { FilesApi(get()) }
    single<VaultServer> { SupabaseVaultServer(get(), AppConfig.supabaseUrl) }
    single { AttachmentManager(androidContext(), get(), get(), get(), get(), get(APP_SCOPE)) }
    single(createdAtStart = true) { SessionCoordinator(get(), get(), get(), get(), get(), get(), get(APP_SCOPE), get(), get(), get(), get(), get(), get(), get()) }
    single(createdAtStart = true) { AttachmentOutbox(get(), get(), get(), get(APP_SCOPE)) }
    single(createdAtStart = true) { RecurringProcessor(get(), get(), get(), get(), get(APP_SCOPE)) }
}

fun appModules(): List<Module> =
    listOf(deviceModule, authFeatureModule, tasksFeatureModule, calendarFeatureModule, journalFeatureModule, notesFeatureModule, financeFeatureModule, vaultFeatureModule, attachmentsFeatureModule, sandiFeatureModule, filesFeatureModule, reviewFeatureModule, linksFeatureModule, organizeFeatureModule, mediaFeatureModule, deviceDataModule, notificationsFeatureModule, wellbeingFeatureModule) + if (serverConfig.isComplete) listOf(serverModule) else emptyList()
