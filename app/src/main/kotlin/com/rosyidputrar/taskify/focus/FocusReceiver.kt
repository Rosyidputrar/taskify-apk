package com.rosyidputrar.taskify.focus

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Kejadian sistem untuk Mode Fokus (lihat intent-filter di AndroidManifest): setelah reboot /
 * pembaruan aplikasi / ganti waktu / zona waktu / izin alarm tepat berubah, alarm AlarmManager
 * sudah hilang — [FocusController.recoverAfterRestart] memasangnya ulang dari sesi yang
 * tersimpan, dan memulihkan "Jangan Ganggu" bila macet menyala tanpa sesi aktif.
 *
 * D-03: receiver ini HANYA menangani aksi sistem. Aksi kustom (alarm akhir fase, tombol
 * notifikasi) pindah ke [FocusActionReceiver] yang tidak diekspor — sebelumnya satu receiver
 * yang diekspor ini menjalankan aksi kustom dari aplikasi mana pun tanpa memeriksa pengirim.
 */
class FocusReceiver : BroadcastReceiver(), KoinComponent {
    private val controller: FocusController by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                        controller.recoverAfterRestart()
                    // Aksi kustom sengaja diabaikan di sini: hanya FocusActionReceiver
                    // (non-ekspor) yang boleh menanganinya.
                    else -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }
}
