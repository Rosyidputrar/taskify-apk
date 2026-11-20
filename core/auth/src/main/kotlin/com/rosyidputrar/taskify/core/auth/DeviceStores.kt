package com.rosyidputrar.taskify.core.auth

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.auth.DeviceTrust
import com.rosyidputrar.taskify.core.domain.auth.TRUST_MILLIS
import com.rosyidputrar.taskify.core.domain.auth.cleanDisplayName

/** "Ingat perangkat ini selama 30 hari" untuk verifikasi dua langkah. */
class MfaTrustStore(devicePrefs: DevicePrefs) {
    private val prefs: SharedPreferences = devicePrefs.prefs

    fun read(): DeviceTrust? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val until = prefs.getLong(KEY_UNTIL, 0L).takeIf { it > 0 } ?: return null
        return DeviceTrust(user, until)
    }

    fun trust(userId: String, now: Long = System.currentTimeMillis()) {
        prefs.edit { putString(KEY_USER, userId).putLong(KEY_UNTIL, now + TRUST_MILLIS) }
        recordVerified(now)
    }

    /**
     * Waktu terakhir perangkat ini lolos verifikasi (kode authenticator / ingat perangkat).
     * Dipakai gerbang MFA untuk masa tenggang offline (D-02): hanya dicatat saat verifikasi
     * benar-benar terjadi, TIDAK saat gerbang dilewati lewat tenggang itu sendiri (supaya tenggang
     * tidak memperpanjang dirinya sendiri selamanya).
     */
    fun lastVerifiedAt(): Long? = prefs.getLong(KEY_LAST_VERIFIED, 0L).takeIf { it > 0 }

    fun recordVerified(now: Long = System.currentTimeMillis()) =
        prefs.edit { putLong(KEY_LAST_VERIFIED, now) }

    fun clear() = prefs.edit { remove(KEY_USER).remove(KEY_UNTIL).remove(KEY_LAST_VERIFIED) }

    private companion object {
        const val KEY_USER = "mfa_trust_user"
        const val KEY_UNTIL = "mfa_trust_until"
        const val KEY_LAST_VERIFIED = "mfa_last_verified_at"
    }
}

/**
 * Nama untuk sapaan layar masuk. Layar masuk tampil sebelum database ada, jadi nama disalin ke
 * perangkat. Sengaja bertahan setelah Keluar; bisa dimatikan di Pengaturan (perangkat bersama).
 */
class LoginNameStore(devicePrefs: DevicePrefs) {
    private val prefs: SharedPreferences = devicePrefs.prefs

    val enabled: Boolean get() = !prefs.getBoolean(KEY_OFF, false)

    fun read(): String? = if (enabled) cleanDisplayName(prefs.getString(KEY_NAME, null)).ifEmpty { null } else null

    fun write(name: String?) {
        val clean = cleanDisplayName(name)
        prefs.edit { if (clean.isEmpty() || !enabled) remove(KEY_NAME) else putString(KEY_NAME, clean) }
    }

    /** Mematikan LANGSUNG menghapus nama tersimpan. */
    fun setEnabled(enabled: Boolean, currentName: String?) {
        prefs.edit { if (enabled) remove(KEY_OFF) else putBoolean(KEY_OFF, true) }
        write(currentName)
    }

    private companion object {
        const val KEY_NAME = "login_name"
        const val KEY_OFF = "login_name_off"
    }
}
