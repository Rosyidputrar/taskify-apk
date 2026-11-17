package com.rosyidputrar.taskify.core.media.youtube

import android.util.Log
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.core.innertube.utils.hasCompleteYouTubeLoginCookies
import com.rosyidputrar.taskify.core.innertube.utils.hasYouTubeLoginCookie
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
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

    /** YouTube menolak verifikasi akun dengan status HTTP (401/403/5xx). */
    data class HttpRejected(val status: Int) :
        MusicLoginError("YouTube menolak verifikasi akun (HTTP $status). Coba lagi.")

    /** Respons verifikasi bukan JSON / strukturnya tidak dikenali (parse null). */
    data object UnrecognizedResponse :
        MusicLoginError("YouTube membalas respons yang tidak dikenali. Coba lagi.")
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
     *    lewat `youTube.accountInfo()`; gagal → klasifikasi
     *    [classifyVerificationFailure] ([MusicLoginError.Network] /
     *    [MusicLoginError.HttpRejected] / [MusicLoginError.UnrecognizedResponse]
     *    / [MusicLoginError.VerificationFailed]). Bila gagal karena
     *    [MusicLoginError.HttpRejected] atau [MusicLoginError.UnrecognizedResponse],
     *    verifikasi dicoba SEKALI LAGI sesudah [VERIFY_RETRY_DELAY_MS]
     *    (kontrak PLAN D.4); bila percobaan kedua gagal, error yang
     *    dikembalikan adalah dari percobaan kedua.
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
            Log.w(TAG, "completeLogin: cookie tidak lengkap kind=IncompleteCookies parseNull=false")
            return Result.failure(MusicLoginException(MusicLoginError.IncompleteCookies))
        }

        val initial = PlaybackAuthState(
            cookie = normalizedCookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
        ).normalized()

        youTube.applyAuthState(initial.copy(dataSyncId = null))
        val accountInfo = try {
            verifyAccount().getOrElse { cause ->
                val error = classifyVerificationFailure(cause)
                logFailure("verifikasi", error, cause)
                return Result.failure(MusicLoginException(error))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            val error = MusicLoginError.Network
            logFailure("verifikasi", error, e)
            return Result.failure(MusicLoginException(error))
        } catch (e: Exception) {
            val error = classifyVerificationFailure(e)
            logFailure("verifikasi", error, e)
            return Result.failure(MusicLoginException(error))
        }

        var dataSyncIdFailure: Throwable? = null
        val resolvedDataSyncId = try {
            val result = youTube.accountDataSyncId()
            if (result.isFailure) dataSyncIdFailure = result.exceptionOrNull()
            result.getOrNull()?.normalizeDataSyncId()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dataSyncIdFailure = e
            null
        } ?: initial.dataSyncId
            ?: run {
                // Gagal total: jaringan mengembalikan failure, atau respons
                // sukses tapi tidak berisi dataSyncId (parse null).
                val error = dataSyncIdFailure?.let(::classifyVerificationFailure)
                    ?: MusicLoginError.UnrecognizedResponse
                logFailure("dataSyncId", error, dataSyncIdFailure, dataSyncIdFailure == null)
                return Result.failure(MusicLoginException(MusicLoginError.MissingDataSyncId))
            }

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

    /**
     * Verifikasi akun dengan SATU retry (kontrak PLAN D.4): bila percobaan
     * pertama gagal karena [MusicLoginError.HttpRejected] atau
     * [MusicLoginError.UnrecognizedResponse] (mis. sesi server-side belum
     * merambat, atau `account_menu` sempat ditolak), coba sekali lagi
     * sesudah [VERIFY_RETRY_DELAY_MS] dengan state auth yang sama.
     * [MusicLoginError.Network] TIDAK di-retry di sini (transport
     * `withRetry` sudah mengulang kegagalan jaringan transient 3x).
     * Kembalikan hasil percobaan kedua apa adanya, supaya error yang
     * tampil mencerminkan kondisi terakhir. `delay` biasa supaya `runTest`
     * menanganinya dengan waktu virtual.
     */
    private suspend fun verifyAccount(): Result<AccountInfo> {
        val first = youTube.accountInfo()
        if (first.isSuccess) return first
        val error = classifyVerificationFailure(first.exceptionOrNull())
        if (error is MusicLoginError.HttpRejected || error is MusicLoginError.UnrecognizedResponse) {
            delay(VERIFY_RETRY_DELAY_MS)
            return youTube.accountInfo()
        }
        return first
    }

    private fun String.normalizeDataSyncId(): String? =
        PlaybackAuthState(dataSyncId = this).normalized().dataSyncId

    companion object {
        /** Jeda sebelum percobaan verifikasi kedua (kontrak PLAN D.4). */
        const val VERIFY_RETRY_DELAY_MS = 2_000L
    }
}

/** Tag log diagnostik login musik (kontrak PLAN D.3): satu tag konsisten. */
private const val TAG = "MusicLogin"

/**
 * Bedakan penyebab gagal verifikasi akun dari `Throwable` yang dibawa
 * `Result.failure` `accountInfo()` (kontrak PLAN D.2). `accountInfo()`
 * sendiri tidak diubah; klasifikasi dilakukan di sini.
 *
 * - [IOException] (termasuk timeout Ktor) → [MusicLoginError.Network].
 * - Ktor [ResponseException] (`expectSuccess = true` di transport) →
 *   [MusicLoginError.HttpRejected] dengan status HTTP aslinya.
 * - Badan bukan JSON ([SerializationException] dari `parseToJsonElement`)
 *   atau parse null ([IllegalStateException] "info akun tidak ditemukan")
 *   → [MusicLoginError.UnrecognizedResponse].
 * - Sisanya → [MusicLoginError.VerificationFailed] (fallback).
 */
private fun classifyVerificationFailure(cause: Throwable?): MusicLoginError = when {
    cause is IOException -> MusicLoginError.Network
    cause is ResponseException -> MusicLoginError.HttpRejected(cause.response.status.value)
    cause is SerializationException -> MusicLoginError.UnrecognizedResponse
    cause is IllegalStateException &&
        cause.message?.contains("info akun tidak ditemukan") == true ->
        MusicLoginError.UnrecognizedResponse
    else -> MusicLoginError.VerificationFailed
}

/** True bila penyebabnya parse null (`accountInfo()` tidak menemukan akun). */
private fun isParseNull(cause: Throwable?): Boolean =
    cause is IllegalStateException &&
        cause.message?.contains("info akun tidak ditemukan") == true

/**
 * Satu baris log diagnostik per kegagalan `completeLogin` (kontrak PLAN
 * D.3). Hanya jenis + status HTTP + flag parse-null; TIDAK PERNAH nilai
 * cookie, SAPISID, token, visitorData, dataSyncId, nama/email akun, atau
 * badan respons.
 */
private fun logFailure(
    step: String,
    error: MusicLoginError,
    cause: Throwable?,
    parseNull: Boolean = isParseNull(cause),
) {
    val status = (error as? MusicLoginError.HttpRejected)?.status
    val statusPart = if (status != null) " status=$status" else ""
    Log.w(
        TAG,
        "completeLogin: $step gagal kind=${error::class.simpleName}$statusPart parseNull=$parseNull",
    )
}
