package com.rosyidputrar.taskify.core.media.mytube

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.UnrecognizedAccountResponseException
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.innertube.pages.SelectedAccount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `MusicLoginRepository` (Tahap 4) dengan `MyTube` palsu: tidak ada
 * panggilan jaringan sungguhan. Cookie di fixture ini DUMMY, bukan cookie asli.
 *
 * Catatan: fake di sini meng-override `accountInfo()`/`accountDataSyncId()`
 * kontrak PLAN C.3 (Agen A1); kompilasi penuh file ini menunggu metode itu
 * mendarat di `MyTube`.
 */
@RunWith(AndroidJUnit4::class)
class MusicLoginRepositoryTest {
    private lateinit var context: Context
    private lateinit var store: MyTubeAuthStore

    // Cookie dummy SESI PENUH (PLAN §C.2): LOGIN_INFO + satu SID + HSID +
    // SSID + satu APISID. Sesi parsial (mis. hanya LOGIN_INFO + PAPISID)
    // DITOLAK gate sesi penuh sebelum `accountInfo()`.
    private val completeCookie =
        "LOGIN_INFO=dummy-login-info; SAPISID=dummy-sapisid; SID=dummy-sid; HSID=dummy-hsid; SSID=dummy-ssid"
    // Nama cookie terurut dari `completeCookie` (dipakai ekspektasi rawDetail).
    private val completeCookieNames = "HSID, LOGIN_INFO, SAPISID, SID, SSID"
    // Tiga baris diagnostik wire (Agen C) di akhir rawDetail untuk
    // `completeCookie`: header Cookie terlampir dari state sesi penuh,
    // hash dari SAPISID, origin WEB_REMIX.
    private val diagnosticsFull =
        "cookie terlampir: ya\nhash dari: SAPISID\nhash origin: https://music.youtube.com"
    private val account = AccountInfo(
        name = "Nama Tes",
        email = "tes@example.com",
        channelHandle = "@tes",
        thumbnailUrl = "https://example.com/foto.jpg",
    )

    private class FakeMyTube : MyTube() {
        var accountInfoResult: Result<AccountInfo> = Result.success(
            AccountInfo("Nama Tes", "tes@example.com", "@tes", "https://example.com/foto.jpg"),
        )
        /** Antrean hasil per pemanggilan `accountInfo()` (tes retry); kosong → [accountInfoResult]. */
        var accountInfoResults: ArrayDeque<Result<AccountInfo>>? = null
        var accountInfoCalls = 0
        var accountDataSyncIdResult: Result<String> = Result.success("delegated||user")
        /** State auth yang sedang diterapkan saat `accountInfo()` dipanggil. */
        var authStateAtAccountInfo: PlaybackAuthState? = null

        override suspend fun accountInfo(authState: PlaybackAuthState): Result<AccountInfo> {
            accountInfoCalls++
            authStateAtAccountInfo = currentAuthState()
            return accountInfoResults?.removeFirstOrNull() ?: accountInfoResult
        }

        override suspend fun accountDataSyncId(authState: PlaybackAuthState): Result<String> =
            accountDataSyncIdResult

        /** Hasil `selectedAccount()` (fallback `accounts_list`); gagal default. */
        var selectedAccountResult: Result<SelectedAccount> =
            Result.failure(IllegalStateException("tidak ada akun terpilih"))
        var selectedAccountCalls = 0

        override suspend fun selectedAccount(authState: PlaybackAuthState): Result<SelectedAccount> {
            selectedAccountCalls++
            return selectedAccountResult
        }
    }

    /** `ResponseException` asli (4xx/5xx) dari MockEngine, seperti `expectSuccess = true` produksi. */
    private suspend fun httpRejected(status: Int): ResponseException {
        val client = HttpClient(MockEngine) {
            expectSuccess = true
            engine {
                addHandler { respond("", HttpStatusCode.fromValue(status)) }
            }
        }
        try {
            client.get("https://music.youtube.com")
            error("MockEngine seharusnya melempar ResponseException")
        } catch (e: ResponseException) {
            return e
        } finally {
            client.close()
        }
    }

    /** `SerializationException` asli dari badan yang bukan JSON. */
    private fun notJsonException(): SerializationException =
        try {
            Json.parseToJsonElement("{bukan json")
            error("seharusnya gagal parse")
        } catch (e: SerializationException) {
            e
        }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(MyTubeAuthStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = MyTubeAuthStore(context)
    }

    private fun repository(fake: FakeMyTube = FakeMyTube()) = MusicLoginRepository(fake, store)

    private fun loginError(result: Result<AccountInfo>): MusicLoginError? =
        (result.exceptionOrNull() as? MusicLoginException)?.error

    private fun loginException(result: Result<AccountInfo>): MusicLoginException? =
        result.exceptionOrNull() as? MusicLoginException

    @Test fun `completeLogin sukses menyimpan state akhir dan akun`() = runTest {
        val fake = FakeMyTube()
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
        val fake = FakeMyTube().apply {
            accountDataSyncIdResult = Result.failure(RuntimeException("jaringan putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(account), result)
        assertEquals("kandidat||kandidat", store.loadAuthState().dataSyncId)
        assertTrue(repo.isLoggedIn)
    }

    @Test fun `completeLogin dataSyncId jaringan kosong pakai kandidat`() = runTest {
        val fake = FakeMyTube().apply {
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
        val fake = FakeMyTube().apply {
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
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(IOException("putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.Network, loginError(result))
        assertNull(repo.account.value)
    }

    @Test fun `accountInfo ditolak HTTP 403 menghasilkan HttpRejected 403`() = runTest {
        val rejected = httpRejected(403)
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(rejected)
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.HttpRejected(403), loginError(result))
        assertEquals(
            "YouTube menolak verifikasi akun (HTTP 403). Coba lagi.",
            loginError(result)?.message,
        )
        assertNull(repo.account.value)
    }

    @Test fun `accountInfo ditolak HTTP 500 menghasilkan HttpRejected 500`() = runTest {
        val rejected = httpRejected(500)
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(rejected)
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.HttpRejected(500), loginError(result))
        assertEquals(
            "YouTube menolak verifikasi akun (HTTP 500). Coba lagi.",
            loginError(result)?.message,
        )
    }

    @Test fun `accountInfo ditolak HTTP 403 menghasilkan rawDetail HTTP 403`() = runTest {
        val rejected = httpRejected(403)
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(rejected)
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.HttpRejected(403), loginError(result))
        assertEquals("HTTP 403\n$diagnosticsFull", loginException(result)?.rawDetail)
        assertNull(repo.account.value)
    }

    @Test fun `accountInfo ditolak HTTP 500 menghasilkan rawDetail HTTP 500`() = runTest {
        val rejected = httpRejected(500)
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(rejected)
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.HttpRejected(500), loginError(result))
        assertEquals("HTTP 500\n$diagnosticsFull", loginException(result)?.rawDetail)
    }

    @Test fun `accountInfo gagal IOException menghasilkan Network tanpa rawDetail`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(IOException("putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.Network, loginError(result))
        assertNull(loginException(result)?.rawDetail)
    }

    @Test fun `accountInfo gagal VerificationFailed tanpa rawDetail`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(RuntimeException("respons rusak"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.VerificationFailed, loginError(result))
        assertNull(loginException(result)?.rawDetail)
    }

    @Test fun `scrubRawDetail menghapus nilai cookie SAPISID`() {
        val scrubbed = scrubRawDetail("badan aneh SAPISID=RAHASIA123 lanjut")

        assertFalse(scrubbed.contains("RAHASIA123"))
        assertTrue(scrubbed.contains("SAPISID=<redacted>"))
    }

    @Test fun `scrubRawDetail menghapus Authorization dan SAPISIDHASH`() {
        val scrubbed = scrubRawDetail(
            "Authorization: Bearer TOKENRAHASIA\nSAPISIDHASH 1700000000_ABCDEF1234567890",
        )

        assertFalse(scrubbed.contains("TOKENRAHASIA"))
        assertFalse(scrubbed.contains("ABCDEF1234567890"))
        assertTrue(scrubbed.contains("Authorization: <redacted>"))
        assertTrue(scrubbed.contains("SAPISIDHASH <redacted>"))
    }

    @Test fun `scrubRawDetail menghapus nilai header cookie`() {
        val scrubbed = scrubRawDetail("cookie=SAPISID=RAHASIA123; SID=abc; lanjutan teks")

        assertFalse(scrubbed.contains("RAHASIA123"))
        assertTrue(scrubbed.contains("cookie=<redacted>"))
    }

    @Test fun `scrubRawDetail memotong ke 500 karakter dan kosong tetap kosong`() {
        val panjang = "x".repeat(600)
        assertEquals(500, scrubRawDetail(panjang).length)
        assertEquals("", scrubRawDetail(""))
        assertEquals("", scrubRawDetail(null))
    }

    @Test fun `fallback sukses accountInfo gagal lalu selectedAccount sukses`() = runTest {
        val fallbackAccount = AccountInfo(
            "Nama Fallback", "fallback@example.com", "@fallback", "https://example.com/fb.jpg",
        )
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException("potongan"),
            )
            selectedAccountResult = Result.success(
                SelectedAccount(fallbackAccount, "delegated||fallback"),
            )
            // Langkah 3 resolve HARUS dilewati bila fallback membawa dataSyncId:
            // buktikan dengan kegagalan jaringan yang tidak boleh muncul.
            accountDataSyncIdResult = Result.failure(RuntimeException("jangan dipanggil"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(fallbackAccount), result)
        assertEquals(fallbackAccount, repo.account.value)
        assertEquals(2, fake.accountInfoCalls) // retry account_menu dulu, baru fallback
        assertEquals(1, fake.selectedAccountCalls)
        assertEquals("delegated||fallback", store.loadAuthState().dataSyncId)
    }

    @Test fun `fallback tanpa dataSyncId lanjut resolve jaringan seperti biasa`() = runTest {
        val fallbackAccount = AccountInfo(
            "Nama Fallback", "fallback@example.com", "@fallback", "https://example.com/fb.jpg",
        )
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException("potongan"),
            )
            selectedAccountResult = Result.success(SelectedAccount(fallbackAccount, null))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(fallbackAccount), result)
        assertEquals("delegated||user", store.loadAuthState().dataSyncId)
    }

    @Test fun `fallback gagal mengembalikan UnrecognizedResponse dengan rawDetail account_menu`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException("potongan menu"),
            )
            selectedAccountResult = Result.failure(
                UnrecognizedAccountResponseException("potongan fallback JANGAN MUNCUL"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        assertEquals(
            "potongan menu\ncookie: $completeCookieNames\n$diagnosticsFull",
            loginException(result)?.rawDetail,
        )
        assertNull(repo.account.value)
    }

    @Test fun `sesi parsial LOGIN_INFO plus satu PAPISID ditolak sebelum accountInfo`() = runTest {
        // Kondisi gagal tes HP 18 Nov 01:54: lolos gate lama, tapi server
        // tidak mengenalinya (`"logged_in":"0"`).
        val fake = FakeMyTube()
        val repo = repository(fake)
        val partialCookie = "LOGIN_INFO=dummy-login-info; __Secure-3PAPISID=dummy-papisid"

        val result = repo.completeLogin(partialCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.IncompleteCookies, loginError(result))
        assertEquals(
            "cookie: LOGIN_INFO, __Secure-3PAPISID\n" +
                "cookie terlampir: tidak\nhash dari: __Secure-3PAPISID\nhash origin: https://music.youtube.com",
            loginException(result)?.rawDetail,
        )
        assertEquals(0, fake.accountInfoCalls)
        assertEquals(0, fake.selectedAccountCalls)
        assertFalse(loginException(result)?.rawDetail?.contains("dummy-login-info") == true)
        assertFalse(loginException(result)?.rawDetail?.contains("dummy-papisid") == true)
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
        assertNull(store.loadAccount())
        assertNull(repo.account.value)
        assertFalse(repo.isLoggedIn)
    }

    @Test fun `sesi parsial tanpa HSID dan SSID ditolak sebelum accountInfo`() = runTest {
        val fake = FakeMyTube()
        val repo = repository(fake)
        val partialCookie = "LOGIN_INFO=dummy-login-info; SAPISID=dummy-sapisid; SID=dummy-sid"

        val result = repo.completeLogin(partialCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.IncompleteCookies, loginError(result))
        assertEquals(
            "cookie: LOGIN_INFO, SAPISID, SID\n" +
                "cookie terlampir: tidak\nhash dari: SAPISID\nhash origin: https://music.youtube.com",
            loginException(result)?.rawDetail,
        )
        assertEquals(0, fake.accountInfoCalls)
        assertEquals(0, fake.selectedAccountCalls)
    }

    @Test fun `UnrecognizedResponse tanpa cuplikan rawDetail hanya baris cookie`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                IllegalStateException("info akun tidak ditemukan, pengguna mungkin belum login"),
            )
            selectedAccountResult = Result.failure(
                IllegalStateException("tidak ada akun terpilih"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        assertEquals("cookie: $completeCookieNames\n$diagnosticsFull", loginException(result)?.rawDetail)
    }

    @Test fun `rawDetail UnrecognizedResponse tidak pernah mengandung nilai cookie SID`() = runTest {
        val cookieDenganNilaiRahasia =
            "LOGIN_INFO=dummy-login-info; SAPISID=dummy-sapisid; SID=RAHASIA123; HSID=dummy-hsid; SSID=dummy-ssid"
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException("potongan"),
            )
            selectedAccountResult = Result.failure(
                IllegalStateException("tidak ada akun terpilih"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(cookieDenganNilaiRahasia, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        val rawDetail = loginException(result)?.rawDetail
        assertEquals("potongan\ncookie: $completeCookieNames\n$diagnosticsFull", rawDetail)
        assertTrue(rawDetail != null)
        assertFalse(rawDetail.contains("RAHASIA123"))
        assertFalse(scrubRawDetail("SID=RAHASIA123; lanjutan").contains("RAHASIA123"))
    }

    @Test fun `rawDetail tidak pernah mengandung nilai cookie`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException(
                    "respons aneh berisi SAPISID=RAHASIA123 di tengah",
                ),
            )
            selectedAccountResult = Result.failure(
                IllegalStateException("tidak ada akun terpilih"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        val rawDetail = loginException(result)?.rawDetail
        assertTrue(rawDetail != null)
        assertFalse(rawDetail.contains("RAHASIA123"))
    }

    // ---- Diagnostik wire Agen C (PLAN §C): tiga baris baru di rawDetail ----

    @Test fun `rawDetail sesi penuh memuat cookie terlampir hash dari dan origin`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException("potongan"),
            )
            selectedAccountResult = Result.failure(
                IllegalStateException("tidak ada akun terpilih"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        assertEquals(
            "potongan\ncookie: $completeCookieNames\n$diagnosticsFull",
            loginException(result)?.rawDetail,
        )
    }

    @Test fun `rawDetail hash dari memakai Secure-3PAPISID bila itu satu-satunya PAPISID`() = runTest {
        val cookie3p =
            "LOGIN_INFO=dummy-login-info; __Secure-3PAPISID=dummy-papisid; SID=dummy-sid; HSID=dummy-hsid; SSID=dummy-ssid"
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException("potongan"),
            )
            selectedAccountResult = Result.failure(
                IllegalStateException("tidak ada akun terpilih"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(cookie3p, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        assertEquals(
            "potongan\ncookie: HSID, LOGIN_INFO, SID, SSID, __Secure-3PAPISID\n" +
                "cookie terlampir: ya\nhash dari: __Secure-3PAPISID\nhash origin: https://music.youtube.com",
            loginException(result)?.rawDetail,
        )
    }

    @Test fun `cookie hanya APISID tanpa PAPISID ditolak gate pertama`() = runTest {
        // `APISID` saja TIDAK lolos gate pertama (`hasCompleteMyTubeLoginCookies`
        // menuntut LOGIN_INFO + satu PAPISID: SAPISID / __Secure-3PAPISID /
        // __Secure-1PAPISID) — jadi tidak pernah mencapai gate sesi penuh
        // maupun verifikasi, dan rawDetail tetap null seperti gate pertama
        // lainnya. Catatan: karena gate pertama sudah menuntut PAPISID,
        // `hash dari: - (tidak ada)` TIDAK PERNAH muncul lewat
        // `completeLogin` — cabang itu hanya terjangkau lewat helper
        // (`verificationDiagnostics(null/"", ...)`), yang diuji langsung di
        // `verificationDiagnostics tanpa cookie hash dari tidak ada dan tidak terlampir`.
        val cookieApisidOnly =
            "LOGIN_INFO=dummy-login-info; APISID=dummy-apisid; SID=dummy-sid; HSID=dummy-hsid; SSID=dummy-ssid"
        val fake = FakeMyTube()
        val repo = repository(fake)

        val result = repo.completeLogin(cookieApisidOnly, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.IncompleteCookies, loginError(result))
        assertNull(loginException(result)?.rawDetail)
        assertEquals(0, fake.accountInfoCalls)
        assertEquals(PlaybackAuthState.EMPTY, store.loadAuthState())
    }

    @Test fun `hashCookieName mengikuti urutan myTubeLoginCookieValue`() {
        // SAPISID menang atas __Secure-3PAPISID bila dua-duanya ada.
        assertEquals(
            "SAPISID",
            hashCookieName("SAPISID=a; __Secure-3PAPISID=b; __Secure-1PAPISID=c"),
        )
        assertEquals("__Secure-3PAPISID", hashCookieName("__Secure-3PAPISID=b; __Secure-1PAPISID=c"))
        assertEquals("__Secure-1PAPISID", hashCookieName("__Secure-1PAPISID=c"))
        assertNull(hashCookieName("APISID=a; SID=b"))
        assertNull(hashCookieName(""))
        assertNull(hashCookieName(null))
    }

    @Test fun `verificationDiagnostics tanpa cookie hash dari tidak ada dan tidak terlampir`() {
        assertEquals(
            "cookie terlampir: tidak\nhash dari: - (tidak ada)\nhash origin: https://music.youtube.com",
            verificationDiagnostics(null, cookieAttached = false),
        )
        assertEquals(
            "cookie terlampir: tidak\nhash dari: - (tidak ada)\nhash origin: https://music.youtube.com",
            verificationDiagnostics("", cookieAttached = false),
        )
        assertEquals(
            "cookie terlampir: ya\nhash dari: SAPISID\nhash origin: https://music.youtube.com",
            verificationDiagnostics("SAPISID=dummy; SID=dummy", cookieAttached = true),
        )
    }

    @Test fun `rawDetail diagnostik tidak pernah mengandung nilai cookie dummy`() = runTest {
        val cookieRahasia =
            "LOGIN_INFO=NILAI_LOGIN_RAHASIA; SAPISID=NILAI_SAPISID_RAHASIA; SID=NILAI_SID_RAHASIA; HSID=NILAI_HSID_RAHASIA; SSID=NILAI_SSID_RAHASIA"
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                UnrecognizedAccountResponseException(
                    "respons aneh berisi SAPISID=NILAI_SNIPPET_RAHASIA di tengah",
                ),
            )
            selectedAccountResult = Result.failure(
                IllegalStateException("tidak ada akun terpilih"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(cookieRahasia, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        val rawDetail = loginException(result)?.rawDetail
        assertTrue(rawDetail != null)
        // Tiga baris diagnostik tetap ada, dengan nama saja.
        assertTrue(rawDetail.contains("cookie terlampir: ya"))
        assertTrue(rawDetail.contains("hash dari: SAPISID"))
        assertTrue(rawDetail.contains("hash origin: https://music.youtube.com"))
        // TIDAK SATU PUN nilai dummy boleh lolos, dari cookie maupun cuplikan.
        for (nilai in listOf(
            "NILAI_LOGIN_RAHASIA", "NILAI_SAPISID_RAHASIA", "NILAI_SID_RAHASIA",
            "NILAI_HSID_RAHASIA", "NILAI_SSID_RAHASIA", "NILAI_SNIPPET_RAHASIA",
        )) {
            assertFalse(rawDetail.contains(nilai), "nilai bocor di rawDetail: $nilai")
        }
    }

    @Test fun `accountInfo parse null menghasilkan UnrecognizedResponse`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(
                IllegalStateException("info akun tidak ditemukan, pengguna mungkin belum login"),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        assertEquals(
            "YouTube membalas respons yang tidak dikenali. Coba lagi.",
            loginError(result)?.message,
        )
        assertNull(repo.account.value)
    }

    @Test fun `accountInfo badan bukan JSON menghasilkan UnrecognizedResponse`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(notJsonException())
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.UnrecognizedResponse, loginError(result))
        assertNull(repo.account.value)
    }

    @Test fun `retry verifikasi HttpRejected lalu sukses menghasilkan sukses`() = runTest {
        val rejected = httpRejected(403)
        val fake = FakeMyTube().apply {
            accountInfoResults = ArrayDeque(
                listOf(Result.failure(rejected), Result.success(account)),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(account), result)
        assertEquals(2, fake.accountInfoCalls)
        assertEquals(account, repo.account.value)
        assertEquals("delegated||user", store.loadAuthState().dataSyncId)
    }

    @Test fun `retry verifikasi UnrecognizedResponse lalu sukses menghasilkan sukses`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResults = ArrayDeque(
                listOf(
                    Result.failure(
                        IllegalStateException("info akun tidak ditemukan, pengguna mungkin belum login"),
                    ),
                    Result.success(account),
                ),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(Result.success(account), result)
        assertEquals(2, fake.accountInfoCalls)
    }

    @Test fun `retry verifikasi gagal dua kali mengembalikan error percobaan kedua`() = runTest {
        val first = httpRejected(403)
        val second = httpRejected(401)
        val fake = FakeMyTube().apply {
            accountInfoResults = ArrayDeque(
                listOf(Result.failure(first), Result.failure(second)),
            )
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        // Bukan 403 dari percobaan pertama, tapi 401 dari percobaan kedua.
        assertEquals(MusicLoginError.HttpRejected(401), loginError(result))
        assertEquals(2, fake.accountInfoCalls)
        assertNull(repo.account.value)
    }

    @Test fun `verifikasi gagal Network TIDAK di-retry`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(IOException("putus"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.Network, loginError(result))
        assertEquals(1, fake.accountInfoCalls)
    }

    @Test fun `verifikasi gagal lain TIDAK di-retry`() = runTest {
        val fake = FakeMyTube().apply {
            accountInfoResult = Result.failure(RuntimeException("respons rusak"))
        }
        val repo = repository(fake)

        val result = repo.completeLogin(completeCookie, "Cgtv", "kandidat||kandidat")

        assertEquals(MusicLoginError.VerificationFailed, loginError(result))
        assertEquals(1, fake.accountInfoCalls)
    }

    @Test fun `tanpa dataSyncId jaringan dan kandidat menghasilkan MissingDataSyncId`() = runTest {
        val fake = FakeMyTube().apply {
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
        val fake = FakeMyTube()
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
