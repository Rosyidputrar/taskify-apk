package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `signatureTimestamp` dari `base.js` (C1-8): ekstraksi murni + provider
 * dengan OkHttp palsu (interceptor membalas `iframe_api` dan `base.js`
 * sintetis, tanpa jaringan).
 */
class SignatureTimestampProviderTest {

    private fun fakeClient(iframeApiBody: String, baseJsBody: String, calls: MutableList<String>): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = chain.request().url.toString()
                calls += url
                val body = if ("iframe_api" in url) iframeApiBody else baseJsBody
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()

    @Test fun `extractPlayerId membaca id dari iframe_api`() {
        assertEquals(
            "abcdef1234567890",
            SignatureTimestampProvider.extractPlayerId(
                """{"player":"x"}// player/abcdef1234567890/player_ias.vflset/en_US/base.js""",
            ),
        )
        assertNull(SignatureTimestampProvider.extractPlayerId("tidak ada pola"))
    }

    @Test fun `extractSignatureTimestamp butuh tepat satu nilai unik`() {
        assertEquals(19283, SignatureTimestampProvider.extractSignatureTimestamp("x signatureTimestamp: 19283 y"))
        assertEquals(
            19283,
            SignatureTimestampProvider.extractSignatureTimestamp("signatureTimestamp: 19283 signatureTimestamp: 19283"),
            "nilai sama yang berulang tetap satu nilai unik",
        )
        assertNull(SignatureTimestampProvider.extractSignatureTimestamp("signatureTimestamp: 11111 signatureTimestamp: 22222"))
        assertNull(SignatureTimestampProvider.extractSignatureTimestamp("tidak ada timestamp"))
        assertNull(SignatureTimestampProvider.extractSignatureTimestamp("signatureTimestamp: 123"))
    }

    @Test fun `provider mengambil timestamp dan memakai cache`() = runBlocking {
        val calls = mutableListOf<String>()
        val provider = SignatureTimestampProvider(
            fakeClient(
                "player/abcdef1234567890/",
                "var x = {signatureTimestamp: 19283};",
                calls,
            ),
        )
        assertEquals(19283, provider.signatureTimestamp(MyTubeClient.ANDROID, "1RrF6Ee_io0"))
        assertEquals(2, calls.size, "iframe_api + base.js")
        assertEquals(19283, provider.signatureTimestamp(MyTubeClient.ANDROID, "1RrF6Ee_io0"))
        assertEquals(2, calls.size, "panggilan kedua dari cache, tanpa jaringan")
    }

    @Test fun `provider null untuk klien tanpa useSignatureTimestamp`() = runBlocking {
        val calls = mutableListOf<String>()
        val provider = SignatureTimestampProvider(fakeClient("", "", calls))
        assertNull(provider.signatureTimestamp(MyTubeClient.MWEB, "1RrF6Ee_io0"))
        assertEquals(0, calls.size)
    }

    @Test fun `provider null bila iframe_api gagal`() = runBlocking {
        val failing = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Server Error")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val provider = SignatureTimestampProvider(failing)
        assertNull(provider.signatureTimestamp(MyTubeClient.ANDROID, "1RrF6Ee_io0"))
    }
}
