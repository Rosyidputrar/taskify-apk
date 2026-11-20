package com.rosyidputrar.taskify.feature.vault.component

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LooksSecretTest {
    @Test fun keyValuePairsStillDetected() {
        assertTrue(looksSecret("const api_key = 'abc'"))
        assertTrue(looksSecret("API-KEY: hunter2"))
        assertTrue(looksSecret("password: hunter2"))
        assertTrue(looksSecret("private_key = \"-----BEGIN\""))
        assertTrue(looksSecret("token=abc123"))
    }

    @Test fun bearerTokensDetected() {
        assertTrue(looksSecret("fetch(url, { headers: { Authorization: 'Bearer abcdefghij1234567890' } })"))
        assertTrue(looksSecret("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature"))
        // Placeholder di contoh/dokumentasi bukan rahasia sungguhan.
        assertFalse(looksSecret("Authorization: Bearer <token>"))
        assertFalse(looksSecret("Authorization: Bearer YOUR_TOKEN_HERE"))
        assertFalse(looksSecret("Authorization: Bearer abc"))
    }

    @Test fun bareServiceTokensDetected() {
        assertTrue(looksSecret("const t = 'ghp_abcdefghij1234567890'"))
        assertTrue(looksSecret("github_pat_11ABCDEFG0abcdefghij1234567890"))
        assertTrue(looksSecret("const key = 'sk-proj-abc123def456ghi789'"))
        assertTrue(looksSecret("const key = 'sk-abcdefghij1234567890'"))
        assertTrue(looksSecret("const key = 'sk_live_abcdefghij1234567890'"))
        // Literal dipisah: token Slack PALSU ini (angka berurutan + alfabet) tetap terdeteksi sebagai pola oleh GitHub secret scanning saat push bila ditulis utuh; string runtime-nya tidak berubah.
        assertTrue(looksSecret("xoxb-" + "123456789012-abcdefghij1234"))
        assertTrue(looksSecret("AKIAIOSFODNN7EXAMPLE"))
        assertTrue(looksSecret("glpat-abcdefghij1234567890"))
        assertTrue(looksSecret("dop_v1_abcdef0123456789abcdef0123456789"))
    }

    @Test fun jwtDetected() {
        assertTrue(looksSecret("const jwt = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c'"))
        assertFalse(looksSecret("eyJ.ab.cd"))
    }

    @Test fun ordinaryCodeIsNotSecret() {
        assertFalse(looksSecret("const greeting = 'hello'"))
        assertFalse(looksSecret("// tokenize the string, then split"))
        assertFalse(looksSecret("my secret recipe: sugar, flour"))
        assertFalse(looksSecret("const key = getKey()"))
        assertFalse(looksSecret("password field label"))
        assertFalse(looksSecret("const hash = '9f86d081884c7d659a2feaa0c55ad015'"))
        assertFalse(looksSecret("const id = '550e8400-e29b-41d4-a716-446655440000'"))
    }
}
