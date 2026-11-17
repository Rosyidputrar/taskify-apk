package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.core.innertube.utils.hasCompleteYouTubeLoginCookies
import com.rosyidputrar.taskify.core.innertube.utils.hasYouTubeLoginCookie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Kegagalan login akun musik (kontrak PLAN C.1). Pesan berbahasa Indonesia,
 * dipakai apa adanya oleh UI — jangan diubah tanpa lapor koordinator.
 */
sealed class MusicLoginError(val message: String) {
    data object IncompleteCookies :
        MusicLoginError("Login belum lengkap. Coba masuk lagi dan pastikan memakai akun Google.")

    data object VerificationFailed :
        MusicLoginError("Akun tidak bisa diverifikasi. Periksa koneksi lalu coba lagi.")

    data object MissingDataSyncId :
        MusicLoginError("Sesi akun tidak lengkap. Coba keluar lalu masuk lagi.")

    data object Network :
        MusicLoginError("Tidak bisa menghubungi YouTube. Periksa koneksi.")
}

/** Pembungkus [MusicLoginError] supaya [Result.failure] membawa tipenya. */
class MusicLoginException(val error: MusicLoginError) : Exception(error.message)

/**
 * Hook kedaluwarsa sesi (kontrak PLAN C.6). Pemutar memanggil ini bila
 * resolve/play gagal karena login kedaluwarsa; repository cukup menaikkan
 * flag [MusicLoginRepository.sessionExpired], VM yang menampilkan prompt.
 */
fun interface MusicLoginEvents {
    fun onSessionExpired()
}

/**
 * Repository login akun musik (kontrak PLAN C.5, Tahap 4).
 *
 * Alur [completeLogin] persis ArchiveTune
 * `YouTubeLoginRepository.completeLogin` (v15.1.0): validasi cookie →
 * verifikasi akun lewat `youTube.accountInfo()` dengan `dataSyncId = null`
 * dulu → resolve `dataSyncId` (jaringan, fallback kandidat WebView) →
 * persist state + akun → update flows.
 *
 * Satu akun saja (tanpa multi-akun tersimpan). [logout] membersihkan total:
 * cookie/visitorData/dataSyncId/poToken + identitas akun; `visitorData`
 * akan diambil lagi oleh [VisitorDataBootstrap] saat start berikutnya.
 * Pemutar TIDAK dihentikan di sini — pemanggil (VM) yang menghentikannya.
 *
 * `open` supaya tes VM bisa memalsukan [completeLogin] lewat subclass
 * (MusicLoginViewModelTest.FakeRepo); perilaku produksi tidak berubah.
 */
open class MusicLoginRepository(
    private val youTube: YouTube,
    private val authStore: YouTubeAuthStore,
) : MusicLoginEvents {

    private val _account = MutableStateFlow(authStore.loadAccount())
    /** Akun yang login, atau null bila belum login; diisi dari store saat konstruksi. */
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    /** True = cookie kedaluwarsa, minta login ulang. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /** True bila ada akun tersimpan DAN state auth punya konteks login playback. */
    val isLoggedIn: Boolean
        get() = _account.value != null && authStore.loadAuthState().hasPlaybackLoginContext

    /**
     * Selesaikan login dari cookie hasil WebView.
     *
     * 1. Normalisasi cookie; gagal bila cookie login tidak lengkap →
     *    [MusicLoginError.IncompleteCookies].
     * 2. Terapkan state awal dengan `dataSyncId = null`, lalu verifikasi akun
     *    lewat `youTube.accountInfo()`; gagal → [MusicLoginError.VerificationFailed],
     *    kecuali [IOException]/timeout → [MusicLoginError.Network].
     * 3. Resolve `dataSyncId`: jaringan dulu, fallback kandidat WebView;
     *    dua-duanya kosong → [MusicLoginError.MissingDataSyncId].
     * 4. Terapkan + simpan state akhir dan akun, naikkan [account],
     *    turunkan [sessionExpired].
     */
    open suspend fun completeLogin(
        cookie: String,
        visitorData: String?,
        dataSyncId: String?,
    ): Result<AccountInfo> {
        val normalizedCookie = cookie.trim()
        if (!hasYouTubeLoginCookie(normalizedCookie) || !hasCompleteYouTubeLoginCookies(normalizedCookie)) {
            return Result.failure(MusicLoginException(MusicLoginError.IncompleteCookies))
        }

        val initial = PlaybackAuthState(
            cookie = normalizedCookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
        ).normalized()

        youTube.applyAuthState(initial.copy(dataSyncId = null))
        val accountInfo = try {
            youTube.accountInfo().getOrElse { cause ->
                val error = if (cause is IOException) MusicLoginError.Network else MusicLoginError.VerificationFailed
                return Result.failure(MusicLoginException(error))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            return Result.failure(MusicLoginException(MusicLoginError.Network))
        } catch (_: Exception) {
            return Result.failure(MusicLoginException(MusicLoginError.VerificationFailed))
        }

        val resolvedDataSyncId = try {
            youTube.accountDataSyncId().getOrNull()?.normalizeDataSyncId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: initial.dataSyncId
            ?: return Result.failure(MusicLoginException(MusicLoginError.MissingDataSyncId))

        val final = initial.copy(dataSyncId = resolvedDataSyncId).normalized()
        youTube.applyAuthState(final)
        authStore.saveAuthState(final)
        authStore.saveAccount(accountInfo)
        _account.value = accountInfo
        _sessionExpired.value = false
        return Result.success(accountInfo)
    }

    /**
     * Keluar: hapus seluruh konteks auth + identitas akun, turunkan
     * [account] dan [sessionExpired]. Pemutar dihentikan pemanggil (VM).
     */
    fun logout() {
        authStore.clear()
        authStore.clearAccount()
        _account.value = null
        _sessionExpired.value = false
    }

    /** Tandai sesi kedaluwarsa (dipanggil pemutar/VM saat 401/403/LoginRequired). */
    fun markSessionExpired() {
        _sessionExpired.value = true
    }

    /** Bersihkan flag kedaluwarsa (mis. setelah login ulang sukses). */
    fun clearSessionExpired() {
        _sessionExpired.value = false
    }

    override fun onSessionExpired() = markSessionExpired()

    private fun String.normalizeDataSyncId(): String? =
        PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
}
