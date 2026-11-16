package com.rosyidputrar.taskify.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preferensi per perangkat yang TIDAK disinkron dan dibaca sebelum database dibuka:
 * pemilik database, kepercayaan MFA, nama untuk layar masuk, dan kunci PIN.
 * File ini dikecualikan dari backup cloud (lihat data_extraction_rules.xml).
 */
class DevicePrefs(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("taskify_device", Context.MODE_PRIVATE)

    var dbOwner: String?
        get() = prefs.getString(KEY_DB_OWNER, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_DB_OWNER) else putString(KEY_DB_OWNER, value) }

    private companion object {
        const val KEY_DB_OWNER = "db_owner"
    }
}
