package com.rosyidputrar.taskify.core.sandi

import android.app.Application
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.sandi.Dek
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustTestActivity : FragmentActivity()

/**
 * "Ingat perangkat" Sandi terhadap Android Keystore + BiometricPrompt SUNGGUHAN.
 * Butuh perangkat dengan kunci layar (mis. `adb shell locksettings set-pin 1111`) dan seseorang /
 * skrip yang memasukkan PIN saat dialog verifikasi muncul (penanda log `TRUST_PROMPT`).
 */
@RunWith(AndroidJUnit4::class)
class DeviceTrustInstrumentedTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test fun rememberedKeyOpensOnlyAfterOwnerVerification() = runBlocking {
        assumeTrue("perangkat butuh kunci layar", ActivityDeviceAuthenticator.available(app))
        val auth = ActivityDeviceAuthenticator(app)
        val store = KeystoreTrustStore(app)
        store.forget()
        ActivityScenario.launch(TrustTestActivity::class.java).use {
            val secret = ByteArray(32) { (it * 7 + 3).toByte() }
            Log.i("TaskifyTrust", "TRUST_PROMPT remember")
            assertTrue(withTimeout(180_000) { store.remember(Dek(secret.copyOf()), "stamp-1", auth) }, "verifikasi saat mengingat")
            assertNotNull(store.meta())

            // Tanpa verifikasi pemilik (authenticator palsu yang langsung "Ok"): Keystore MENOLAK.
            assertFailsWith<DeviceAuthException> { store.load("stamp-1", { _, _ -> AuthResult.Ok }) }
            assertNotNull(store.meta(), "penolakan verifikasi tidak menghapus kunci")

            // Dibatalkan pengguna → null, kunci tetap ada.
            assertNull(store.load("stamp-1", { _, _ -> AuthResult.Cancelled }))

            Log.i("TaskifyTrust", "TRUST_PROMPT load")
            val opened = withTimeout(180_000) { store.load("stamp-1", auth) }
            assertNotNull(opened)
            assertContentEquals(secret, opened.export())

            // Kunci brankas diganti di perangkat lain (stamp beda) → dilupakan.
            assertNull(store.load("stamp-2", auth))
            assertNull(store.meta())
        }
    }
}
