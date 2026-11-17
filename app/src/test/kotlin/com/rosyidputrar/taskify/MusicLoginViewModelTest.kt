package com.rosyidputrar.taskify

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginError
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginException
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginRepository
import com.rosyidputrar.taskify.core.media.youtube.YouTubeAuthStore
import com.rosyidputrar.taskify.feature.media.MusicLoginScreenState
import com.rosyidputrar.taskify.feature.media.MusicLoginViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guard [MusicLoginViewModel] (port ArchiveTune) + pemetaan galat ke pesan
 * Indonesia (kontrak PLAN C.8). Repo dipalsukan lewat subclass stub
 * [MusicLoginRepository] — versi penuh Agen A2 mengganti stub ini.
 */
@RunWith(AndroidJUnit4::class)
class MusicLoginViewModelTest {
    private val account = AccountInfo("Kopi Senja", "kopi@contoh.id", "@kopisenja")
    private val cookie = "LOGIN_INFO=abc; SAPISID=xyz"

    private inner class FakeRepo(
        var result: Result<AccountInfo> = Result.success(account),
        var gate: CompletableDeferred<Unit>? = null,
    ) : MusicLoginRepository(YouTube(), YouTubeAuthStore(ApplicationProvider.getApplicationContext())) {
        var calls = 0
        var lastVisitorData: String? = null
        var lastDataSyncId: String? = null

        override suspend fun completeLogin(cookie: String, visitorData: String?, dataSyncId: String?): Result<AccountInfo> {
            calls++
            lastVisitorData = visitorData
            lastDataSyncId = dataSyncId
            gate?.await()
            return result
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun cookieGandaTidakMemulaiLoginDuaKali() {
        val repo = FakeRepo(gate = CompletableDeferred())
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(MusicLoginScreenState.Loading, vm.screenState.value)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(1, repo.calls, "cookie yang sama saat login berjalan tidak boleh start ulang")
        repo.gate?.complete(Unit)
        idle()
        assertEquals(MusicLoginScreenState.Success(account), vm.screenState.value)
        assertEquals(1, repo.calls)
    }

    @Test fun cookieSuksesTidakDiprosesLagi() {
        val repo = FakeRepo()
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(MusicLoginScreenState.Success(account), vm.screenState.value)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(1, repo.calls, "cookie yang sudah sukses jangan diproses lagi")
    }

    @Test fun dataSyncIdBaruMemulaiUlangBilaCookieAktifAda() {
        val repo = FakeRepo(gate = CompletableDeferred())
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(1, repo.calls)
        vm.onDataSyncIdExtracted("delegated||user")
        idle()
        assertEquals(2, repo.calls, "dataSyncId baru memicu startLogin ulang")
        assertEquals("delegated||user", repo.lastDataSyncId)
        repo.gate?.complete(Unit)
        idle()
        assertEquals(MusicLoginScreenState.Success(account), vm.screenState.value)
    }

    @Test fun dataSyncIdSamaTidakMemulaiUlang() {
        val repo = FakeRepo()
        val vm = MusicLoginViewModel(repo)
        vm.onVisitorDataExtracted("visitor-1")
        vm.onDataSyncIdExtracted("delegated||user")
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(1, repo.calls)
        assertEquals("visitor-1", repo.lastVisitorData)
        vm.onDataSyncIdExtracted("delegated||user")
        idle()
        assertEquals(1, repo.calls, "dataSyncId yang sama tidak memicu apa-apa")
    }

    @Test fun errorMappingKePesanIndonesia() {
        val cases = listOf(
            MusicLoginError.IncompleteCookies to "Login belum lengkap. Coba masuk lagi dan pastikan memakai akun Google.",
            MusicLoginError.VerificationFailed to "Akun tidak bisa diverifikasi. Periksa koneksi lalu coba lagi.",
            MusicLoginError.MissingDataSyncId to "Sesi akun tidak lengkap. Coba keluar lalu masuk lagi.",
            MusicLoginError.Network to "Tidak bisa menghubungi YouTube. Periksa koneksi.",
        )
        for ((error, message) in cases) {
            val repo = FakeRepo(result = Result.failure(MusicLoginException(error)))
            val vm = MusicLoginViewModel(repo)
            vm.onCookiesCaptured(cookie)
            idle()
            val state = vm.screenState.value
            assertTrue(state is MusicLoginScreenState.Error, "harusnya Error untuk $error")
            assertEquals(message, state.message)
        }
    }

    @Test fun errorMembawaRawDetailBilaAda() {
        val repo = FakeRepo(result = Result.failure(MusicLoginException(MusicLoginError.HttpRejected(403), "HTTP 403")))
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        val state = vm.screenState.value
        assertTrue(state is MusicLoginScreenState.Error, "harusnya Error untuk HttpRejected")
        assertEquals(MusicLoginError.HttpRejected(403).message, state.message)
        assertEquals("HTTP 403", state.rawDetail)
    }

    @Test fun errorRawDetailNullBilaTidakAda() {
        val repo = FakeRepo(result = Result.failure(MusicLoginException(MusicLoginError.VerificationFailed)))
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        val state = vm.screenState.value
        assertTrue(state is MusicLoginScreenState.Error, "harusnya Error untuk VerificationFailed")
        assertEquals(null, state.rawDetail)
    }

    @Test fun galatUmumDipetakanKeVerificationFailed() {
        val repo = FakeRepo(result = Result.failure(RuntimeException("boom")))
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(
            MusicLoginScreenState.Error(MusicLoginError.VerificationFailed.message),
            vm.screenState.value,
        )
    }

    @Test fun resetMembukaJalanLoginUlangCookieYangSama() {
        val repo = FakeRepo()
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(MusicLoginScreenState.Success(account), vm.screenState.value)
        vm.reset()
        assertEquals(MusicLoginScreenState.Idle, vm.screenState.value)
        vm.onCookiesCaptured(cookie)
        idle()
        assertEquals(2, repo.calls, "sesudah reset, cookie yang sama boleh login lagi")
        assertEquals(MusicLoginScreenState.Success(account), vm.screenState.value)
    }

    @Test fun cookieKosongAtauNullDiabaikan() {
        val repo = FakeRepo()
        val vm = MusicLoginViewModel(repo)
        vm.onCookiesCaptured(null)
        vm.onCookiesCaptured("   ")
        vm.onVisitorDataExtracted("null")
        vm.onDataSyncIdExtracted("null")
        idle()
        assertEquals(0, repo.calls)
        assertEquals(MusicLoginScreenState.Idle, vm.screenState.value)
    }
}
