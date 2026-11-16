package com.rosyidputrar.taskify.session

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.rosyidputrar.taskify.core.auth.LockManager
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
class DeviceLeftovers(private val context: Context, private val shareInbox: ShareInbox, private val lock: LockManager) {
    suspend fun clear() {
        // Notifikasi pengingat yang masih tampil memuat judul tugas akun lama.
        NotificationManagerCompat.from(context).cancelAll()
        // Berkas/teks yang dibagikan ke Taskify tetapi belum diproses.
        shareInbox.clear()
        File(context.cacheDir, SHARE_UPLOAD_DIR).deleteRecursively()
        // PIN dibuat oleh akun lama; akun berikutnya membuat PIN-nya sendiri.
        lock.disable()
        TodayWidget.refresh(context)
    }

    companion object {
        /** Salinan sementara dari Bagikan → Berkas (dihapus setelah unggah selesai). */
        const val SHARE_UPLOAD_DIR = "share-upload"
    }
}
