package com.rosyidputrar.taskify.core.domain.tasks

import com.rosyidputrar.taskify.core.domain.time.Dates

/** Satu alarm pengingat yang perlu dipasang di AlarmManager. */
data class ReminderAlarm(val taskId: String, val remindAt: String, val atMillis: Long)

/**
 * Hasil penyelarasan alarm: [cancel] dibatalkan, [set] dipasang (atau dipasang ulang), dan
 * [scheduled] = keadaan baru yang disimpan (`id tugas → remind_at` yang sudah ditangani).
 */
data class ReminderPlan(
    val cancel: Set<String>,
    val set: List<ReminderAlarm>,
    val scheduled: Map<String, String>,
)

/**
 * Menentukan alarm mana yang dipasang setiap kali data tugas berubah.
 *
 * Aturan utama: satu `(tugas, remind_at)` hanya dipasang SEKALI. Alarm yang waktunya sudah lewat
 * tidak pernah dipasang lagi — bila dipasang ulang, AlarmManager langsung membunyikannya, sehingga
 * pengingat yang sudah berbunyi (atau sudah ditutup pengguna) muncul lagi di setiap perubahan tabel.
 */
object ReminderPlanner {
    /** Pengingat yang baru tiba (mis. lewat sinkron) sedikit terlambat tetap dibunyikan sekali. */
    const val GRACE_MS = 60_000L

    /** Nilai `remind_at` tidak diketahui (data lama yang hanya menyimpan id). */
    const val UNKNOWN = ""

    /**
     * @param candidates `id → remind_at` tugas aktif yang punya pengingat.
     * @param scheduled keadaan sebelumnya (`id → remind_at` yang sudah ditangani).
     * @param force pasang ulang semua alarm yang akan datang walau tidak berubah (setelah reboot,
     *   ganti zona waktu, atau izin alarm tepat berubah — saat itu AlarmManager sudah kosong).
     */
    fun plan(
        candidates: Map<String, String>,
        scheduled: Map<String, String>,
        nowMs: Long,
        force: Boolean = false,
    ): ReminderPlan {
        val set = mutableListOf<ReminderAlarm>()
        val next = mutableMapOf<String, String>()
        for ((id, remindAt) in candidates) {
            val at = Dates.parseTimestampMillis(remindAt) ?: continue
            val previous = scheduled[id]
            val unchanged = previous == remindAt
            val isNew = previous == null || (previous != remindAt && previous != UNKNOWN)
            val shouldSet = when {
                at > nowMs -> force || !unchanged
                // Baru dibuat/diubah dan jatuh tempo barusan → bunyikan sekali sekarang.
                at > nowMs - GRACE_MS -> isNew
                else -> false
            }
            if (shouldSet) set += ReminderAlarm(id, remindAt, at)
            next[id] = remindAt
        }
        // Tugas selesai/dihapus/pengingatnya dikosongkan → alarm yang mungkin masih menunggu dibatalkan.
        val cancel = scheduled.keys - candidates.keys
        return ReminderPlan(cancel, set, next)
    }
}
