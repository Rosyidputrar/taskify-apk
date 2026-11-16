package com.rosyidputrar.taskify.ui.gate

import android.app.Activity
import android.os.Build
import com.rosyidputrar.taskify.core.designsystem.component.SecureFlag

/**
 * Saat PIN aplikasi aktif, pratinjau Taskify di layar Terkini dikosongkan: PIN mengunci tampilan,
 * jadi thumbnail jurnal/keuangan/tugas terakhir juga tidak boleh terlihat.
 *
 * API 33+: `setRecentsScreenshotEnabled(false)` (layar tetap bisa di-screenshot di dalam aplikasi).
 * API < 33: FLAG_SECURE dipasang saat Activity dijeda (sebelum snapshot Terkini diambil) dan
 * dicabut lagi saat kembali; dihitung bersama [SecureFlag] sehingga layar rahasia (Sandi) tidak
 * kehilangan flag-nya.
 */
class RecentsPrivacy(private val activity: Activity, private val sdk: Int = Build.VERSION.SDK_INT) {
    private var hidden = false
    private var holding = false

    fun setHidden(value: Boolean) {
        hidden = value
        if (sdk >= Build.VERSION_CODES.TIRAMISU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!value)
        }
    }

    fun onPause() {
        if (sdk < Build.VERSION_CODES.TIRAMISU && hidden && !holding) {
            SecureFlag.acquire(activity.window)
            holding = true
        }
    }

    fun onResume() {
        if (holding) {
            SecureFlag.release(activity.window)
            holding = false
        }
    }
}
