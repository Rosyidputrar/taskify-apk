package com.rosyidputrar.taskify.core.reminders

import androidx.core.content.edit
import com.rosyidputrar.taskify.core.data.DevicePrefs

/** Preferensi notifikasi per perangkat. */
class ReminderPrefs(devicePrefs: DevicePrefs) {
    private val prefs = devicePrefs.prefs

    /** "Sembunyikan isi notifikasi": hanya tertulis "Ada pengingat tugas". */
    var hideContent: Boolean
        get() = prefs.getBoolean(KEY_HIDE, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE, value) }

    /** Id tugas yang alarmnya sedang terpasang (untuk membatalkan yang sudah tidak relevan). */
    var scheduledIds: Set<String>
        get() = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_SCHEDULED, value) }

    private companion object {
        const val KEY_HIDE = "notif_hide_content"
        const val KEY_SCHEDULED = "reminder_scheduled_ids"
    }
}
