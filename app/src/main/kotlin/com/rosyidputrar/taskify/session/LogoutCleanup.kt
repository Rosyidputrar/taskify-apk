package com.rosyidputrar.taskify.session

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.reminders.ContextReminderScheduler
import com.rosyidputrar.taskify.ui.share.ShareInbox
import com.rosyidputrar.taskify.widget.TodayWidget
import kotlinx.coroutines.CancellationException
import java.io.File

/** Satu langkah keluar akun; [label] dipakai untuk log & pesan bila gagal. */
internal class LogoutStep(val label: String, val run: suspend () -> Unit)

/**
 * Jalankan SEMUA langkah walau ada yang gagal: satu error (mis. SQLite sibuk) tidak boleh membuat
 * sesi tetap masuk atau sisa data lain tertinggal. Mengembalikan label langkah yang gagal.
 */
internal suspend fun runLogoutSteps(steps: List<LogoutStep>): List<String> {
    val failed = mutableListOf<String>()
    for (step in steps) {
        try {
            step.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("Taskify", "Keluar: langkah '${step.label}' gagal", e)
            failed += step.label
        }
    }
    return failed
}

/** Sisa akun di perangkat di luar database: notifikasi, kotak masuk Bagikan, PIN, widget. */
class DeviceLeftovers(
    private val context: Context,
    private val shareInbox: ShareInbox,
    private val lock: LockManager,
    private val captureDraft: com.rosyidputrar.taskify.ui.capture.CaptureDraft? = null,
    private val contextPrefs: com.rosyidputrar.taskify.core.reminders.ContextReminderPrefs? = null,
    private val focus: com.rosyidputrar.taskify.focus.FocusController? = null,
    /** Bisa diganti di tes; default membatalkan pekerjaan unik pengingat kontekstual. */
    private val cancelContextWork: (Context) -> Unit = ::cancelContextReminderWork,
    /** Notifikasi & Kesehatan Digital: preferensi (pengecualian, pemetaan dompet) + pekerjaan latar. */
    private val devicePrefs: com.rosyidputrar.taskify.core.device.DeviceModulePrefs? = null,
    private val deviceScheduler: com.rosyidputrar.taskify.core.device.DeviceScheduler? = null,
    /** Asisten AI: hentikan jawaban + hapus pengaturan & kunci API (riwayat chat ada di tabel lokal). */
    private val aiWipe: (() -> Unit)? = null,
) {
    suspend fun clear() {
        // Notifikasi pengingat yang masih tampil memuat judul tugas akun lama.
        NotificationManagerCompat.from(context).cancelAll()
        // Pekerjaan latar pengingat kontekstual milik akun lama (cek 6 jam + nudge jurnal):
        // tanpa ini nudge jurnal menjadwalkan ulang dirinya sendiri walau sudah keluar.
        cancelContextWork(context)
        // Berkas/teks yang dibagikan ke Taskify tetapi belum diproses.
        shareInbox.clear()
        // Draf sheet Tangkap yang belum disimpan.
        captureDraft?.clear()
        // Riwayat "tagihan/anggaran sudah diberitahukan" milik akun lama.
        contextPrefs?.forgetHistory()
        // Sesi Mode Fokus (judul tugas akun lama di notifikasi, alarm, Jangan Ganggu yang kita nyalakan).
        focus?.clear()
        // Notifikasi & Kesehatan Digital: isinya ada di tabel lokal (ikut terhapus bersama database);
        // di sini preferensinya (pemetaan dompet, pengecualian) dan pekerjaan salin data tiap jam.
        devicePrefs?.clear()
        deviceScheduler?.cancel()
        aiWipe?.invoke()
        File(context.cacheDir, SHARE_UPLOAD_DIR).deleteRecursively()
        // PIN dibuat oleh akun lama; akun berikutnya membuat PIN-nya sendiri.
        lock.disable()
        // Shortcut launcher berisi judul catatan/jurnal/tugas akun lama.
        com.rosyidputrar.taskify.widget.EntityShortcuts.clear(context)
        TodayWidget.refresh(context)
    }

    companion object {
        /** Salinan sementara dari Bagikan → Berkas (dihapus setelah unggah selesai). */
        const val SHARE_UPLOAD_DIR = "share-upload"
    }
}

/** Batalkan semua pekerjaan unik pengingat kontekstual (cek berkala, cek segera, nudge jurnal). */
internal fun cancelContextReminderWork(context: Context) {
    val wm = WorkManager.getInstance(context)
    wm.cancelUniqueWork(ContextReminderScheduler.WORK_CHECK)
    wm.cancelUniqueWork(ContextReminderScheduler.WORK_CHECK_NOW)
    wm.cancelUniqueWork(ContextReminderScheduler.WORK_JOURNAL)
}
