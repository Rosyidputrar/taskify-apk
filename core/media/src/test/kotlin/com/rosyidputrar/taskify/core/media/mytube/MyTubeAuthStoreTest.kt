package com.rosyidputrar.taskify.core.media.mytube

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * `MyTubeAuthStore` (Tahap 5): simpan/baca `visitorData` di
 * SharedPreferences terpisah `"youtube_auth"` — bukan `"music"`.
 */
@RunWith(AndroidJUnit4::class)
class MyTubeAuthStoreTest {
    private lateinit var context: Context
    private lateinit var store: MyTubeAuthStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Bersihkan ketiga prefs supaya tes independen dari urutan jalan
        // (C M-2: rahasia kini di SECURE_PREFS_NAME, bukan PREFS_NAME).
        context.getSharedPreferences(MyTubeAuthStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(MyTubeAuthStore.SECURE_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("music", Context.MODE_PRIVATE).edit().clear().commit()
        store = MyTubeAuthStore(context)
    }

    @Test fun `kosong saat belum ada`() {
        assertNull(store.visitorData)
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
    }

    @Test fun `simpan dan baca visitorData bolak-balik`() {
        store.saveVisitorData("CgttZXN0")
        assertEquals("CgttZXN0", store.visitorData)
        // Instans baru membaca simpanan yang sama.
        assertEquals("CgttZXN0", MyTubeAuthStore(context).loadAuthState().visitorData)
    }

    @Test fun `simpan visitorData menormalisasi spasi dan string null`() {
        store.saveVisitorData("  CgttZXN0  ")
        assertEquals("CgttZXN0", store.visitorData)
        store.saveVisitorData("null")
        assertNull(store.visitorData)
        store.saveVisitorData("   ")
        assertNull(store.visitorData)
    }

    @Test fun `saveAuthState dan loadAuthState bolak-balik`() {
        store.saveAuthState(
            PlaybackAuthState(cookie = "SAPISID=x", visitorData = "Cgtv", dataSyncId = "d||u"),
        )
        val loaded = store.loadAuthState()
        assertEquals("SAPISID=x", loaded.cookie)
        assertEquals("Cgtv", loaded.visitorData)
        assertEquals("d||u", loaded.dataSyncId)
        // Tahap 5: poToken selalu kosong.
        assertNull(loaded.poToken)
    }

    @Test fun `tidak tertukar dengan prefs music`() {
        store.saveVisitorData("CgttZXN0")
        val music = context.getSharedPreferences("music", Context.MODE_PRIVATE)
        assertFalse(music.contains("visitorData"))
        assertFalse(music.contains("cookie"))
        assertFalse(music.contains("dataSyncId"))
        assertFalse(music.contains("poToken"))
        // Sebaliknya: isi prefs "music" tidak terbaca oleh store.
        music.edit().putString("visitorData", "Cgtsalah").apply()
        assertEquals("CgttZXN0", store.visitorData)
    }

    @Test fun `clear menghapus semua`() {
        store.saveVisitorData("CgttZXN0")
        store.clear()
        assertNull(store.visitorData)
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
    }

    // ------------------------------------------------------------ akun (Tahap 3)

    @Test fun `akun kosong saat belum ada`() {
        assertNull(store.loadAccount())
    }

    @Test fun `simpan dan baca akun bolak-balik`() {
        val account = AccountInfo(
            name = "Nama Tes",
            email = "tes@example.com",
            channelHandle = "@tes",
            thumbnailUrl = "https://example.com/foto.jpg",
        )
        store.saveAccount(account)
        assertEquals(account, store.loadAccount())
        // Instans baru membaca simpanan yang sama.
        assertEquals(account, MyTubeAuthStore(context).loadAccount())
    }

    @Test fun `simpan akun hanya nama, sisanya null`() {
        store.saveAccount(AccountInfo(name = "Nama Tes"))
        assertEquals(AccountInfo(name = "Nama Tes"), store.loadAccount())
    }

    @Test fun `nilai akun kosong menghapus kuncinya`() {
        store.saveAccount(AccountInfo(name = "Nama Tes", email = "tes@example.com"))
        store.saveAccount(AccountInfo(name = "Nama Tes", email = "   ", channelHandle = "", thumbnailUrl = null))
        assertEquals(AccountInfo(name = "Nama Tes"), store.loadAccount())
    }

    @Test fun `nama akun kosong berarti tidak ada akun`() {
        store.saveAccount(AccountInfo(name = "   "))
        assertNull(store.loadAccount())
    }

    @Test fun `clearAccount hanya menghapus akun, state auth tetap`() {
        store.saveAuthState(PlaybackAuthState(cookie = "SAPISID=x", visitorData = "Cgtv"))
        store.saveAccount(AccountInfo(name = "Nama Tes", email = "tes@example.com"))
        store.clearAccount()
        assertNull(store.loadAccount())
        assertEquals("SAPISID=x", store.loadAuthState().cookie)
        assertEquals("Cgtv", store.loadAuthState().visitorData)
    }

    @Test fun `clear menghapus akun juga`() {
        store.saveAccount(AccountInfo(name = "Nama Tes"))
        store.clear()
        assertNull(store.loadAccount())
    }

    // ------------------------------------------------------------ C M-2: prefs terenkripsi

    /**
     * Routing rahasia → prefs terpisah dari prefs biasa. Prefs diinjeksikan
     * (bukan EncryptedSharedPreferences sungguhan) supaya tes ini membuktikan
     * PEMISAHAN-nya di Robolectric apa pun dukungan KeyStore-nya; enkripsi
     * nyatanya dibuktikan probe `EncryptedPrefsProbeTest` + tes HP.
     */
    @Test fun `rahasia disimpan di prefs terpisah, bukan prefs biasa`() {
        val secure = context.getSharedPreferences("tes-secure", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val plain = context.getSharedPreferences("tes-plain", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val injected = MyTubeAuthStore(context, securePrefs = secure, plainPrefs = plain)
        injected.saveAuthState(
            PlaybackAuthState(cookie = "SAPISID=x", visitorData = "Cgtv", dataSyncId = "d||u", poToken = "tok123"),
        )
        assertFalse(plain.contains("cookie"), "cookie tidak boleh di prefs biasa")
        assertFalse(plain.contains("dataSyncId"), "dataSyncId tidak boleh di prefs biasa")
        assertFalse(plain.contains("poToken"), "poToken tidak boleh di prefs biasa")
        assertEquals("Cgtv", plain.getString("visitorData", null), "visitorData anonim tetap di prefs biasa")
        assertEquals("SAPISID=x", secure.getString("cookie", null))
        assertEquals("d||u", secure.getString("dataSyncId", null))
        assertEquals("tok123", secure.getString("poToken", null))
        val loaded = injected.loadAuthState()
        assertEquals("SAPISID=x", loaded.cookie)
        assertEquals("Cgtv", loaded.visitorData)
        assertEquals("d||u", loaded.dataSyncId)
        assertEquals("tok123", loaded.poToken)
    }

    @Test fun `migrasi memindahkan rahasia lama ke prefs terenkripsi`() {
        val plain = context.getSharedPreferences("tes-plain-mig", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        // Seolah dari versi lama: rahasia masih di prefs biasa.
        plain.edit().putString("cookie", "SAPISID=lama").putString("dataSyncId", "d||lama").putString("visitorData", "Cgtv").apply()
        val secure = context.getSharedPreferences("tes-secure-mig", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val injected = MyTubeAuthStore(context, securePrefs = secure, plainPrefs = plain)
        val loaded = injected.loadAuthState()
        assertEquals("SAPISID=lama", loaded.cookie)
        assertEquals("d||lama", loaded.dataSyncId)
        assertEquals("Cgtv", loaded.visitorData)
        assertFalse(plain.contains("cookie"), "kunci lama harus dihapus setelah migrasi")
        assertFalse(plain.contains("dataSyncId"), "kunci lama harus dihapus setelah migrasi")
        assertEquals("SAPISID=lama", secure.getString("cookie", null))
        assertEquals("d||lama", secure.getString("dataSyncId", null))
        // Idempotent: instans kedua membaca hasil migrasi yang sama.
        val second = MyTubeAuthStore(context, securePrefs = secure, plainPrefs = plain)
        assertEquals("SAPISID=lama", second.loadAuthState().cookie)
    }

    @Test fun `clear menghapus kedua prefs`() {
        val secure = context.getSharedPreferences("tes-secure-clr", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val plain = context.getSharedPreferences("tes-plain-clr", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        val injected = MyTubeAuthStore(context, securePrefs = secure, plainPrefs = plain)
        injected.saveAuthState(PlaybackAuthState(cookie = "SAPISID=x", visitorData = "Cgtv", dataSyncId = "d||u", poToken = "tok123"))
        injected.saveAccount(AccountInfo(name = "Nama Tes"))
        injected.clear()
        assertEquals(PlaybackAuthState.EMPTY, injected.loadAuthState())
        assertNull(injected.loadAccount())
        assertFalse(secure.contains("cookie"))
        assertFalse(plain.contains("visitorData"))
    }
}
