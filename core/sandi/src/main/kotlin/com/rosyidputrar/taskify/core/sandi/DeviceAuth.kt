package com.rosyidputrar.taskify.core.sandi

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** Teks dialog verifikasi pemilik HP. */
data class AuthPrompt(val title: String, val subtitle: String)

sealed interface AuthResult {
    data object Ok : AuthResult
    /** Pengguna menutup dialog — bukan kesalahan. */
    data object Cancelled : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Verifikasi pemilik HP lewat sidik jari/wajah atau PIN/pola/sandi kunci layar.
 * [cipher] non-null → verifikasi diikat ke operasi kripto itu (CryptoObject).
 */
fun interface DeviceAuthenticator {
    suspend fun authenticate(prompt: AuthPrompt, cipher: Cipher?): AuthResult
}

/** Verifikasi gagal (bukan dibatalkan); pesannya layak ditampilkan. */
class DeviceAuthException(message: String) : Exception(message)

/**
 * Cara kunci Keystore "ingat perangkat" dilindungi per versi Android.
 * - API 30+: kunci per-pemakaian (timeout 0) yang dibuka BiometricPrompt + CryptoObject, dengan
 *   biometrik kuat ATAU kredensial layar.
 * - API 26–29: CryptoObject + kredensial layar belum didukung → kunci berbatas waktu singkat
 *   setelah verifikasi (biometrik ATAU kredensial layar).
 */
object DeviceTrustPolicy {
    const val VALIDITY_SECONDS = 15

    fun usesCryptoObject(sdk: Int): Boolean = sdk >= 30

    fun authenticators(sdk: Int): Int = if (sdk >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Kode error BiometricPrompt yang berarti "pengguna membatalkan", bukan kegagalan. */
    fun isCancel(code: Int): Boolean = code == BiometricPrompt.ERROR_USER_CANCELED ||
        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_CANCELED
}

/**
 * [DeviceAuthenticator] berbasis BiometricPrompt pada Activity yang sedang tampil (dilacak lewat
 * ActivityLifecycleCallbacks, jadi ViewModel tidak perlu memegang Activity).
 */
class ActivityDeviceAuthenticator(private val app: Application) : DeviceAuthenticator {
    @Volatile private var current: WeakReference<FragmentActivity>? = null

    init {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is FragmentActivity) current = WeakReference(activity)
            }
            override fun onActivityDestroyed(activity: Activity) {
                if (current?.get() === activity) current = null
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        })
    }

    override suspend fun authenticate(prompt: AuthPrompt, cipher: Cipher?): AuthResult = withContext(Dispatchers.Main) {
        val activity = current?.get()?.takeIf { !it.isFinishing && !it.isDestroyed } ?: return@withContext AuthResult.Failed("Buka Taskify lalu coba lagi.")
        val sdk = Build.VERSION.SDK_INT
        suspendCancellableCoroutine<AuthResult> { cont ->
            val biometric = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(AuthResult.Ok)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!cont.isActive) return
                        cont.resume(if (DeviceTrustPolicy.isCancel(errorCode)) AuthResult.Cancelled else AuthResult.Failed(errString.toString()))
                    }
                    // onAuthenticationFailed: sidik jari tidak cocok — dialog tetap terbuka untuk mencoba lagi.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(prompt.title)
                .setSubtitle(prompt.subtitle)
                .setAllowedAuthenticators(DeviceTrustPolicy.authenticators(sdk))
                .build()
            if (cipher != null) biometric.authenticate(info, BiometricPrompt.CryptoObject(cipher)) else biometric.authenticate(info)
            cont.invokeOnCancellation { runCatching { biometric.cancelAuthentication() } }
        }
    }

    companion object {
        /**
         * HP punya kunci layar (PIN/pola/sandi). Syarat Keystore untuk kunci yang butuh verifikasi,
         * dan selalu tersedia sebagai cadangan bila biometrik tidak ada.
         */
        fun available(app: Application): Boolean =
            app.getSystemService(android.app.KeyguardManager::class.java)?.isDeviceSecure == true
    }
}
