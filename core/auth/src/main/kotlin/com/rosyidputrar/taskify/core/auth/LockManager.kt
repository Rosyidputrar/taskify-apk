package com.rosyidputrar.taskify.core.auth

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.lock.LockAttempts
import com.rosyidputrar.taskify.core.domain.lock.LockPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Fungsi MAC untuk hash PIN. Di perangkat: kunci HMAC Android Keystore (tidak bisa diekspor). */
fun interface PinMac {
    fun mac(data: ByteArray): ByteArray
}

class KeystorePinMac : PinMac {
    override fun mac(data: ByteArray): ByteArray =
        Mac.getInstance(ALGORITHM).apply { init(key()) }.doFinal(data)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "taskify_pin_mac"
        const val ALGORITHM = "HmacSHA256"
    }
}

data class LockConfig(val length: Int, val autoLockMinutes: Int)

data class LockState(
    val config: LockConfig? = null,
    val locked: Boolean = false,
    val attempts: LockAttempts = LockAttempts(),
)

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class Wrong(val remaining: Int) : UnlockResult
    data class CoolingDown(val untilMillis: Long) : UnlockResult
}

/**
 * Kunci aplikasi PIN per perangkat. Ini kunci TAMPILAN (privasi dari orang yang memegang HP),
 * bukan enkripsi data. Terkunci saat aplikasi dibuka bila PIN aktif, dan setelah aplikasi berada
 * di latar selama batas pilihan (0 = segera).
 */
class LockManager(
    devicePrefs: DevicePrefs,
    private val pinMac: PinMac,
    private val now: () -> Long = System::currentTimeMillis,
) : DefaultLifecycleObserver {
    private val prefs: SharedPreferences = devicePrefs.prefs
    private val random = SecureRandom()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var backgroundAt: Long? = null

    private fun initialState(): LockState {
        val config = readConfig()
        return LockState(config = config, locked = config != null, attempts = readAttempts())
    }

    suspend fun enable(pin: String, autoLockMinutes: Int) {
        require(LockPolicy.isValidPin(pin)) { "PIN harus 4–8 angka" }
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = withContext(Dispatchers.Default) { LockPolicy.hash(pinMac::mac, salt, pin) }
        prefs.edit {
            putString(KEY_SALT, salt.b64())
            putString(KEY_HASH, hash.b64())
            putInt(KEY_LENGTH, pin.length)
            putInt(KEY_AUTO, autoLockMinutes)
        }
        writeAttempts(LockAttempts())
        _state.value = LockState(config = LockConfig(pin.length, autoLockMinutes), locked = false)
    }

    fun setAutoLockMinutes(minutes: Int) {
        val config = _state.value.config ?: return
        prefs.edit { putInt(KEY_AUTO, minutes) }
        _state.update { it.copy(config = config.copy(autoLockMinutes = minutes)) }
    }

    /** Matikan PIN (dari Pengaturan, atau setelah "Lupa PIN" diverifikasi dengan password akun). */
    fun disable() {
        prefs.edit { remove(KEY_SALT).remove(KEY_HASH).remove(KEY_LENGTH).remove(KEY_AUTO) }
        writeAttempts(LockAttempts())
        _state.value = LockState()
    }

    fun lockNow() {
        if (_state.value.config != null) _state.update { it.copy(locked = true) }
    }

    suspend fun unlock(pin: String): UnlockResult {
        val current = LockPolicy.sanitize(readAttempts(), now())
        current.cooldownUntil?.let { return UnlockResult.CoolingDown(it) }
        val salt = prefs.getString(KEY_SALT, null)?.unb64() ?: return UnlockResult.Success.also { unlocked() }
        val expected = prefs.getString(KEY_HASH, null)?.unb64() ?: return UnlockResult.Success.also { unlocked() }
        val ok = withContext(Dispatchers.Default) { LockPolicy.verify(pinMac::mac, salt, pin, expected) }
        if (ok) {
            unlocked()
            return UnlockResult.Success
        }
        val next = LockPolicy.registerFailure(current, now())
        writeAttempts(next)
        _state.update { it.copy(attempts = next) }
        return next.cooldownUntil?.let { UnlockResult.CoolingDown(it) }
            ?: UnlockResult.Wrong(LockPolicy.MAX_ATTEMPTS - next.failedAttempts)
    }

    private fun unlocked() {
        writeAttempts(LockAttempts())
        _state.update { it.copy(locked = false, attempts = LockAttempts()) }
    }

    // Siklus hidup proses (ProcessLifecycleOwner): seluruh aplikasi ke latar / kembali.
    override fun onStop(owner: LifecycleOwner) {
        backgroundAt = now()
    }

    override fun onStart(owner: LifecycleOwner) {
        val hiddenAt = backgroundAt ?: return
        backgroundAt = null
        val config = _state.value.config ?: return
        if (LockPolicy.shouldLockAfterBackground(hiddenAt, now(), config.autoLockMinutes)) lockNow()
    }

    private fun readConfig(): LockConfig? {
        if (prefs.getString(KEY_HASH, null) == null) return null
        return LockConfig(
            length = prefs.getInt(KEY_LENGTH, 4),
            autoLockMinutes = prefs.getInt(KEY_AUTO, LockPolicy.DEFAULT_AUTO_LOCK_MINUTES),
        )
    }

    private fun readAttempts(): LockAttempts = LockPolicy.sanitize(
        LockAttempts(
            failedAttempts = prefs.getInt(KEY_FAILED, 0),
            cooldownUntil = prefs.getLong(KEY_COOLDOWN, 0L).takeIf { it > 0 },
        ),
        now(),
    )

    private fun writeAttempts(attempts: LockAttempts) = prefs.edit {
        putInt(KEY_FAILED, attempts.failedAttempts)
        val until = attempts.cooldownUntil
        if (until == null) remove(KEY_COOLDOWN) else putLong(KEY_COOLDOWN, until)
    }

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEY_SALT = "lock_salt"
        const val KEY_HASH = "lock_hash"
        const val KEY_LENGTH = "lock_length"
        const val KEY_AUTO = "lock_auto_minutes"
        const val KEY_FAILED = "lock_failed"
        const val KEY_COOLDOWN = "lock_cooldown_until"
    }
}
