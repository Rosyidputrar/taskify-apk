/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `app/.../ui/screens/LoginScreen.kt`
// (v15.1.0, c7f0e24) — WebView login Google + `YouTubeLoginWebViewClient` +
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
import com.rosyidputrar.taskify.core.innertube.utils.hasCompleteYouTubeLoginCookies
import org.koin.compose.viewmodel.koinViewModel

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val LOGIN_CONTEXT_RETRY_DELAY_MS = 1_000L
private const val LOGIN_CONTEXT_EXTRACTION_ATTEMPTS = 10

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
                        webViewClient = YouTubeLoginWebViewClient(
                            cookieManager = CookieManager.getInstance(),
                            onCookiesCaptured = viewModel::onCookiesCaptured,
                        )
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
                    (releasedWebView.webViewClient as? YouTubeLoginWebViewClient)?.release(releasedWebView)
                    releasedWebView.removeJavascriptInterface("Android")
                    releasedWebView.stopLoading()
                    releasedWebView.destroy()
                    if (webView === releasedWebView) webView = null
                },
            )
            MusicLoginStateContent(
                screenState,
                onRetry = { viewModel.reset(); webView?.loadUrl(DEFAULT_LOGIN_URL) },
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
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
            }
        }
        else -> Box(modifier)
    }
}

private class YouTubeLoginWebViewClient(
    private val cookieManager: CookieManager,
    private val onCookiesCaptured: (String) -> Unit,
) : WebViewClient() {
    private var extractionRunnable: Runnable? = null
    private var lastCapturedCookie: String? = null

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

    private fun scheduleLoginContextExtraction(view: WebView, url: String?) {
        extractionRunnable?.let(view::removeCallbacks)
        extractionRunnable = null

        if (!url.isYouTubeUrl()) return

        var remainingAttempts = LOGIN_CONTEXT_EXTRACTION_ATTEMPTS
        val runnable = object : Runnable {
            override fun run() {
                if (!view.url.isYouTubeUrl()) return
                view.evaluateJavascript(LOGIN_CONTEXT_SCRIPT, null)
                captureCookies(view.url)
                remainingAttempts -= 1
                if (remainingAttempts > 0) {
                    view.postDelayed(this, LOGIN_CONTEXT_RETRY_DELAY_MS)
                }
            }
        }
        extractionRunnable = runnable
        view.post(runnable)
    }

    private fun captureCookies(currentUrl: String?) {
        val mergedCookie = mergeYouTubeCookies(cookieManager, currentUrl) ?: return
        if (!hasCompleteYouTubeLoginCookies(mergedCookie) || mergedCookie == lastCapturedCookie) return
        lastCapturedCookie = mergedCookie
        onCookiesCaptured(mergedCookie)
    }

    private fun handleUrlLoading(view: WebView, url: String?): Boolean {
        val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
        if (targetUrl.isYouTubeMusicStoreListingUrl()) return true
        if (targetUrl.isWebViewLoadableUrl()) return false
        targetUrl.intentWebViewUrl()?.let(view::loadUrl)
        return true
    }

    private fun mergeYouTubeCookies(cookieManager: CookieManager, currentUrl: String?): String? {
        val candidateUrls = linkedSetOf<String>()
        currentUrl.youTubeCookieOrigin()?.let(candidateUrls::add)
        candidateUrls.addAll(YOUTUBE_COOKIE_URLS)
        val cookiesByUrl = candidateUrls.associateWith { cookieManager.getCookie(it).orEmpty() }
        return mergeYouTubeCookies(cookiesByUrl, currentUrl)
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
