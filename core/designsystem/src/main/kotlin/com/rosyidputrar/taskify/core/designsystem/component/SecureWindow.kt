package com.rosyidputrar.taskify.core.designsystem.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * FLAG_SECURE selama layar ini tampil: tidak bisa di-screenshot / direkam, dan pratinjau di
 * Terkini kosong. Untuk layar yang menampilkan rahasia yang sudah didekripsi (Sandi, Konfigurasi).
 *
 * Dihitung per window: saat dua layar rahasia tersusun bersamaan (transisi navigasi), layar yang
 * keluar tidak mencabut flag milik layar yang masuk.
 */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        SecureFlag.acquire(activity.window)
        onDispose { SecureFlag.release(activity.window) }
    }
}

/** Hitungan FLAG_SECURE per window (juga dipakai di luar Compose, mis. privasi layar Terkini). */
object SecureFlag {
    private val holders = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val n = holders[window] ?: 0
        if (n == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        holders[window] = n + 1
    }

    fun release(window: Window) {
        val n = (holders[window] ?: return) - 1
        if (n <= 0) {
            holders.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            holders[window] = n
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
