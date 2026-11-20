package com.rosyidputrar.taskify.core.youtubei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.Dns
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * C S-2: `parseResponse` membaca nama klien yang benar-benar dipakai JS
 * (`client`) dari payload resolve; bundle lama tanpa field itu memberi null.
 */
@RunWith(AndroidJUnit4::class)
class YoutubeiResolverTest {

    private fun resolver(): YoutubeiResolver {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return YoutubeiResolver(context) {
            YoutubeiNetworkConfiguration(proxy = null, proxyUsername = null, proxyPassword = null, dns = Dns.SYSTEM)
        }
    }

    private fun payload(client: String?) = """{"ok":true,"value":{
        "url":"https://rr.test/x?expire=1&sig=S","headers":{},
        "formatId":251,"mimeType":"audio/webm","codecs":"opus",
        "bitrate":160000,"sampleRate":48000,"contentLength":1000,
        "expiresAtMs":9999999999999,"runtimeVersion":"1.0.0"
        ${if (client != null) ""","client":"$client"""" else ""}
    }}""".trimIndent()

    @Test fun parseResponseMembacaClientDariJs() {
        val stream = resolver().parseResponse(payload("WEB_CREATOR"))
        assertEquals("WEB_CREATOR", stream.clientName)
        assertEquals(251, stream.formatId)
    }

    @Test fun parseResponseTanpaClientMemberiNull() {
        val stream = resolver().parseResponse(payload(null))
        assertNull(stream.clientName)
    }
}
