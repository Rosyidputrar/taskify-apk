package com.rosyidputrar.taskify.core.media.mytube

import android.util.Log
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.UnrecognizedAccountResponseException
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import com.rosyidputrar.taskify.core.innertube.utils.cookieNames
import com.rosyidputrar.taskify.core.innertube.utils.hasCompleteMyTubeLoginCookies
import com.rosyidputrar.taskify.core.innertube.utils.hasFullMyTubeLoginSession
import com.rosyidputrar.taskify.core.innertube.utils.hasMyTubeLoginCookie
import com.rosyidputrar.taskify.core.innertube.utils.parseCookieString
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

/**
 * Pembungkus [MusicLoginError] supaya [Result.failure] membawa tipenya.
 * [rawDetail] adalah teks mentah penyebab error yang SUDAH di-scrub
 * (status HTTP atau cuplikan badan respons tanpa nilai cookie/token),
 * ditampilkan apa adanya oleh UI di bawah pesan ramah. Default null
 * supaya call-site lama tetap kompilasi. JANGAN PERNAH isi dari cookie,
 * `normalizedCookie`, atau badan mentah tanpa scrub.
 */
class MusicLoginException(val error: MusicLoginError, val rawDetail: String? = null) :
    Exception(error.message)

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
 * `MyTubeLoginRepository.completeLogin` (v15.1.0): validasi cookie →
 * verifikasi akun lewat `myTube.accountInfo()` dengan `dataSyncId = null`
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
    private val myTube: MyTube,
    private val authStore: MyTubeAuthStore,
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
     *    [MusicLoginError.IncompleteCookies]. Gate lama
     *    (`hasMyTubeLoginCookie` / `hasCompleteMyTubeLoginCookies`)
     *    dipertahankan, lalu pertahanan kedua: sesi harus PENUH
     *    ([hasFullMyTubeLoginSession] — LOGIN_INFO + satu SID + HSID +
     *    SSID + satu APISID); sesi parsial ditolak di sini dengan
     *    rawDetail `"cookie: <nama, dipisah koma>"` (nama saja, tanpa
     *    nilai) dan TIDAK PERNAH mencapai `myTube.accountInfo()`.
     * 2. Terapkan state awal dengan `dataSyncId = null`, lalu verifikasi akun
     *    lewat `myTube.accountInfo()`; gagal → klasifikasi
     *    [classifyVerificationFailure] ([MusicLoginError.Network] /
     *    [MusicLoginError.HttpRejected] / [MusicLoginError.UnrecognizedResponse]
     *    / [MusicLoginError.VerificationFailed]). Bila gagal karena
     *    [MusicLoginError.HttpRejected] atau [MusicLoginError.UnrecognizedResponse],
     *    verifikasi dicoba SEKALI LAGI sesudah [VERIFY_RETRY_DELAY_MS]
     *    (kontrak PLAN D.4); bila percobaan kedua gagal, error yang
     *    dikembalikan adalah dari percobaan kedua. Bila percobaan kedua
     *    gagal [MusicLoginError.UnrecognizedResponse], verifikasi dicoba
     *    lewat fallback `myTube.selectedAccount()` (`accounts_list`);
     *    sukses → lanjut dengan akun + `dataSyncId` itu.
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
        if (!hasMyTubeLoginCookie(normalizedCookie) || !hasCompleteMyTubeLoginCookies(normalizedCookie)) {
            Log.w(TAG, "completeLogin: cookie tidak lengkap kind=IncompleteCookies parseNull=false")
            return Result.failure(MusicLoginException(MusicLoginError.IncompleteCookies))
        }
        // Pertahanan kedua (PLAN §C.3 butir 1): sesi parsial yang lolos
        // gate lama (LOGIN_INFO + satu PAPISID) DITOLAK di sini, sebelum
        // `accountInfo()` — tes HP 18 Nov 01:54 membuktikan server tidak
        // mengenali sesi seperti itu (`"logged_in":"0"` di `account_menu`)
        // padahal WebView login berhasil. rawDetail hanya berisi NAMA
        // cookie (dari `cookieNames`), tanpa nilai.
        val names = cookieNames(normalizedCookie)
        if (!hasFullMyTubeLoginSession(normalizedCookie)) {
            // Diagnostik wire (Agen C): request verifikasi TIDAK PERNAH
            // dikirim untuk sesi parsial, jadi header Cookie tidak terlampir.
            val detail = "cookie: ${names.joinToString(", ")}\n" +
                verificationDiagnostics(normalizedCookie, cookieAttached = false)
            logFailure(
                "completeLogin",
                MusicLoginError.IncompleteCookies,
                null,
                rawDetail = detail,
                cookies = names,
            )
            return Result.failure(MusicLoginException(MusicLoginError.IncompleteCookies, detail))
        }

        val initial = PlaybackAuthState(
            cookie = normalizedCookie,
            visitorData = visitorData,
            dataSyncId = dataSyncId,
        ).normalized()

        // Diagnostik wire (Agen C) untuk kegagalan verifikasi: state inilah
        // yang dipakai `myTube.accountInfo()` (auth state yang diterapkan
        // di bawah). `accountMenu` selalu `setLogin=true` dengan
        // `MyTubeClient.WEB_REMIX` (`supportsCookieAuthentication=true`),
        // jadi header Cookie disetel `ytClient` bila cookie state ini
        // tidak kosong. Catatan jujur: ini menyatakan header disetel dari
        // state, BUKAN observasi wire — apakah header benar-benar sampai
        // ke server adalah temuan eksperimen wire Agen A (PLAN §B.2).
        val diagnostics = verificationDiagnostics(
            initial.cookie,
            cookieAttached = !initial.cookie.isNullOrBlank(),
        )

        myTube.applyAuthState(initial.copy(dataSyncId = null))
        // Fallback verifikasi (PLAN music-login-raw-error): bila
        // `verifyAccount()` gagal dengan `MusicLoginError.UnrecognizedResponse`,
        // JANGAN langsung gagal — coba `myTube.selectedAccount()` (fallback
        // `accounts_list`). Sukses → pakai `info` itu sebagai `accountInfo`
        // dan `dataSyncId` dari `SelectedAccount` bila ada (langkah 3 resolve
        // dataSyncId dilewati bila dataSyncId sudah didapat dari fallback;
        // bila fallback tidak membawa dataSyncId, langkah 3 jalan seperti
        // biasa dengan kandidat WebView). Gagal → kembalikan
        // `UnrecognizedResponse` dengan `rawDetail` dari kegagalan
        // `account_menu` (bukan dari fallback). Urutan ini sengaja:
        // account_menu dulu, accounts_list cadangan.
        var fallbackDataSyncId: String? = null
        val accountInfo = try {
            verifyAccount().getOrElse { cause ->
                val error = classifyVerificationFailure(cause)
                if (error is MusicLoginError.UnrecognizedResponse) {
                    val fallback = myTube.selectedAccount()
                    if (fallback.isSuccess) {
                        val selected = fallback.getOrThrow()
                        fallbackDataSyncId = selected.dataSyncId
                        return@getOrElse selected.info
                    }
                }
                val rawDetail = rawDetailForVerification(error, cause, names, diagnostics)
                logFailure("verifikasi", error, cause, rawDetail = rawDetail, cookies = names)
                return Result.failure(MusicLoginException(error, rawDetail))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            val error = MusicLoginError.Network
            logFailure("verifikasi", error, e, cookies = names)
            return Result.failure(MusicLoginException(error))
        } catch (e: Exception) {
            val error = classifyVerificationFailure(e)
            val rawDetail = rawDetailForVerification(error, e, names, diagnostics)
            logFailure("verifikasi", error, e, rawDetail = rawDetail, cookies = names)
            return Result.failure(MusicLoginException(error, rawDetail))
        }

        var dataSyncIdFailure: Throwable? = null
        val resolvedDataSyncId = fallbackDataSyncId?.normalizeDataSyncId()
            ?: try {
                val result = myTube.accountDataSyncId()
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
                logFailure("dataSyncId", error, dataSyncIdFailure, dataSyncIdFailure == null, cookies = names)
                return Result.failure(MusicLoginException(MusicLoginError.MissingDataSyncId))
            }

        val final = initial.copy(dataSyncId = resolvedDataSyncId).normalized()
        myTube.applyAuthState(final)
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
        val first = myTube.accountInfo()
        if (first.isSuccess) return first
        val error = classifyVerificationFailure(first.exceptionOrNull())
        if (error is MusicLoginError.HttpRejected || error is MusicLoginError.UnrecognizedResponse) {
            delay(VERIFY_RETRY_DELAY_MS)
            return myTube.accountInfo()
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
 * Teks mentah (sudah scrub) untuk kegagalan verifikasi, atau null bila
 * tidak ada yang aman ditampilkan:
 * - [MusicLoginError.HttpRejected] → `"HTTP $status"`, DITAMBAH tiga baris
 *   diagnostik wire [diagnostics] (PLAN §C, Agen C).
 * - [MusicLoginError.UnrecognizedResponse] → cuplikan dari
 *   `UnrecognizedAccountResponseException.rawSnippet` (Agen A), di-scrub
 *   sekali lagi secara defensif di sini, DITAMBAH baris akhir
 *   `"cookie: <nama, dipisah koma>"` (PLAN §C.3 butir 2) lalu tiga baris
 *   diagnostik wire [diagnostics]. Bila tidak ada cuplikan, rawDetail
 *   menjadi baris cookie saja + diagnostik. [names] HANYA berisi
 *   nama cookie (dari `cookieNames`), tanpa nilai — baris ini ditambah
 *   SETELAH scrub, jadi tidak pernah melewati scrub kedua yang beda gaya.
 *   Begitu pula [diagnostics]: hanya nama cookie + origin, tanpa nilai.
 * - Lainnya → null (jangan karang).
 */
private fun rawDetailForVerification(
    error: MusicLoginError,
    cause: Throwable?,
    names: List<String> = emptyList(),
    diagnostics: String? = null,
): String? {
    val withDiagnostics = { base: String ->
        if (diagnostics.isNullOrBlank()) base else "$base\n$diagnostics"
    }
    return when (error) {
        is MusicLoginError.HttpRejected -> withDiagnostics("HTTP ${error.status}")
        is MusicLoginError.UnrecognizedResponse -> {
            val snippet = rawSnippetFrom(cause)?.let(::scrubRawDetail)?.takeIf { it.isNotEmpty() }
            val cookieLine = names.takeIf { it.isNotEmpty() }
                ?.let { "cookie: ${it.joinToString(", ")}" }
            when {
                snippet != null && cookieLine != null -> withDiagnostics("$snippet\n$cookieLine")
                snippet != null -> withDiagnostics(snippet)
                cookieLine != null -> withDiagnostics(cookieLine)
                else -> diagnostics?.takeIf { it.isNotBlank() }
            }
        }
        else -> null
    }
}

/**
 * Urutan NAMA cookie yang dipakai `myTubeLoginCookieValue` untuk
 * SAPISIDHASH (AuthUtils.kt `MYTUBE_LOGIN_COOKIE_NAMES`): cerminan
 * urutan itu, BUKAN salinan nilai. Bila urutan di AuthUtils berubah,
 * daftar ini WAJIB ikut berubah — tidak ada API publik yang mengembalikan
 * namanya, hanya nilainya.
 */
private val LOGIN_COOKIE_HASH_NAMES = listOf(
    "SAPISID",
    "__Secure-3PAPISID",
    "__Secure-1PAPISID",
)

/**
 * Nama cookie yang dipakai `myTubeLoginCookieValue` untuk menghitung
 * SAPISIDHASH, atau null bila tidak ada yang cocok (mis. sesi penuh
 * hanya bermodal `APISID`). NAMA SAJA, tanpa nilai.
 * `internal` supaya tes modul ini bisa mengujinya langsung.
 */
internal fun hashCookieName(cookie: String?): String? {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return LOGIN_COOKIE_HASH_NAMES.firstOrNull { !cookieMap[it].isNullOrBlank() }
}

/**
 * Tiga baris diagnostik wire (PLAN §C, Agen C) yang ditambahkan di akhir
 * rawDetail — string yang sama tampil di layar error DAN masuk log.
 * HANYA berisi nama dan origin, TIDAK PERNAH nilai cookie, hash, atau
 * token, jadi tidak perlu scrub:
 * - `cookie terlampir: ya/tidak` — apakah request verifikasi melampirkan
 *   header Cookie, ditentukan dari state yang dipakai verifikasi
 *   ([cookieAttached]: cookie state tidak kosong + `setLogin=true` di
 *   `accountMenu` + `WEB_REMIX` mendukung cookie auth). Ini menyatakan
 *   header disetel dari state, BUKAN observasi wire (lihat PLAN §B.2).
 * - `hash dari: <NAMA_COOKIE>` — nama cookie sumber SAPISIDHASH, atau
 *   `- (tidak ada)` bila tidak ada yang cocok.
 * - `hash origin: <origin>` — origin penghitung SAPISIDHASH dari
 *   `MyTubeClient.WEB_REMIX.requestOrigin()`.
 * `internal` supaya tes modul ini bisa mengujinya langsung.
 */
internal fun verificationDiagnostics(cookie: String?, cookieAttached: Boolean): String {
    val hashFrom = hashCookieName(cookie) ?: "- (tidak ada)"
    return "cookie terlampir: ${if (cookieAttached) "ya" else "tidak"}\n" +
        "hash dari: $hashFrom\n" +
        "hash origin: ${MyTubeClient.WEB_REMIX.requestOrigin()}"
}

/**
 * Cuplikan mentah dari penyebab `UnrecognizedResponse`, atau null bila
 * penyebabnya bukan [UnrecognizedAccountResponseException] (mis. parse
 * null dari `IllegalStateException` biasa). Snippet itu sudah di-scrub
 * Agen A di innertube; [rawDetailForVerification] tetap menjalankan
 * scrub defensif kedua di atasnya.
 */
private fun rawSnippetFrom(cause: Throwable?): String? =
    (cause as? UnrecognizedAccountResponseException)?.rawSnippet

/** Batas panjang cuplikan mentah (kontrak PLAN: maks ~500 karakter). */
internal const val MAX_RAW_DETAIL_CHARS = 500

private val SENSITIVE_COOKIE_VALUE_REGEX = Regex(
    """(?i)(SAPISID\w*|__Secure-\w*SID|SID|HSID|SSID|APISID)\s*=\s*[^;\s,\n"']*""",
)
private val COOKIE_HEADER_REGEX = Regex("""(?i)cookie\s*=\s*[^;\n]*""")
private val AUTHORIZATION_HEADER_REGEX = Regex("""(?i)authorization\s*:\s*[^"\r\n,;]+""")
private val SAPISIDHASH_REGEX = Regex("""(?i)SAPISIDHASH\s+[^\s\n"',;]+""")

/**
 * Scrub defensif KEDUA untuk teks mentah (kontrak PLAN: satu-satunya
 * tempat scrub adalah `scrubRawDetail` Agen A di innertube/utils; fungsi
 * ini di sini HANYA karena Agen A belum selesai, dan dipakai semata di
 * titik pembuatan `rawDetail` — bukan untuk cookie atau badan mentah
 * tanpa scrub). Hapus nilai cookie (`k=v` untuk kunci SAPISID, SID,
 * HSID, SSID, dan kunci berakhiran SID, `cookie=...`,
 * `Authorization: ...`, `SAPISIDHASH ...`), lalu potong ke
 * [MAX_RAW_DETAIL_CHARS].
 * `internal` supaya tes modul ini bisa mengujinya langsung.
 */
internal fun scrubRawDetail(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val scrubbed = raw
        .replace(SAPISIDHASH_REGEX, "SAPISIDHASH <redacted>")
        .replace(AUTHORIZATION_HEADER_REGEX, "Authorization: <redacted>")
        .replace(COOKIE_HEADER_REGEX, "cookie=<redacted>")
        .replace(SENSITIVE_COOKIE_VALUE_REGEX, "$1=<redacted>")
    return scrubbed.take(MAX_RAW_DETAIL_CHARS)
}

/**
 * Satu baris log diagnostik per kegagalan `completeLogin` (kontrak PLAN
 * D.3). Hanya jenis + status HTTP + flag parse-null + daftar NAMA cookie
 * + [rawDetail] bila ada; TIDAK PERNAH nilai cookie, SAPISID, token,
 * visitorData, dataSyncId, nama/email akun, atau badan respons. [cookies]
 * berisi nama saja (dari `cookieNames`); [rawDetail] yang di-log adalah
 * persis yang ditampilkan ke user (sudah scrub), bukan lebih dari itu.
 */
private fun logFailure(
    step: String,
    error: MusicLoginError,
    cause: Throwable?,
    parseNull: Boolean = isParseNull(cause),
    rawDetail: String? = null,
    cookies: List<String> = emptyList(),
) {
    val status = (error as? MusicLoginError.HttpRejected)?.status
    val statusPart = if (status != null) " status=$status" else ""
    val cookiesPart = if (cookies.isNotEmpty()) " cookies=[${cookies.joinToString(", ")}]" else ""
    val rawPart = if (rawDetail != null) " rawDetail=$rawDetail" else ""
    Log.w(
        TAG,
        "completeLogin: $step gagal kind=${error::class.simpleName}$statusPart parseNull=$parseNull$cookiesPart$rawPart",
    )
}
