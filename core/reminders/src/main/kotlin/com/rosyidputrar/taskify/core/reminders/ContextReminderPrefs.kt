package com.rosyidputrar.taskify.core.reminders

import androidx.core.content.edit
import com.rosyidputrar.taskify.core.data.DevicePrefs

/** Saklar pengingat kontekstual (per perangkat) + catatan apa yang sudah diberitahukan. */
class ContextReminderPrefs(devicePrefs: DevicePrefs) {
    private val prefs = devicePrefs.prefs

    /** Tagihan berulang (manual) yang jatuh tempo, dengan tombol Catat / Lewati. */
    var bills: Boolean
        get() = prefs.getBoolean(KEY_BILLS, true)
        set(value) = prefs.edit { putBoolean(KEY_BILLS, value) }

    /** Anggaran mencapai batas peringatan / terlampaui. */
    var budgets: Boolean
        get() = prefs.getBoolean(KEY_BUDGETS, true)
        set(value) = prefs.edit { putBoolean(KEY_BUDGETS, value) }

    /** Ingatkan menulis jurnal bila streak akan putus. */
    var journal: Boolean
        get() = prefs.getBoolean(KEY_JOURNAL, true)
        set(value) = prefs.edit { putBoolean(KEY_JOURNAL, value) }

    /** Jam pengingat jurnal (WIB, "HH:mm"). */
    var journalTime: String
        get() = prefs.getString(KEY_JOURNAL_TIME, "20:00") ?: "20:00"
        set(value) = prefs.edit { putString(KEY_JOURNAL_TIME, value) }

    /** Kunci kejadian yang sudah diberitahukan (tagihan per tanggal, anggaran per bulan & level). */
    var notified: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet(KEY_NOTIFIED, value) }

    /** Keluar akun: lupakan riwayat pemberitahuan akun lama (saklar tetap milik perangkat). */
    fun forgetHistory() = prefs.edit { remove(KEY_NOTIFIED) }

    private companion object {
        const val KEY_BILLS = "ctx_bills"
        const val KEY_BUDGETS = "ctx_budgets"
        const val KEY_JOURNAL = "ctx_journal"
        const val KEY_JOURNAL_TIME = "ctx_journal_time"
        const val KEY_NOTIFIED = "ctx_notified"
    }
}
