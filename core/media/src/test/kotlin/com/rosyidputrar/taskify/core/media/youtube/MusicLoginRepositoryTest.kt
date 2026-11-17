package com.rosyidputrar.taskify.core.media.youtube

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.YouTube
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `MusicLoginRepository` (Tahap 4) dengan `YouTube` palsu: tidak ada
 * panggilan jaringan sungguhan. Cookie di fixture ini DUMMY, bukan cookie asli.
 *
 * Catatan: fake di sini meng-override `accountInfo()`/`accountDataSyncId()`
 * kontrak PLAN C.3 (Agen A1); kompilasi penuh file ini menunggu metode itu
 * mendarat di `YouTube`.
 */
@RunWith(AndroidJUnit4::class)
class MusicLoginRepositoryTest {
    private lateinit var context: Context
    private lateinit var store: YouTubeAuthStore

    // Cookie dummy lengkap: LOGIN_INFO + salah satu SAPISID.
    private val completeCookie = "LOGIN_INFO=dummy-login-info; SAPISID=dummy-sapisid; SID=dummy"
    private val account = AccountInfo(
        name = "Nama Tes",
        email = "tes@example.com",
        channelHandle = "@tes",
        thumbnailUrl = "https://example.com/foto.jpg",
    )

    private class FakeYouTube : YouTube() {
        var accountInfoResult: Result<AccountInfo> = Result.success(
            AccountInfo("Nama Tes", "tes@example.com", "@tes", "https://example.com/foto.jpg"),
        )
        var accountDataSyncIdResult: Result<String> = Result.success("delegated||user")
        /** State auth yang sedang diterapkan saat `accountInfo()` dipanggil. */
        var authStateAtAccountInfo: PlaybackAuthState? = null

        override suspend fun accountInfo(authState: PlaybackAuthState): Result<AccountInfo> {
            authStateAtAccountInfo = currentAuthState()
            return accountInfoResult
        }

        override suspend fun accountDataSyncId(authState: PlaybackAuthState): Result<String> =
            accountDataSyncIdResult
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(YouTubeAuthStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = YouTubeAuthStore(context)
    }

    private fun repository(fake: FakeYouTube = FakeYouTube()) = MusicLoginRepository(fake, store)

    private fun loginError(result: Result<AccountInfo>): MusicLoginError? =
        (result.exceptionOrNull() as? MusicLoginException)?.error

    @Test fun `completeLogin sukses menyimpan state akhir dan akun`() = runTest {
        val fake = FakeYouTube()
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(account), result)
        assertEquals(account, repo.account.value)
        assertFalse(repo.sessionExpired.value)
        assertTrue(repo.isLoggedIn)
        // dataSyncId jaringan menang atas kandidat WebView.
        val stored = store.loadAuthState()
        assertEquals(completeCookie, stored.cookie)
        assertEquals("Cgtv", stored.visitorData)
        assertEquals("delegated||user", stored.dataSyncId)
        assertEquals(account, store.loadAccount())
        // Fasad ikut memakai state akhir.
        assertEquals(stored, fake.currentAuthState())
        // Verifikasi akun jalan dengan dataSyncId=null dulu.
        assertEquals(completeCookie, fake.authStateAtAccountInfo?.cookie)
        assertNull(fake.authStateAtAccountInfo?.dataSyncId)
    }

    @Test fun `completeLogin fallback ke kandidat bila jaringan dataSyncId gagal`() = runTest {
        val fake = FakeYouTube().apply {
            accountDataSyncIdResult = Result.failure(RuntimeException("jaringan putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(account), result)
        assertEquals("kandidat||kandidat", store.loadAuthState().dataSyncId)
        assertTrue(repo.isLoggedIn)
    }

    @Test fun `completeLogin dataSyncId jaringan kosong pakai kandidat`() = runTest {
        val fake = FakeYouTube().apply {
            accountDataSyncIdResult = Result.success("   ")
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, null, "kandidat||kandidat")

        assertEquals(Result.success(account), result)
        assertEquals("kandidat||kandidat", store.loadAuthState().dataSyncId)
    }

    @Test fun `cookie tidak lengkap menghasilkan IncompleteCookies`() = runTest {
        val repo = repository()
        val incompleteCookies = listOf(
            "SID=dummy; HSID=dummy", // tanpa LOGIN_INFO dan tanpa SAPISID
            "LOGIN_INFO=dummy-login-info", // tanpa SAPISID
            "SAPISID=dummy-sapisid", // tanpa LOGIN_INFO
            "",
        )
        for (cookie in incompleteCookies) {
            val result = repo.completeLogin(cookie, "Cgtv", "kandidat||kandidat")
            assertEquals(MusicLoginError.IncompleteCookies, loginError(result), "cookie: $cookie")
        }
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
        assertNull(store.loadAccount())
        assertNull(repo.account.value)
        assertFalse(repo.isLoggedIn)
    }

    @Test fun `accountInfo gagal menghasilkan VerificationFailed dan tidak menyimpan`() = runTest {
        val fake = FakeYouTube().apply {
            accountInfoResult = Result.failure(RuntimeException("respons rusak"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.VerificationFailed, loginError(result))
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
        assertNull(store.loadAccount())
        assertNull(repo.account.value)
    }

    @Test fun `accountInfo gagal IOException menghasilkan Network`() = runTest {
        val fake = FakeYouTube().apply {
            accountInfoResult = Result.failure(IOException("putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.Network, loginError(result))
        assertNull(repo.account.value)
    }

    @Test fun `tanpa dataSyncId jaringan dan kandidat menghasilkan MissingDataSyncId`() = runTest {
        val fake = FakeYouTube().apply {
            accountDataSyncIdResult = Result.failure(RuntimeException("jaringan putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", null)

        assertEquals(MusicLoginError.MissingDataSyncId, loginError(result))
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
        assertNull(store.loadAccount())
        assertNull(repo.account.value)
        assertFalse(repo.isLoggedIn)
    }

    @Test fun `logout menghapus store akun dan flag`() = runTest {
        val fake = FakeYouTube()
        val repo = repository(fake)
        assertEquals(Result.success(account), repo.completeLogin(completeCookie, "Cgtv", null))
        repo.markSessionExpired()
        assertTrue(repo.sessionExpired.value)

        repo.logout()

        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
        assertNull(store.loadAccount())
        assertNull(repo.account.value)
        assertFalse(repo.sessionExpired.value)
        assertFalse(repo.isLoggedIn)
    }

    @Test fun `markSessionExpired dan clearSessionExpired`() {
        val repo = repository()
        assertFalse(repo.sessionExpired.value)
        repo.markSessionExpired()
        assertTrue(repo.sessionExpired.value)
        repo.clearSessionExpired()
        assertFalse(repo.sessionExpired.value)
    }

    @Test fun `onSessionExpired sama dengan markSessionExpired`() {
        val repo = repository()
        repo.onSessionExpired()
        assertTrue(repo.sessionExpired.value)
    }

    @Test fun `account diisi dari store saat konstruksi`() {
        store.saveAuthState(
            PlaybackAuthState(cookie = completeCookie, visitorData = "Cgtv", dataSyncId = "delegated||user"),
        )
        store.saveAccount(account)

        val repo = repository()

        assertEquals(account, repo.account.value)
        assertTrue(repo.isLoggedIn)
    }

    @Test fun `isLoggedIn false bila akun ada tapi state tanpa dataSyncId`() {
        store.saveAuthState(PlaybackAuthState(cookie = completeCookie, visitorData = "Cgtv"))
        store.saveAccount(account)

        val repo = repository()

        assertEquals(account, repo.account.value)
        assertFalse(repo.isLoggedIn)
    }
}
