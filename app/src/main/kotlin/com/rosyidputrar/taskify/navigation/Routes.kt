package com.rosyidputrar.taskify.navigation

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
/** Kalender; [date] memilih tanggal tertentu (dari strip minggu Beranda, shortcut, dsb.). */
@Serializable data class CalendarRoute(val date: String? = null)
@Serializable data object RoomRoute
@Serializable data object SettingsRoute

/** Tugas; [taskId] membuka detail tugas itu (dari notifikasi, Beranda, dsb.). */
@Serializable data class TasksRoute(val taskId: String? = null)

@Serializable data object JournalRoute

/**
 * Editor jurnal: [id] = entri yang ada; tanpa id = draf baru di [date] (default hari ini).
 * [template] "week"/"month" + [from]/[to] = templat refleksi dari Review.
 */
@Serializable data class JournalEntryRoute(
    val id: String? = null,
    val date: String? = null,
    val template: String? = null,
    val from: String? = null,
    val to: String? = null,
)

@Serializable data object NotesRoute

/** Editor catatan: [id] = catatan yang ada; tanpa id = catatan baru, ditempel ke [date] bila diisi. */
@Serializable data class NoteEditRoute(val id: String? = null, val date: String? = null)

@Serializable data object FinanceRoute

/** Editor transaksi: [id] = transaksi yang ada; tanpa id = baru di [date] / dompet [walletId]. */
@Serializable data class TransactionRoute(val id: String? = null, val date: String? = null, val walletId: String? = null)

@Serializable data object VaultRoute

/** Editor snippet: [id] null = snippet baru. */
@Serializable data class SnippetRoute(val id: String? = null)

@Serializable data class ProjectRoute(val id: String)

/** Isi arsip ZIP satu versi project. */
@Serializable data class ZipRoute(val attachmentId: String)

/** Berkas konfigurasi terenkripsi (Vault › Konfig). */
@Serializable data class ConfigFileRoute(val id: String)

/** Editor konfigurasi: [fileId] null = berkas baru (opsional tertaut [projectId]); selain itu versi baru. */
@Serializable data class ConfigEditRoute(val fileId: String? = null, val projectId: String? = null)

@Serializable data object SandiRoute

@Serializable data object FilesRoute

@Serializable data object ReviewRoute

@Serializable data object TagsRoute

@Serializable data class TagRoute(val id: String)

@Serializable data object TrashRoute

@Serializable data object ShareRoute

/** Mode Fokus (Pomodoro); [taskId] = tugas yang disiapkan bila belum ada sesi berjalan. */
/** Modul Notifikasi; [app] = saring satu aplikasi, [drafts] = langsung buka draf transaksi. */
@Serializable data class NotificationsRoute(val app: String? = null, val drafts: Boolean = false)

/** Modul Kesehatan Digital. */
@Serializable data object WellbeingRoute

@Serializable data class FocusRoute(val taskId: String? = null)

@Serializable data object MusicRoute

/** Pengunduh TikTok; [input] = link yang langsung diambil (dari Bagikan). */
@Serializable data class TikTokRoute(val input: String? = null)

/** Modul yang layarnya belum dibangun memakai rute generik ini sampai tahapnya tiba. */
@Serializable data class ModuleRoute(val moduleName: String) {
    val module: TaskifyModule get() = TaskifyModule.valueOf(moduleName)
}

fun TaskifyModule.route(): Any = when (this) {
    TaskifyModule.Home -> HomeRoute
    TaskifyModule.Calendar -> CalendarRoute()
    TaskifyModule.Settings -> SettingsRoute
    TaskifyModule.Tasks -> TasksRoute()
    TaskifyModule.Journal -> JournalRoute
    TaskifyModule.Notes -> NotesRoute
    TaskifyModule.Finance -> FinanceRoute
    TaskifyModule.Vault -> VaultRoute
    TaskifyModule.Sandi -> SandiRoute
    TaskifyModule.Files -> FilesRoute
    TaskifyModule.Review -> ReviewRoute
    TaskifyModule.Tags -> TagsRoute
    TaskifyModule.Trash -> TrashRoute
    TaskifyModule.Music -> MusicRoute
    TaskifyModule.TikTok -> TikTokRoute()
    TaskifyModule.Notifications -> NotificationsRoute()
    TaskifyModule.Wellbeing -> WellbeingRoute
    else -> ModuleRoute(name)
}
