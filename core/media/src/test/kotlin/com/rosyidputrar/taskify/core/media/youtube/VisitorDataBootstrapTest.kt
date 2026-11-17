package com.rosyidputrar.taskify.core.media.youtube

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.YouTube
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `VisitorDataBootstrap` (Tahap 5) via MockEngine: sukses menyimpan,
 * gagal-aman (jaringan mati / pola tidak ketemu / timeout) tanpa throw.
 * Tidak ada panggilan jaringan sungguhan di sini.
 */
@RunWith(AndroidJUnit4::class)
class VisitorDataBootstrapTest {
    private lateinit var context: Context
    private lateinit var store: YouTubeAuthStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(YouTubeAuthStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        store = YouTubeAuthStore(context)
    }

    private fun MockRequestHandleScope.okText(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    /** Badan `sw.js_data` palsu: `)]}'` + `[[ "s1", "s2", [ ...string... ] ]]`. */
    private fun swJsData(vararg inner: String): String {
        val items = inner.joinToString(",") { "\"$it\"" }
        return ")]}'\n[[\"s1\",\"s2\",[$items]]]"
    }

    private fun bootstrapFor(timeoutMs: Long = VisitorDataBootstrap.BOOTSTRAP_TIMEOUT_MS, handler: MockRequestHandler): VisitorDataBootstrap {
        val engine = MockEngine(handler)
        val tube = InnerTube(HttpClient(engine) { expectSuccess = true })
        return VisitorDataBootstrap(store, YouTube(tube), timeoutMs)
    }

    @Test fun `sukses menyimpan visitorData dan tidak mengulang jaringan`() = runBlocking {
        var calls = 0
        val bootstrap = bootstrapFor {
            calls++
            okText(swJsData("noise", "CgttZXN0dmVzdA"))
        }
        assertEquals("CgttZXN0dmVzdA", bootstrap.ensureVisitorData())
        assertEquals("CgttZXN0dmVzdA", store.visitorData)
        // Panggilan kedua memakai simpanan, tanpa jaringan.
        assertEquals("CgttZXN0dmVzdA", bootstrap.ensureVisitorData())
        assertEquals(1, calls)
    }

    @Test fun `sudah tersimpan tidak menyentuh jaringan`() = runBlocking {
        store.saveVisitorData("CgttZXN0")
        var calls = 0
        val bootstrap = bootstrapFor {
            calls++
            okText(swJsData("Cgtlain"))
        }
        assertEquals("CgttZXN0", bootstrap.ensureVisitorData())
        assertEquals(0, calls)
    }

    @Test fun `jaringan mati mengembalikan null tanpa melempar`() = runBlocking {
        var calls = 0
        val bootstrap = bootstrapFor {
            calls++
            throw IOException("putus")
        }
        assertNull(bootstrap.ensureVisitorData())
        assertEquals(1, calls)
        assertNull(store.visitorData)
    }

    @Test fun `pola tidak ketemu mengembalikan null`() = runBlocking {
        val bootstrap = bootstrapFor { okText(swJsData("noise", "other")) }
        assertNull(bootstrap.ensureVisitorData())
        assertNull(store.visitorData)
    }

    @Test fun `timeout singkat mengembalikan null`() = runBlocking {
        val bootstrap = bootstrapFor(timeoutMs = 100) {
            delay(500)
            okText(swJsData("CgttZXN0"))
        }
        assertNull(bootstrap.ensureVisitorData())
        assertNull(store.visitorData)
    }

    @Test fun `regresi merge - cookie dan dataSyncId tetap saat visitorData diambil`() = runBlocking {
        // Sesi login sudah ada, visitorData belum pernah di-bootstrap.
        store.saveAuthState(
            PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", dataSyncId = "delegated||user"),
        )
        var calls = 0
        val bootstrap = bootstrapFor {
            calls++
            okText(swJsData("noise", "CgttZXN0dmVzdA"))
        }
        assertEquals("CgttZXN0dmVzdA", bootstrap.ensureVisitorData())
        assertEquals(1, calls)
        val stored = store.loadAuthState()
        assertEquals("CgttZXN0dmVzdA", stored.visitorData)
        // Regresi Tahap 3: cookie + dataSyncId TIDAK BOLEH hilang.
        assertEquals("SAPISID=dummy; LOGIN_INFO=dummy", stored.cookie)
        assertEquals("delegated||user", stored.dataSyncId)
    }

    @Test fun `sudah tersimpan beserta cookie tidak menyentuh jaringan`() = runBlocking {
        store.saveAuthState(
            PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", visitorData = "CgttZXN0"),
        )
        var calls = 0
        val bootstrap = bootstrapFor {
            calls++
            okText(swJsData("Cgtlain"))
        }
        assertEquals("CgttZXN0", bootstrap.ensureVisitorData())
        assertEquals(0, calls)
        assertEquals("SAPISID=dummy; LOGIN_INFO=dummy", store.loadAuthState().cookie)
    }
}
