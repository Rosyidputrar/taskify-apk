package com.rosyidputrar.taskify.core.media.youtube

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
 * `YouTubeAuthStore` (Tahap 5): simpan/baca `visitorData` di
 * SharedPreferences terpisah `"youtube_auth"` — bukan `"music"`.
 */
@RunWith(AndroidJUnit4::class)
class YouTubeAuthStoreTest {
    private lateinit var context: Context
    private lateinit var store: YouTubeAuthStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Bersihkan kedua prefs supaya tes independen dari urutan jalan.
        context.getSharedPreferences(YouTubeAuthStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("music", Context.MODE_PRIVATE).edit().clear().commit()
        store = YouTubeAuthStore(context)
    }

    @Test fun `kosong saat belum ada`() {
        assertNull(store.visitorData)
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
    }

    @Test fun `simpan dan baca visitorData bolak-balik`() {
        store.saveVisitorData("CgttZXN0")
        assertEquals("CgttZXN0", store.visitorData)
        // Instans baru membaca simpanan yang sama.
        assertEquals("CgttZXN0", YouTubeAuthStore(context).loadAuthState().visitorData)
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
        assertEquals(account, YouTubeAuthStore(context).loadAccount())
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
}
