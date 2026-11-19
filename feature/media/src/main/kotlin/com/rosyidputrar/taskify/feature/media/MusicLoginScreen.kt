/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `app/.../ui/screens/LoginScreen.kt`
// (v15.1.0, c7f0e24) — WebView login Google + `MyTubeLoginWebViewClient` +
// `LOGIN_CONTEXT_SCRIPT` (disalin verbatim) + penggabung cookie.
// Yang diubah: tanpa Hilt/NavController (callback `onBack`/`onLoginComplete`),
// tanpa Toast sukses, tanpa `resetAuthWebViewSession` terpisah (logika
// pembersihan sesi ditulis di sini), state Loading/Error dirender sebagai
// composable statis [MusicLoginStateContent] supaya bisa di-screenshot tanpa
// WebView. Lihat licenses/ARCHIVETUNE.md.
// `AndroidView` (androidx.compose.ui.viewinterop) TIDAK butuh dependensi
// tambahan: di Compose 1.12.1 ia ada di `ui-android`, transitif dari
// `compose-ui` yang sudah dipakai modul ini (artefak `ui-viewinterop`
// terpisah sudah tidak diterbitkan; jangan tambahkan lagi).
// Keamanan: JS interface "Android" HANYA dua fungsi di bawah; JANGAN tambah
// fungsi lain; JANGAN log cookie di mana pun.

package com.rosyidputrar.taskify.feature.media

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val LOGIN_CONTEXT_RETRY_DELAY_MS = 1_000L
/**
 * Batas atas percobaan ekstraksi login per pemanggilan
 * [MyTubeLoginWebViewClient.scheduleLoginContextExtraction] (PLAN §C.4
 * butir 2): 60 percobaan @1 detik = jendela tunggu maksimal 60 detik
 * sampai cookie gabungan menjadi SESI PENUH. Counter di-reset tiap
 * `onPageFinished` / `doUpdateVisitedHistory` baru (pemanggilan baru
 * membatalkan runnable lama dan memulai hitungan dari 60 lagi); loop
 * berhenti lebih awal bila sesi penuh tertangkap, bila URL keluar dari
 * YouTube, atau bila `release()` dipanggil. 60 detik dipilih karena
 * cookie sesi (`SID`/`HSID`/`SSID`) kadang baru muncul beberapa detik
 * sesudah `LOGIN_INFO` + PAPISID, dan gate sesi penuh (§C.2) menolak
 * tangkapan parsial — tanpa jendela sepanjang ini, login yang sah bisa
 * tidak pernah tertangkap.
 */
private const val LOGIN_CONTEXT_EXTRACTION_ATTEMPTS = 60

/** SALIN verbatim dari ArchiveTune `LoginScreen.kt` — jangan tulis dari ingatan. */
private const val LOGIN_CONTEXT_SCRIPT =
    "(function(){try{var c=window.ytcfg;var y=window.yt&&window.yt.config_;var s=document.querySelectorAll('script');var v=c&&c.get&&c.get('VISITOR_DATA')||y&&y.VISITOR_DATA;var d=c&&c.get&&c.get('DATASYNC_ID')||y&&y.DATASYNC_ID;for(var i=0;i<s.length&&(!v||!d);i++){var x=s[i].textContent;if(!v){var vm=x.match(/[\"']VISITOR_DATA[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(vm)v=vm[1]}if(!d){var dm=x.match(/[\"'](?:DATASYNC_ID|dataSyncId)[\"']\\s*:\\s*[\"']([^\"']+)[\"']/);if(dm)d=dm[1]}}if(v)Android.onRetrieveVisitorData(v);if(d)Android.onRetrieveDataSyncId(d)}catch(e){}})();"

/**
 * Layar login musik: WebView login Google di dalam aplikasi.
 * Sukses → [onLoginComplete] (pemanggil menutup layar + memuat ulang).
 * Tombol kembali / gesture kembali: mundur di WebView bila bisa, bila tidak
 * → [onBack].
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLoginScreen(
    onBack: () -> Unit,
    onLoginComplete: () -> Unit = {},
    viewModel: MusicLoginViewModel = koinViewModel(),
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loginClient by remember { mutableStateOf<MyTubeLoginWebViewClient?>(null) }

    LaunchedEffect(screenState) {
        if (screenState is MusicLoginScreenState.Success) onLoginComplete()
    }
    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Masuk dengan Google") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") } },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        val client = MyTubeLoginWebViewClient(
                            cookieManager = CookieManager.getInstance(),
                            onCookiesCaptured = viewModel::onCookiesCaptured,
                        )
                        webViewClient = client
                        loginClient = client
                        settings.apply {
                            javaScriptEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        val loginWebView = this
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onRetrieveVisitorData(newVisitorData: String?) {
                                    loginWebView.post { viewModel.onVisitorDataExtracted(newVisitorData) }
                                }

                                @JavascriptInterface
                                fun onRetrieveDataSyncId(newDataSyncId: String?) {
                                    loginWebView.post { viewModel.onDataSyncIdExtracted(newDataSyncId) }
                                }
                            },
                            "Android",
                        )
                        webView = this
                        resetAuthWebViewSession(context, this, clearCookies = true) {
                            loadUrl(DEFAULT_LOGIN_URL)
                        }
                    }
                },
                onRelease = { releasedWebView ->
                    val releasedClient = releasedWebView.webViewClient as? MyTubeLoginWebViewClient
                    releasedClient?.release(releasedWebView)
                    releasedWebView.removeJavascriptInterface("Android")
                    releasedWebView.stopLoading()
                    releasedWebView.destroy()
                    if (webView === releasedWebView) webView = null
                    if (loginClient === releasedClient) loginClient = null
                },
            )
            MusicLoginStateContent(
                screenState,
                // PLAN §D.5 — "Coba lagi":
                // * WebView SUDAH di music.youtube.com (login Google berhasil,
                //   tapi verifikasi pertama gagal): jangan navigasi ulang,
                //   langsung coba tangkap ulang cookie yang ada. Tanpa reset
                //   gate, cookie yang sama akan didiamkan dedup (jebakan §B.2).
                // * WebView BELUM di YTM (masih di halaman Google / kosong):
                //   perilaku lama — reset gate lalu muat ulang halaman login.
                // Factory AndroidView membuat client baru tiap komposisi baru,
                // jadi gate juga segar saat layar login dibuka ulang.
                onRetry = {
                    viewModel.reset()
                    val view = webView
                    val client = loginClient
                    if (view != null && client != null && view.url.isYouTubeUrl()) {
                        client.retryCaptureNow(view)
                    } else {
                        client?.resetCapturedCookie()
                        view?.loadUrl(DEFAULT_LOGIN_URL)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Tampilan state login (statis, tanpa WebView): Loading = progress di atas,
 * Error = teks + tombol "Coba lagi". Dipisah supaya bisa di-screenshot di
 * Robolectric — WebView tidak bisa dibuat di tes unit.
 */
@Composable
fun MusicLoginStateContent(
    state: MusicLoginScreenState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is MusicLoginScreenState.Loading -> Column(modifier) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Memuat halaman login…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                textAlign = TextAlign.Center,
            )
        }
        is MusicLoginScreenState.Error -> Box(modifier.background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                // Perintah sir: teks mentah error tampil LANGSUNG di layar,
                // tanpa ketukan tambahan. Komponen bersama (Agen A):
                // blok monospace bisa di-scroll (maks 200.dp) supaya tombol
                // "Coba lagi" tetap terlihat tanpa scroll layar, dibungkus
                // SelectionContainer + tombol "Salin detail" hanya di debug.
                // rawDetail sudah di-scrub di repository — UI menampilkan
                // apa adanya, tanpa memproses ulang isinya.
                val rawDetail = state.rawDetail
                if (!rawDetail.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    RawErrorDetail(rawDetail, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
            }
        }
        else -> Box(modifier)
    }
}

private class MyTubeLoginWebViewClient(
    private val cookieManager: CookieManager,
    private val onCookiesCaptured: (String) -> Unit,
) : WebViewClient() {
    private var extractionRunnable: Runnable? = null
    private val captureGate = LoginCookieCaptureGate()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val targetUrl = request.url.toString()
        if (!request.isForMainFrame && !targetUrl.isWebViewLoadableUrl()) return true
        return handleUrlLoading(view, targetUrl)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean = handleUrlLoading(view, url)

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        scheduleLoginContextExtraction(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        scheduleLoginContextExtraction(view, url)
    }

    fun release(view: WebView) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null
    }

    /**
     * PLAN §D.1 — lupakan tangkapan cookie terakhir supaya "Coba lagi"
     * bisa menangkap ulang cookie yang sama. WAJIB dipanggil dari handler
     * retry (bukan hanya dari `release()`): tanpa ini, cookie gabungan
     * yang sama persis dengan tangkapan pertama didiamkan dedup dan
     * `onCookiesCaptured` tidak pernah dipanggil lagi (§B.2).
     */
    fun resetCapturedCookie() {
        captureGate.reset()
    }

    /**
     * PLAN §D.5 — retry aktif: WebView sudah di music.youtube.com dalam
     * keadaan login, jadi langsung coba tangkap ulang cookie yang ada
     * (reset gate dulu + baca cookie dari URL saat ini), TANPA menunggu
     * `onPageFinished` berikutnya dan TANPA navigasi ulang. Tidak ada
     * `startLogin` ganda: guard di `MusicLoginViewModel` tetap berlaku
     * (cookie yang sama + job aktif / sudah sukses tidak diproses ulang).
     */
    fun retryCaptureNow(view: WebView) {
        captureGate.reset()
        captureCookies(view.url)
    }

    /**
     * Ekstraksi konteks login berulang (PLAN §C.4 butir 2): cookie
     * gabungan yang BELUM sesi penuh (gate di [LoginCookieCaptureGate]
     * menolaknya) TIDAK menghentikan loop — runnable dijadwalkan ulang
     * @1 detik selama WebView masih di URL YouTube, sampai batas atas
     * [LOGIN_CONTEXT_EXTRACTION_ATTEMPTS] percobaan per pemanggilan ini.
     * Begitu sesi penuh tertangkap (`captureCookies` true), loop
     * berhenti: tidak ada gunanya mengevaluasi script + membaca cookie
     * tiap detik sesudah verifikasi sudah jalan. Tangkapan parsial
     * TIDAK PERNAH diteruskan ke `onCookiesCaptured` (gate sesi penuh),
     * jadi "berhenti di tangkapan parsial" tidak bisa terjadi.
     * Counter di-reset tiap pemanggilan baru: `onPageFinished` /
     * `doUpdateVisitedHistory` membatalkan runnable lama sebelum membuat
     * yang baru.
     */
    private fun scheduleLoginContextExtraction(view: WebView, url: String?) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null

        if (!url.isYouTubeUrl()) return

        var remainingAttempts = LOGIN_CONTEXT_EXTRACTION_ATTEMPTS
        val runnable = object : Runnable {
            override fun run() {
                if (!view.url.isYouTubeUrl()) return
                view.evaluateJavascript(LOGIN_CONTEXT_SCRIPT, null)
                if (captureCookies(view.url)) return
                remainingAttempts -= 1
                if (remainingAttempts > 0) {
                    view.postDelayed(this, LOGIN_CONTEXT_RETRY_DELAY_MS)
                }
            }
        }
        extractionRunnable = runnable
        view.post(runnable)
    }

    /**
     * Baca + gabung cookie lalu teruskan ke `onCookiesCaptured` bila
     * gate sesi penuh menerimanya. true = sesi penuh tertangkap
     * (pemanggil boleh menghentikan loop ekstraksi); false = belum sesi
     * penuh, duplikat tangkapan terakhir, atau tidak ada cookie.
     */
    private fun captureCookies(currentUrl: String?): Boolean {
        val mergedCookie = mergeMyTubeCookies(cookieManager, currentUrl) ?: return false
        if (!captureGate.shouldCapture(mergedCookie)) return false
        onCookiesCaptured(mergedCookie)
        return true
    }

    private fun handleUrlLoading(view: WebView, url: String?): Boolean {
        val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
        if (targetUrl.isYouTubeMusicStoreListingUrl()) return true
        if (targetUrl.isWebViewLoadableUrl()) return false
        targetUrl.intentWebViewUrl()?.let(view::loadUrl)
        return true
    }

    private fun mergeMyTubeCookies(cookieManager: CookieManager, currentUrl: String?): String? {
        val candidateUrls = linkedSetOf<String>()
        currentUrl.myTubeCookieOrigin()?.let(candidateUrls::add)
        candidateUrls.addAll(MYTUBE_COOKIE_URLS)
        val cookiesByUrl = candidateUrls.associateWith { cookieManager.getCookie(it).orEmpty() }
        return mergeMyTubeCookies(cookiesByUrl, currentUrl)
    }
}

private fun String.intentWebViewUrl(): String? {
    val parsedIntent = runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }.getOrNull()
    return sequenceOf(
        parsedIntent?.dataString,
        intentUriAsHttpsUrl(),
        parsedIntent
            ?.getStringExtra("browser_fallback_url")
            ?.takeUnless(String::isYouTubeMusicStoreListingUrl),
    ).firstOrNull { it?.isHttpUrl() == true }
}

private fun String.intentUriAsHttpsUrl(): String? {
    val intentUri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    if (!intentUri.scheme.equals("intent", ignoreCase = true)) return null
    val authority = intentUri.encodedAuthority ?: return null
    val path = intentUri.encodedPath.orEmpty()
    val query = intentUri.encodedQuery?.let { "?$it" }.orEmpty()
    return "https://$authority$path$query"
}

/**
 * Bersihkan sesi WebView auth sebelum login (port
 * `resetAuthWebViewSession` ArchiveTune): cookie akun lain yang nyangkut
 * tidak boleh terbawa; `onReady` dipanggil sesudah cookie terhapus.
 */
private fun resetAuthWebViewSession(
    context: Context,
    webView: WebView,
    clearCookies: Boolean = true,
    onReady: () -> Unit,
) {
    webView.stopLoading()
    webView.clearHistory()
    webView.clearFormData()
    webView.clearCache(true)
    WebStorage.getInstance().deleteAllData()
    WebViewDatabase.getInstance(context.applicationContext).apply {
        clearFormData()
        clearHttpAuthUsernamePassword()
        clearUsernamePassword()
    }
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    if (!clearCookies) {
        onReady()
        return
    }
    cookieManager.removeSessionCookies {
        cookieManager.removeAllCookies {
            cookieManager.flush()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            onReady()
        }
    }
}
