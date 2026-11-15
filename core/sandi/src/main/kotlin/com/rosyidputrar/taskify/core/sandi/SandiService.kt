package com.rosyidputrar.taskify.core.sandi

import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.data.TaskifyDatabase
import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigPayload
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.KEY_REJECTED_MESSAGE
import com.rosyidputrar.taskify.core.domain.sandi.KeyRecord
import com.rosyidputrar.taskify.core.domain.sandi.REMOTE_KEY_MESSAGE
import com.rosyidputrar.taskify.core.domain.sandi.RekeyResult
import com.rosyidputrar.taskify.core.domain.sandi.RemoteKeyState
import com.rosyidputrar.taskify.core.domain.sandi.RewrittenVault
import com.rosyidputrar.taskify.core.domain.sandi.RotationProbe
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.domain.sandi.TOTP_LOG_RETENTION_DAYS
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.TotpLogEntry
import com.rosyidputrar.taskify.core.domain.sandi.UNREACHABLE
import com.rosyidputrar.taskify.core.domain.sandi.rotationBlocker
import com.rosyidputrar.taskify.core.domain.sandi.rotationRejectedMessage
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.sandi.ConfigRepository
import com.rosyidputrar.taskify.core.store.sandi.ConfigVersionRow
import com.rosyidputrar.taskify.core.store.sandi.KeyRow
import com.rosyidputrar.taskify.core.store.sandi.SandiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pesan galat yang siap ditampilkan apa adanya. */
class SandiUserException(message: String) : Exception(message)

sealed interface VaultStatus {
    data object Loading : VaultStatus
    /** Belum ada brankas di perangkat ini (mungkin ada di server tapi belum tersinkron). */
    data object NoVault : VaultStatus
    data class Locked(val key: KeyRow) : VaultStatus
    data class Unlocked(val key: KeyRow, val open: OpenVault) : VaultStatus
}

data class SandiItem(val id: String, val payload: ItemPayload, val createdAt: String, val updatedAt: String)

data class DecryptedItems(val items: List<SandiItem> = emptyList(), val failed: List<String> = emptyList(), val ready: Boolean = false)

data class TotpLogItem(val id: String, val createdAt: String, val entry: TotpLogEntry)

data class DecryptedVersion(val row: ConfigVersionRow, val payload: ConfigPayload)

data class DecryptedVersions(val versions: List<DecryptedVersion> = emptyList(), val failed: List<String> = emptyList(), val ready: Boolean = false)

/** Hasil penggantian kunci: kunci pemulihan BARU wajib ditampilkan ke pengguna. */
data class RotationDone(val recoveryKey: String, val result: RekeyResult, val confirmed: Boolean)

/**
 * Semua alur brankas Sandi (padanan SandiPage + sandi-data + vault-guard webapp):
 * buat (setelah memastikan server belum punya brankas), buka (master password / perangkat
 * diingat), pulihkan, ganti master password / kunci pemulihan (dengan prasyarat & menunggu
 * jawaban server), serta baca-tulis item, riwayat TOTP, dan versi konfigurasi terenkripsi.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SandiService(
    private val repo: SandiRepository,
    private val configs: ConfigRepository,
    val session: SandiSession,
    private val trust: TrustStore,
    private val server: VaultServer?,
    private val database: TaskifyDatabase,
    private val users: UserIdProvider,
    private val prefs: SandiPrefs,
    private val scope: CoroutineScope,
) {
    private val key: StateFlow<KeyRow?> = repo.watchKey().stateIn(scope, SharingStarted.Eagerly, null)
    private val keyLoaded = repo.watchKey().map { true }.stateIn(scope, SharingStarted.Eagerly, false)

    init {
        // Kunci diganti di perangkat lain → DEK yang terbuka di sini sudah usang.
        scope.launch { key.collect { session.onKeyChanged(it?.record?.wrappedKey) } }
    }

    val status: Flow<VaultStatus> = combine(key, keyLoaded, session.vault) { k, loaded, open ->
        when {
            !loaded -> VaultStatus.Loading
            k == null -> VaultStatus.NoVault
            open != null && open.stamp == k.record.wrappedKey -> VaultStatus.Unlocked(k, open)
            else -> VaultStatus.Locked(k)
        }
    }

    val items: Flow<DecryptedItems> = session.vault.flatMapLatest { open ->
        if (open == null) flowOf(DecryptedItems()) else repo.watchItemRows().map { rows ->
            withContext(Dispatchers.Default) {
                val userId = users.require()
                val ok = mutableListOf<SandiItem>()
                val failed = mutableListOf<String>()
                rows.forEach { r ->
                    runCatching { SandiCrypto.decryptItem(open.dek, userId, r.id, r.enc) }
                        .onSuccess { ok += SandiItem(r.id, it, r.createdAt, r.updatedAt) }
                        .onFailure { failed += r.id }
                }
                DecryptedItems(ok, failed, true)
            }
        }
    }

    /** Riwayat kode TOTP; baris dari kunci lama dilewati (hilang sendiri karena retensi). */
    val totpLog: Flow<List<TotpLogItem>> = session.vault.flatMapLatest { open ->
        if (open == null) flowOf(emptyList()) else repo.watchTotpLogRows().map { rows ->
            withContext(Dispatchers.Default) {
                val userId = users.require()
                rows.mapNotNull { r -> runCatching { TotpLogItem(r.id, r.createdAt, SandiCrypto.decryptTotpLog(open.dek, userId, r.id, r.enc)) }.getOrNull() }
            }
        }
    }

    val rejectedKeyCreation = repo.watchRejectedKeyCreation()
    val rejectedRotation = repo.watchRejectedRotation()
    val staleWrites = repo.watchStaleWrites()

    /** Waktu terkoreksi untuk TOTP (selisih jam perangkat vs server). */
    fun totpNow(): Long = System.currentTimeMillis() + prefs.clockOffsetMs
    val clockOffsetMs: Long get() = prefs.clockOffsetMs

    // ================================================================== buat & buka

    /** Buat brankas pertama: SERVER harus memastikan belum ada brankas (bukan database lokal). */
    suspend fun createVault(password: String): String {
        val state = server?.keyState() ?: RemoteKeyState.Unreachable
        if (state != RemoteKeyState.None) throw SandiUserException(REMOTE_KEY_MESSAGE.getValue(state))
        val userId = users.require()
        val vault = withContext(Dispatchers.Default) { SandiCrypto.createVault(password, userId) }
        val keyId = try {
            repo.createKey(vault.record)
        } catch (e: Exception) {
            vault.dek.destroy()
            throw SandiUserException(e.message ?: "Brankas tidak bisa dibuat.")
        }
        // Tunggu server menerima record kunci SEBELUM ada item yang ditulis dengan kunci ini.
        val deadline = System.currentTimeMillis() + 15_000
        while (true) {
            if (repo.keyRejection(keyId).first) {
                repo.dismissRejectedKeyCreation(keyId)
                vault.dek.destroy()
                throw SandiUserException(KEY_REJECTED_MESSAGE)
            }
            if (!repo.opPending("pw_keys", keyId) || System.currentTimeMillis() >= deadline) break
            delay(400)
        }
        session.unlock(vault.dek, vault.record.wrappedKey)
        return vault.recoveryKey
    }

    /** false = master password salah. */
    suspend fun unlock(password: String, remember: Boolean): Boolean {
        val k = key.value ?: repo.getKey() ?: throw SandiUserException("Brankas belum tersinkron ke perangkat ini.")
        val dek = withContext(Dispatchers.Default) { SandiCrypto.unlockWithPassword(k.record, password, users.require()) } ?: return false
        session.unlock(dek, k.record.wrappedKey)
        if (remember) trust.remember(dek, k.record.wrappedKey)
        afterUnlock()
        return true
    }

    /** Buka tanpa master password bila perangkat ini diingat dan kuncinya masih berlaku. */
    suspend fun unlockTrusted(): Boolean {
        val k = key.value ?: repo.getKey() ?: return false
        val dek = withContext(Dispatchers.Default) { trust.load(k.record.wrappedKey) } ?: return false
        session.unlock(dek, k.record.wrappedKey)
        afterUnlock()
        return true
    }

    fun trustMeta() = trust.meta()
    fun forgetDevice() = trust.forget()

    private fun afterUnlock() {
        scope.launch {
            runCatching { repo.purgeTotpLog(Dates.millisToIso(System.currentTimeMillis() - TOTP_LOG_RETENTION_DAYS * 86_400_000L)) }
            server?.clockSample()?.let { (serverMs, sent, received) -> Totp.offsetFromSample(serverMs, sent, received)?.let { prefs.clockOffsetMs = it } }
        }
    }

    fun lock() = session.lock()

    /** Keluar akun: kunci & lupakan perangkat. */
    fun wipe() {
        session.lock()
        trust.forget()
    }

    // ================================================================== ganti kunci

    suspend fun changePassword(old: String, new: String) =
        rotate("Master password lama salah.") { r, u -> SandiCrypto.changePassword(r, old, new, u) }

    suspend fun rotateRecoveryKey(password: String) =
        rotate("Master password salah.") { r, u -> SandiCrypto.rotateRecoveryKey(r, password, u) }

    /** Lupa master password: kunci pemulihan + master password baru. */
    suspend fun recover(recoveryKey: String, newPassword: String) =
        rotate("Kunci pemulihan salah. Periksa lagi (huruf O/0 dan I/1 boleh tertukar).") { r, u -> SandiCrypto.resetPasswordWithRecovery(r, recoveryKey, newPassword, u) }

    private suspend fun rotate(wrongMessage: String, compute: (KeyRecord, String) -> RewrittenVault?): RotationDone {
        val k = repo.getKey() ?: throw SandiUserException("Brankas belum tersinkron ke perangkat ini.")
        // 1. Prasyarat — gagal di sini = tidak ada yang berubah.
        val status = database.syncStatus.first()
        val probe = RotationProbe(
            connected = status.connected,
            hasSynced = status.hasSynced == true,
            downloading = status.downloading,
            pendingUploads = database.pendingUploadCount(),
            remoteWrappedKey = server?.wrappedKey() ?: UNREACHABLE,
        )
        rotationBlocker(probe, k.record.wrappedKey)?.let { throw SandiUserException(it.message) }
        val userId = users.require()
        val vault = withContext(Dispatchers.Default) { compute(k.record, userId) } ?: throw SandiUserException(wrongMessage)

        // 2. Tulis lokal dalam satu transaksi (diunggah atomik).
        val since = Timestamps.nowIso()
        session.rekeying = true
        try {
            val result = withContext(Dispatchers.Default) { repo.rekey(k.id, vault) }
            session.unlock(vault.dek, vault.record.wrappedKey)
            vault.previousDek.destroy()
            trust.forget() // DEK lama tidak berlaku lagi di perangkat mana pun

            // 3. Tunggu jawaban server (maks. 30 dtk).
            val deadline = System.currentTimeMillis() + 30_000
            var confirmed = false
            while (true) {
                val (rejected, code) = repo.keyRejection(k.id, since)
                if (rejected) {
                    repo.dismissRejectedRotation(since)
                    session.lock()
                    throw SandiUserException(rotationRejectedMessage(code))
                }
                if (!repo.opPending("pw_keys", k.id)) {
                    val (late, lateCode) = repo.keyRejection(k.id, since)
                    if (late) {
                        repo.dismissRejectedRotation(since)
                        session.lock()
                        throw SandiUserException(rotationRejectedMessage(lateCode))
                    }
                    confirmed = true
                    break
                }
                if (System.currentTimeMillis() >= deadline) break
                delay(400)
            }
            return RotationDone(vault.recoveryKey, result, confirmed)
        } finally {
            session.rekeying = false
        }
    }

    // ================================================================== item & TOTP

    suspend fun saveItem(payload: ItemPayload, id: String? = null): String {
        val open = session.require()
        val userId = users.require()
        val itemId = id ?: repo.newId()
        val enc = withContext(Dispatchers.Default) { SandiCrypto.encryptItem(open.dek, userId, itemId, payload, open.ref) }
        if (id == null) repo.insertItem(itemId, enc) else repo.updateItem(itemId, enc)
        return itemId
    }

    suspend fun removeItem(id: String) = repo.removeItem(id)
    suspend fun restoreItem(id: String) = repo.restoreItem(id)

    /** Catat pemakaian kode (saat disalin). Tidak menyentuh item. */
    suspend fun recordTotpUse(itemId: String, name: String, code: String) {
        val open = session.require()
        val rowId = repo.newId()
        val enc = SandiCrypto.encryptTotpLog(open.dek, users.require(), rowId, TotpLogEntry(itemId, name, code, Timestamps.nowIso()), open.ref)
        repo.insertTotpLog(rowId, enc)
    }

    suspend fun deleteTotpLog(id: String) = repo.deleteTotpLog(id)
    suspend fun clearTotpLog() = repo.clearTotpLog()

    suspend fun dismissRejectedKeyCreation() = repo.dismissRejectedKeyCreation()
    suspend fun dismissRejectedRotation() = repo.dismissRejectedRotation()
    suspend fun dismissStaleWrites() = repo.dismissStaleWrites()

    // ================================================================== konfigurasi

    fun versions(fileId: String): Flow<DecryptedVersions> = session.vault.flatMapLatest { open ->
        if (open == null) flowOf(DecryptedVersions()) else configs.watchVersions(fileId).map { rows ->
            withContext(Dispatchers.Default) {
                val userId = users.require()
                val ok = mutableListOf<DecryptedVersion>()
                val failed = mutableListOf<String>()
                rows.forEach { r ->
                    runCatching { SandiCrypto.decryptConfig(open.dek, userId, fileId, r.id, r.enc) }.onSuccess { ok += DecryptedVersion(r, it) }.onFailure { failed += r.id }
                }
                DecryptedVersions(ok, failed, true)
            }
        }
    }

    suspend fun createConfigFile(name: String, format: ConfigFormat, environment: String, projectId: String?, text: String, note: String): String {
        val open = session.require()
        val userId = users.require()
        return configs.createFile(name, format, environment, projectId) { fileId, versionId ->
            SandiCrypto.encryptConfig(open.dek, userId, fileId, versionId, ConfigPayload(text, note.trim()), open.ref)
        }
    }

    suspend fun saveConfigVersion(fileId: String, text: String, note: String): String {
        val open = session.require()
        val userId = users.require()
        return configs.saveVersion(fileId) { versionId -> SandiCrypto.encryptConfig(open.dek, userId, fileId, versionId, ConfigPayload(text, note.trim()), open.ref) }
    }

}
