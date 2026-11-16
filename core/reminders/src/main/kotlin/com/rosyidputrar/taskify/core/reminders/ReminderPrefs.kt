package com.rosyidputrar.taskify.core.reminders

import androidx.core.content.edit
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.tasks.ReminderPlanner

/** Preferensi notifikasi per perangkat. */
class ReminderPrefs(devicePrefs: DevicePrefs) {
    private val prefs = devicePrefs.prefs

    /** "Sembunyikan isi notifikasi": hanya tertulis "Ada pengingat tugas". */
    var hideContent: Boolean
        get() = prefs.getBoolean(KEY_HIDE, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE, value) }

    /**
     * Alarm yang sudah ditangani: `id tugas → remind_at`. Dipakai untuk membatalkan yang sudah tidak
     * relevan dan agar `(tugas, remind_at)` yang sama tidak dipasang (dan berbunyi) dua kali.
     * Data versi lama hanya menyimpan id → nilainya [ReminderPlanner.UNKNOWN].
     */
    var scheduled: Map<String, String>
        get() {
            val entries = prefs.getStringSet(KEY_SCHEDULED_AT, null)
            if (entries != null) {
                return entries.mapNotNull { e -> e.indexOf(SEP).takeIf { it > 0 }?.let { e.substring(0, it) to e.substring(it + 1) } }.toMap()
            }
            return prefs.getStringSet(KEY_SCHEDULED_LEGACY, emptySet()).orEmpty().associateWith { ReminderPlanner.UNKNOWN }
        }
        set(value) = prefs.edit {
            putStringSet(KEY_SCHEDULED_AT, value.map { (id, at) -> "$id$SEP$at" }.toSet())
            remove(KEY_SCHEDULED_LEGACY)
        }

    private companion object {
        const val KEY_HIDE = "notif_hide_content"
        const val KEY_SCHEDULED_LEGACY = "reminder_scheduled_ids"
        const val KEY_SCHEDULED_AT = "reminder_scheduled_at"
        const val SEP = '|'
    }
}
