/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `core/.../innertube/InnerTube.kt`
// (v15.1.0, c7f0e24) — struktur transport, `ytClient()`, dan `withRetry`
// dipertahankan; yang DIUBAH untuk Taskify (lihat licenses/ARCHIVETUNE.md):
//   - TIDAK ADA NetworkGatekeeper dalam bentuk apa pun (interceptor itu
//     memblokir semua koneksi sampai verifikasi build; Taskify tidak punya
//     dan tidak butuh mekanisme itu).
//   - Hanya endpoint yang dipakai rewrite tahap 1: search, browse, next,
//     player, music/get_search_suggestions, sw.js_data. Endpoint akun
//     (like/subscribe/playlist tulis/dll) tidak dibawa.
//   - Metode mengembalikan JSON mentah (`String`), bukan `HttpResponse` /
//     `body<T>()`: parser Tahap 2 bekerja di atas `JsonElement` meniru
//     `parse.ts` Worker, jadi deserialisasi ber-skema di transport tidak
//     ada gunanya. Badan permintaan di-encode manual (tanpa plugin
//     ContentNegotiation) supaya tidak menambah dependensi.
//   - Token lanjutan dikirim di BADAN permintaan (bentuk Worker yang
//     terverifikasi), bukan sebagai query `continuation`/`ctoken`.
//   - `locale` bawaan `hl=id, gl=ID` (Worker), dipakai juga untuk `next` —
//     ArchiveTune memakai locale perangkat untuk browse/search dan US/en
//     untuk antrean; PLAN merekomendasikan Worker kecuali uji HP berkata lain.
//   - Header `ytClient()` ditambah `Accept-Language` dan `X-Goog-AuthUser: 0`
//     dari `YT_HEADERS` Worker (terverifikasi live 2026-11-16).
//   - Tanpa proxy/DNS kustom (ditunda; `MusicBackend` mencatatnya sebagai
//     follow-up, bukan prasyarat tahap 1).

package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import com.rosyidputrar.taskify.core.innertube.models.YouTubeLocale
import com.rosyidputrar.taskify.core.innertube.models.body.AccountMenuBody
import com.rosyidputrar.taskify.core.innertube.models.body.AccountsListBody
import com.rosyidputrar.taskify.core.innertube.models.body.BrowseBody
import com.rosyidputrar.taskify.core.innertube.models.body.GetSearchSuggestionsBody
import com.rosyidputrar.taskify.core.innertube.models.body.GetTranscriptBody
import com.rosyidputrar.taskify.core.innertube.models.body.NextBody
import com.rosyidputrar.taskify.core.innertube.models.body.PlayerBody
import com.rosyidputrar.taskify.core.innertube.models.body.SearchBody
import com.rosyidputrar.taskify.core.innertube.utils.sha1
import com.rosyidputrar.taskify.core.innertube.utils.youtubeLoginCookieValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.Base64

/** Konfigurasi JSON InnerTube: kunci tak dikenal diabaikan, null tidak dikirim. */
@OptIn(ExperimentalSerializationApi::class)
internal val InnertubeJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Akses ke endpoint InnerTube — HANYA membuat permintaan HTTP, tidak
 * mem-parsing respons. Parsing ada di `YouTube` + `pages` (Tahap 2).
 *
 * [httpClient] bisa diinjeksi (tes memakai `MockEngine`); bawaan memakai
 * OkHttp dengan timeout 15/10/15 detik.
 */
class InnerTube(
    private val httpClient: HttpClient = createDefaultClient(),
) {
    var locale = YouTubeLocale(gl = "ID", hl = "id")

    @Volatile
    private var authState: PlaybackAuthState = PlaybackAuthState.EMPTY

    var visitorData: String?
        get() = authState.visitorData
        set(value) {
            authState = authState.copy(visitorData = value).normalized()
        }
    var dataSyncId: String?
        get() = authState.dataSyncId
        set(value) {
            authState = authState.copy(dataSyncId = value).normalized()
        }
    var poToken: String?
        get() = authState.poToken
        set(value) {
            authState = authState.copy(poToken = value).normalized()
        }
    var cookie: String?
        get() = authState.cookie
        set(value) {
            authState = authState.copy(cookie = value).normalized()
        }

    // Login adalah syarat musik (PLAN G.3): browse/search selalu berkirim
    // cookie bila ada, seperti `player`/`next` yang memang `setLogin=true`.
    var useLoginForBrowse: Boolean = true

    fun currentAuthState(): PlaybackAuthState = authState

    fun applyAuthState(value: PlaybackAuthState) {
        authState = value.normalized()
    }

    private fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        authState: PlaybackAuthState = currentAuthState(),
        includeVisitorData: Boolean = true,
    ) {
        val requestOrigin = client.requestOrigin()
        val requestReferer = client.requestReferer()
        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        header("X-Goog-Api-Format-Version", "1")
        header("X-YouTube-Client-Name", client.clientId)
        header("X-YouTube-Client-Version", client.clientVersion)
        header("X-Origin", requestOrigin)
        header("Referer", requestReferer)
        // Dari YT_HEADERS Worker (terverifikasi live): tanpa Accept-Language
        // lengkap InnerTube kadang membalas halaman consent.
        header(HttpHeaders.AcceptLanguage, "id-ID,id;q=0.9,en;q=0.8")
        // X-Goog-AuthUser dikirim SEKALI di sini untuk semua request (anonim
        // maupun login). Jangan tambah lagi di blok login di bawah: duplikat
        // digabung Ktor/OkHttp menjadi "0,0" di wire (bukan AuthUser valid).
        header("X-Goog-AuthUser", "0")
        if (includeVisitorData) {
            authState.visitorData?.let { header("X-Goog-Visitor-Id", it) }
        }
        if (setLogin && client.supportsCookieAuthentication) {
            authState.cookie?.let { cookie ->
                header("cookie", cookie)
                val loginCookieValue = youtubeLoginCookieValue(cookie) ?: return@let
                val currentTime = System.currentTimeMillis() / 1000
                val sapisidHash = sha1("$currentTime $loginCookieValue $requestOrigin")
                header("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                authState.dataSyncId.delegatedSessionIdOrNull()?.let { header("X-Goog-PageId", it) }
            }
        }
        header(HttpHeaders.UserAgent, client.userAgent)
        parameter("prettyPrint", false)
    }

    private inline fun <reified T> HttpRequestBuilder.jsonBody(value: T) {
        setBody(TextContent(InnertubeJson.encodeToString(value), ContentType.Application.Json))
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException || !e.isTransientNetworkFailure()) throw e
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException || current is HttpRequestTimeoutException) return true
            if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    /**
     * Semua pemanggil menerima `authState` sebagai parameter (A.8.3):
     * jangan membaca state internal diam-diam. Bawaan
     * `currentAuthState()` menjaga kompatibilitas pemanggil lama (termasuk
     * fasad `YouTube` Tahap 2) — state internal itu sendiri diisi lewat
     * [applyAuthState] (mis. dari `YouTubeAuthStore`, Tahap 5).
     */
    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
        useAccountContext: Boolean = true,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post("search") {
            ytClient(
                client = client,
                setLogin = useAccountContext && useLoginForBrowse,
                includeVisitorData = useAccountContext,
                authState = authState,
            )
            jsonBody(
                SearchBody(
                    context = client.toContext(
                        locale,
                        if (useAccountContext) authState.visitorData else null,
                        if (useAccountContext && useLoginForBrowse) authState.dataSyncId else null,
                    ),
                    query = query,
                    params = params,
                    continuation = continuation,
                ),
            )
        }.bodyAsText()
    }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        formData: BrowseBody.FormData? = null,
        setLogin: Boolean = false,
        useAccountContext: Boolean = true,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post("browse") {
            val shouldUseLogin = useAccountContext && (setLogin || useLoginForBrowse)
            ytClient(
                client = client,
                setLogin = shouldUseLogin,
                includeVisitorData = useAccountContext,
                authState = authState,
            )
            jsonBody(
                BrowseBody(
                    context = client.toContext(
                        locale,
                        if (useAccountContext) authState.visitorData else null,
                        if (shouldUseLogin) authState.dataSyncId else null,
                    ),
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                    formData = formData,
                ),
            )
        }.bodyAsText()
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String? = null,
        playlistId: String? = null,
        playlistSetVideoId: String? = null,
        index: Int? = null,
        params: String? = null,
        continuation: String? = null,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post("next") {
            ytClient(client, setLogin = true, authState = authState)
            jsonBody(
                NextBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                    videoId = videoId,
                    playlistId = playlistId,
                    playlistSetVideoId = playlistSetVideoId,
                    index = index,
                    params = params,
                    continuation = continuation,
                ),
            )
        }.bodyAsText()
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String? = null,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post(client.requestApiUrl("player")) {
            ytClient(client = client, setLogin = setLogin, authState = authState)
            jsonBody(
                PlayerBody(
                    context = client.toContext(
                        locale = locale,
                        visitorData = authState.visitorData,
                        dataSyncId = if (setLogin && client.supportsCookieAuthentication && authState.hasPlaybackLoginContext) authState.dataSyncId else null,
                    ),
                    videoId = videoId,
                    playlistId = playlistId,
                    playbackContext = signatureTimestamp?.let {
                        PlayerBody.PlaybackContext(
                            PlayerBody.PlaybackContext.ContentPlaybackContext(it),
                        )
                    },
                    serviceIntegrityDimensions = poToken?.let {
                        PlayerBody.ServiceIntegrityDimensions(poToken = it)
                    },
                ),
            )
        }.bodyAsText()
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post("music/get_search_suggestions") {
            ytClient(client, authState = authState)
            jsonBody(
                GetSearchSuggestionsBody(
                    context = client.toContext(locale, authState.visitorData, null),
                    input = input,
                ),
            )
        }.bodyAsText()
    }

    /**
     * Menu akun (`account/account_menu`, PLAN C.2) — dipakai fasad
     * `YouTube.accountInfo()` untuk verifikasi login. Selalu `setLogin=true`:
     * tanpa cookie responsnya tidak berisi akun aktif. Mengembalikan JSON
     * mentah; parsing ada di `pages/AccountMenuParser`.
     */
    suspend fun accountMenu(
        client: YouTubeClient,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post("account/account_menu") {
            ytClient(client = client, setLogin = true, authState = authState)
            jsonBody(
                AccountMenuBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                ),
            )
        }.bodyAsText()
    }

    /**
     * Daftar akun (`account/accounts_list`, PLAN C.2) — dipakai fasad
     * `YouTube.accountDataSyncId()` untuk mengambil `dataSyncId` akun yang
     * dipilih. Selalu `setLogin=true`. Mengembalikan JSON mentah; parsing
     * ada di `pages/AccountMenuParser`.
     */
    suspend fun accountChannels(
        client: YouTubeClient,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post(client.requestApiUrl("account/accounts_list")) {
            ytClient(client = client, setLogin = true, authState = authState)
            jsonBody(
                AccountsListBody(
                    context = client.toContext(locale, authState.visitorData, authState.dataSyncId),
                ),
            )
        }.bodyAsText()
    }

    /**
     * Badan `sw.js` untuk bootstrap `visitorData` (Tahap 5).
     * `withRetry = false` = satu percobaan saja, tanpa pengulangan:
     * bootstrap saat start gagal = lanjut anonim, bukan mengulang.
     */
    suspend fun getSwJsData(withRetry: Boolean = true): String =
        if (withRetry) {
            withRetry { httpClient.get("https://music.youtube.com/sw.js_data").bodyAsText() }
        } else {
            httpClient.get("https://music.youtube.com/sw.js_data").bodyAsText()
        }

    /**
     * Transkrip sinkron (`get_transcript`, Tahap 4) — diadaptasi dari
     * ArchiveTune `InnerTube.getTranscript` (tanpa PoToken di tahap 1:
     * [poToken] null berarti tanpa param `pot`/`potc`; tanpa param `key`
     * — endpoint InnerTube lain di transport ini bekerja tanpa `key`,
     * dan perilaku `get_transcript` tanpa `key` BELUM terverifikasi live,
     * lihat laporan Tahap 4). Mengembalikan JSON mentah; parsing ada di
     * `pages/parseTranscript`.
     */
    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
        poToken: String? = null,
        authState: PlaybackAuthState = currentAuthState(),
    ): String = withRetry {
        httpClient.post("get_transcript") {
            poToken?.let {
                parameter("pot", it)
                parameter("potc", "1")
                parameter("c", client.clientId)
            }
            ytClient(client)
            jsonBody(
                GetTranscriptBody(
                    context = client.toContext(locale, authState.visitorData, null),
                    params = Base64.getEncoder().encodeToString(
                        "\n${11.toChar()}$videoId".toByteArray(Charsets.UTF_8),
                    ),
                ),
            )
        }.bodyAsText()
    }

    companion object {
        fun createDefaultClient(): HttpClient =
            HttpClient(OkHttp) {
                expectSuccess = true

                install(ContentEncoding) {
                    gzip(0.9F)
                    deflate(0.8F)
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 15_000
                }

                defaultRequest {
                    url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
                }
            }
    }
}

private fun String?.delegatedSessionIdOrNull(): String? {
    val value = this?.trim()?.takeIf(String::isNotBlank) ?: return null
    val separatorIndex = value.indexOf("||")
    if (separatorIndex <= 0 || separatorIndex + 2 >= value.length) return null
    return value.substring(0, separatorIndex).trim().takeIf(String::isNotBlank)
}
