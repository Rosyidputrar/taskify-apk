package com.rosyidputrar.taskify.core.sandi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.rosyidputrar.taskify.core.domain.sandi.CLIPBOARD_CLEAR_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Salin rahasia lalu hapus otomatis setelah 30 detik (dan saat brankas dikunci). Ditandai
 * sensitif sehingga Android 13+ tidak menampilkan isinya di pratinjau papan klip.
 */
class SecureClipboard(private val context: Context, private val scope: CoroutineScope) {
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var pending: String? = null
    private var timer: Job? = null

    fun copy(label: String, text: String, secret: Boolean = true, clearAfterMs: Long = CLIPBOARD_CLEAR_MS): Boolean = try {
        val clip = ClipData.newPlainText(label, text)
        if (secret && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        clipboard.setPrimaryClip(clip)
        if (secret) {
            pending = text
            timer?.cancel()
            timer = scope.launch(Dispatchers.Main) {
                delay(clearAfterMs)
                clearIfOurs()
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    /** Hapus sekarang bila masih ada rahasia yang menunggu (dipanggil saat brankas dikunci). */
    fun clearNow() {
        timer?.cancel()
        scope.launch(Dispatchers.Main) { clearIfOurs() }
    }

    private fun clearIfOurs() {
        val ours = pending ?: return
        pending = null
        try {
            // Jangan menimpa sesuatu yang disalin pengguna sesudahnya (bila isi papan klip terbaca).
            val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (current != null && current != ours) return
            if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (_: Exception) {
            // Papan klip tidak bisa dibaca di latar: hapus saja.
            runCatching { if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip() }
        }
    }
}
