package com.rosyidputrar.taskify.ui.capture

import android.content.Context
import androidx.core.content.edit

/**
 * Draf sheet Tangkap. Teks yang sedang diketik tidak boleh hilang karena sheet tertutup tanpa
 * sengaja (sentuh latar, geser ke bawah, tombol kembali) atau proses dimatikan sistem. Disimpan
 * di perangkat, dikosongkan setelah berhasil disimpan dan saat keluar akun.
 */
class CaptureDraft(context: Context) {
    private val prefs = context.getSharedPreferences("taskify_capture_draft", Context.MODE_PRIVATE)

    var text: String
        get() = prefs.getString(KEY, "").orEmpty()
        set(value) = prefs.edit { if (value.isBlank()) remove(KEY) else putString(KEY, value) }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY = "text"
    }
}
