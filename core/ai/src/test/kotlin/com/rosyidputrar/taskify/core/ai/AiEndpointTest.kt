package com.rosyidputrar.taskify.core.ai

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiEndpointTest {
    @Test fun normalize() {
        assertEquals("https://api.openai.com/v1", AiEndpoint.normalize(" https://api.openai.com/v1/ ", AiProvider.OpenAiCompatible))
        assertEquals("https://openrouter.ai/api/v1", AiEndpoint.normalize("https://openrouter.ai/api/v1", AiProvider.OpenAiCompatible))
        // Anthropic: /v1 ditambahkan sendiri.
        assertEquals("https://api.anthropic.com", AiEndpoint.normalize("https://api.anthropic.com/v1/", AiProvider.Anthropic))
        fun bad(raw: String) = assertFailsWith<AiException> { AiEndpoint.normalize(raw, AiProvider.OpenAiCompatible) }.also { assertEquals(AiErrorKind.Config, it.kind) }.message!!
        assertTrue(bad("http://192.168.1.5:11434/v1").contains("HTTPS"))
        assertTrue(bad("api.openai.com/v1").contains("https://"))
        assertTrue(bad("").contains("Isi base URL"))
        assertTrue(bad("https://x.com/v1?key=1").contains("?"))
        assertTrue(bad("https:///v1").isNotEmpty())
        assertEquals("api.openai.com", AiEndpoint.host("https://api.openai.com/v1"))
    }

    @Test fun promptVariablesAreFilledWithIndonesianDate() {
        val now = ZonedDateTime.of(2026, 11, 20, 14, 5, 0, 0, ZoneId.of("Asia/Jakarta"))
        assertEquals("Jumat, 20 November 2026 pukul 14.05 (Asia/Jakarta)", AiPrompt.render("{hari}, {tanggal} pukul {jam} ({zona})", now))
        assertTrue(AiPrompt.render(AiPrompt.DEFAULT, now).contains("Sekarang Jumat, 20 November 2026 pukul 14.05 (Asia/Jakarta)."))
    }
}
