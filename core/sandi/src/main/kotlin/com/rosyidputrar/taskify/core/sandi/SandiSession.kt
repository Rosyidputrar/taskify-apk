package com.rosyidputrar.taskify.core.sandi

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rosyidputrar.taskify.core.domain.sandi.Dek
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.domain.sandi.SandiException
import com.rosyidputrar.taskify.core.domain.sandi.shouldLockSandi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Brankas terbuka: DEK + `wrapped_key` record yang membukanya ([stamp]). */
class OpenVault(val dek: Dek, val stamp: String) {
    /** Label kunci untuk setiap tulisan terenkripsi (port `currentKeyRef`). */
    val ref: String = SandiCrypto.keyRef(stamp)
}

/**
 * Status kunci brankas Sandi (padanan `sandi-store.ts`). DEK hanya ada di memori proses: hilang
 * saat dikunci, aplikasi dimatikan, atau akun keluar. Kunci otomatis:
 *  - aplikasi ke latar → dikunci setelah N menit (0 = segera);
 *  - terlihat tapi tanpa interaksi → dikunci setelah N menit.
 */
class SandiSession(private val prefs: SandiPrefs, private val clipboard: SecureClipboard, private val scope: CoroutineScope) : DefaultLifecycleObserver {
    private val _vault = MutableStateFlow<OpenVault?>(null)
    val vault: StateFlow<OpenVault?> = _vault

    /** true selama penggantian kunci dari perangkat ini (record berubah SENGAJA). */
    @Volatile var rekeying = false
    private var lastActivity = System.currentTimeMillis()
    private var hiddenAt: Long? = null
    private var idleJob: Job? = null

    val autoLockMinutes: Int get() = prefs.autoLockMinutes

    fun setAutoLock(minutes: Int) {
        prefs.autoLockMinutes = minutes
    }

    fun unlock(dek: Dek, stamp: String) {
        val previous = _vault.value
        _vault.value = OpenVault(dek, stamp)
        if (previous != null && previous.dek !== dek) previous.dek.destroy()
        lastActivity = System.currentTimeMillis()
        hiddenAt = null
        startIdleWatch()
    }

    fun lock() {
        val current = _vault.value ?: return
        _vault.value = null
        current.dek.destroy()
        idleJob?.cancel()
        clipboard.clearNow() // jangan biarkan sandi tertinggal di papan klip setelah dikunci
    }

    /**
     * Brankas untuk Isi Otomatis di aplikasi lain. Saat itu Taskify biasanya di latar, dan batas
     * kunci otomatis baru diperiksa ketika Taskify tampil lagi ([onStart]) — maka diperiksa di sini
     * juga: yang sudah lewat batas dikunci, bukan dipakai.
     */
    fun openForAutofill(now: Long = System.currentTimeMillis()): OpenVault? {
        val v = _vault.value ?: return null
        if (shouldLockSandi(now, lastActivity, hiddenAt, prefs.autoLockMinutes)) {
            lock()
            return null
        }
        return v
    }

    /** Interaksi pengguna di layar Sandi (menunda kunci idle). */
    fun touch() {
        if (_vault.value != null) lastActivity = System.currentTimeMillis()
    }

    /** Brankas terbuka untuk [dek] ini; menolak menulis dengan kunci yang sudah dikunci/diganti. */
    fun require(dek: Dek? = null): OpenVault {
        val v = _vault.value ?: throw SandiException(SandiException.Code.Invalid, "Brankas terkunci. Buka brankas lagi lalu simpan ulang.")
        if (dek != null && v.dek !== dek) throw SandiException(SandiException.Code.Invalid, "Brankas terkunci atau kuncinya baru diganti. Buka brankas lagi lalu simpan ulang.")
        return v
    }

    /** Kunci di database berubah (diganti di perangkat lain) → DEK ini usang. */
    fun onKeyChanged(wrappedKey: String?) {
        val v = _vault.value ?: return
        if (!rekeying && wrappedKey != v.stamp) lock()
    }

    private fun startIdleWatch() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive && _vault.value != null) {
                delay(15_000)
                if (shouldLockSandi(System.currentTimeMillis(), lastActivity, hiddenAt, prefs.autoLockMinutes)) lock()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        hiddenAt = System.currentTimeMillis()
        if (prefs.autoLockMinutes == 0) lock()
    }

    override fun onStart(owner: LifecycleOwner) {
        val hidden = hiddenAt
        hiddenAt = null
        if (_vault.value == null) return
        // Kunci SEBELUM isi sempat terlihat lagi.
        if (hidden != null && shouldLockSandi(System.currentTimeMillis(), lastActivity, hidden, prefs.autoLockMinutes)) lock()
        else lastActivity = System.currentTimeMillis()
    }
}
